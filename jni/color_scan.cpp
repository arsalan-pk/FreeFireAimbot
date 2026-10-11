#include <jni.h>
#include <android/log.h>

#define LOG_TAG "ColorScan"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_example_colortracker_ColorTrackerService_scanForColor(JNIEnv *env, jobject thiz, jint width, jint height, jlong bufferPtr) {
    uint16_t *pixels = reinterpret_cast<uint16_t *>(bufferPtr);
    
    // Example: Detect Red Button (RGB 565 format)
    // Red in RGB_565 is typically 0xF800
    const uint16_t targetColor = 0xF800; 
    
    int foundX = -1;
    int foundY = -1;
    
    // Scan a central region (ROI)
    int startX = width / 4;
    int startY = height / 4;
    int endX = 3 * width / 4;
    int endY = 3 * height / 4;
    
    for (int y = startY; y < endY; ++y) {
        for (int x = startX; x < endX; ++x) {
            uint16_t currentColor = pixels[y * width + x];
            if (currentColor == targetColor) {
                foundX = x;
                foundY = y;
                goto done;
            }
        }
    }
    
done:
    if (foundX != -1) {
        LOGI("Target detected at %d,%d", foundX, foundY);
        // Here you could trigger a toast or log the UI element location
    }
}