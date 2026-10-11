#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <android/native_window_jni.h>
#include <cmath>

#include <cstdint>
#include <cstring>

#define  LOG_TAG  "AimbotNDK"
#define  LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── Sensitivity / Colour thresholds ─────────────────────────────────────────

// Orange-red head colour (Free Fire enemy heads are typically bright orange)
static constexpr uint8_t  HEAD_COLOR_R = 230;
static constexpr uint8_t  HEAD_COLOR_G = 120;
static constexpr uint8_t  HEAD_COLOR_B =  30;

// Allow ±tolerance on each channel.  ~50 is a good starting point.
static constexpr int8_t   HEAD_COLOR_TOLERANCE = 50;

// ─── ROI (Region of Interest) ────────────────────────────────────────────────
// Adjust these to match where heads appear on your device's screen.
// Recommended: centre crosshair area, roughly 1/5th of screen width/height.
static constexpr int ROI_X = 360;
static constexpr int ROI_Y = 540;
static constexpr int ROI_W =  80;
static constexpr int ROI_H =  80;

// ─── Tap simulation mode ─────────────────────────────────────────────────────
// Set to 1 to use AccessibilityService.dispatchGesture (works without root).
// Set to 0 to use raw InputManager IPC (requires root / adb shell).
static constexpr bool USE_ACCESSIBILITY_TAP = true;

// ─── JNI callbacks (set by Java side) ─────────────────────────────────────────
static jobject           g_service_obj = nullptr;   // AccessibilityService instance
static JavaVM           *g_vm          = nullptr;
static jmethodID         g_tap_method  = nullptr;   // performTap(float x, float y)

// Helper to get thread-local JNIEnv
static inline JNIEnv* get_jni_env() {
    JNIEnv *env = nullptr;
    if (g_vm) {
        g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    }
    return env;
}

// ─── Forward declarations ─────────────────────────────────────────────────────
extern "C" {

// ─── Public JNI entry points ──────────────────────────────────────────────────

/**
 * setServiceObject — called from NativeLib.setServiceObject(service).
 * Stores the AccessibilityService instance so C++ can call performTap().
 */
JNIEXPORT void JNICALL
Java_com_example_aimbot_NativeLib_setServiceObject(
        JNIEnv *env,
        jclass /*clazz*/,
        jobject service)
{
    if (!g_vm) {
        env->GetJavaVM(&g_vm);
    }

    if (g_service_obj) {
        env->DeleteGlobalRef(g_service_obj);
    }
    g_service_obj = env->NewGlobalRef(service);
    LOGD("setServiceObject: service stored, env=%p", (void*)env);
}

/**
 * initTapMethod — Called once to cache the method ID for performTap
 */
JNIEXPORT void JNICALL
Java_com_example_aimbot_NativeLib_initTapMethod(JNIEnv *env, jclass /*clazz*/) {
    if (!g_service_obj) {
        LOGE("initTapMethod: No service object yet");
        return;
    }

    jclass clazz = env->GetObjectClass(g_service_obj);
    g_tap_method = env->GetMethodID(clazz, "performTap", "(FF)V"); 
    
    if (!g_tap_method) {
        LOGE("Could not find performTap method in AccessibilityService");
    } else {
        LOGD("Tap method initialized successfully");
    }
}

/**
 * detectAndTap — main entry point called from Java in a tight loop.
 *
 * @param env      JNIEnv pointer (thread-local, passed from Java).
 * @param bitmap   java.lang.Object holding an android.graphics.Bitmap.
 * @return         1 if a tap was performed, 0 if nothing detected.
 */
JNIEXPORT jint JNICALL
Java_com_example_aimbot_NativeLib_detectAndTap(
        JNIEnv *env,
        jclass /*clazz*/,
        jobject bitmap)
{
    JNIEnv *current_env = get_jni_env();
    if (!current_env) {
        current_env = env;
    }

    if (!bitmap) return 0;
    if (!g_service_obj) {
        LOGE("detectAndTap: no service object set");
        return 0;
    }

    // ── 1. Lock pixels ──────────────────────────────────────────────────────
    AndroidBitmapInfo info;
    int rc = AndroidBitmap_getInfo(current_env, bitmap, &info);
    if (rc != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("getInfo failed: %d", rc);
        return 0;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        LOGE("Unsupported bitmap format: %d", info.format);
        return 0;
    }

    void *pixels = nullptr;
    rc = AndroidBitmap_lockPixels(current_env, bitmap, &pixels);
    if (rc != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("lockPixels failed: %d", rc);
        return 0;
    }

    // ── 2. Clamp ROI to bitmap bounds ───────────────────────────────────────
    int w = (int)info.width;
    int h = (int)info.height;

    int rx = ROI_X;  if (rx < 0) rx = 0;
    int ry = ROI_Y;  if (ry < 0) ry = 0;
    int rw = ROI_W;  if (rw > w - rx) rw = w - rx;
    int rh = ROI_H;  if (rh > h - ry) rh = h - ry;

    if (rw <= 0 || rh <= 0) {
        AndroidBitmap_unlockPixels(current_env, bitmap);
        return 0;
    }

    // ── 3. Scan for Head Color ──────────────────────────────────────────────
    int best_x = -1;
    int best_y = -1;

    int bpp = (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) ? 4 : 2;

    for (int y = 0; y < rh; ++y) {
        uint8_t *row = static_cast<uint8_t*>(pixels) + (ry + y) * info.stride;
        
        for (int x = 0; x < rw; ++x) {
            uint8_t *pixel = row + (rx + x) * bpp;

            uint8_t r, g, b;
            if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
                r = pixel[0];
                g = pixel[1];
                b = pixel[2];
            } else {
                unsigned short color = *(unsigned short*)pixel;
                r = ((color >> 11) & 0x1F) << 3;
                g = ((color >> 5) & 0x3F) << 2;
                b = (color & 0x1F) << 3;
            }

            int diff_r = std::abs((int)r - (int)HEAD_COLOR_R);
            int diff_g = std::abs((int)g - (int)HEAD_COLOR_G);
            int diff_b = std::abs((int)b - (int)HEAD_COLOR_B);

            if (diff_r < HEAD_COLOR_TOLERANCE &&
                diff_g < HEAD_COLOR_TOLERANCE &&
                diff_b < HEAD_COLOR_TOLERANCE) {
                
                best_x = rx + x;
                best_y = ry + y;
                goto tap_found;
            }
        }
    }

tap_found:
    AndroidBitmap_unlockPixels(current_env, bitmap);

    if (best_x != -1) {
        if (g_tap_method && g_service_obj) {
            current_env->CallVoidMethod(g_service_obj, g_tap_method, (jfloat)best_x, (jfloat)best_y);
            LOGD("Tapped at: %d, %d", best_x, best_y);
            return 1;
        } else {
            LOGE("Tap method or service object missing during tap");
        }
    }

    return 0;
}

} // extern "C"