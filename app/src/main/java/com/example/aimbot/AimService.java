package com.example.aimbot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

public class AimService extends AccessibilityService {

    private static final String TAG = "akbar_debug";
    private static volatile AimService sInstance;

    // UI Elements
    private WindowManager windowManager;
    private View overlayView;
    private Switch aimSwitch;
    private TextView statusText;
    private ImageView crosshair;

    // Bitmap used for screen capture
    private Bitmap mScreenBitmap;
    private boolean isRunning = false;

    public static AimService getInstance() {
        return sInstance;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void startAimbot() {
        if (!isRunning) {
            isRunning = true;
            if (statusText != null) {
                statusText.setText("AIMING...");
            }
            if (aimSwitch != null && !aimSwitch.isChecked()) {
                aimSwitch.setChecked(true);
            }
            startCaptureLoop();
        }
    }

    public void stopAimbot() {
        if (isRunning) {
            isRunning = false;
            if (statusText != null) {
                statusText.setText("OFF");
            }
            if (aimSwitch != null && aimSwitch.isChecked()) {
                aimSwitch.setChecked(false);
            }
            stopCaptureLoop();
        }
    }

    public void onMediaProjectionResult(int resultCode, Intent data) {
        Log.d(TAG, "onMediaProjectionResult: resultCode=" + resultCode);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service started");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Service is connected and ready");
        sInstance = this;

        // Initialize C++ with this service instance
        NativeLib.setServiceObject(this);
        NativeLib.initTapMethod();

        setupOverlay();
    }

    private void setupOverlay() {
        if (overlayView != null) {
            return;
        }

        Log.d(TAG, "Creating overlay view");

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            Log.e(TAG, "WindowManager service not available");
            return;
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);
        
        aimSwitch = overlayView.findViewById(R.id.aim_switch);
        statusText = overlayView.findViewById(R.id.status_text);
        crosshair = overlayView.findViewById(R.id.crosshair);

        aimSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startAimbot();
            } else {
                stopAimbot();
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        try {
            windowManager.addView(overlayView, params);
            Log.d(TAG, "Overlay added successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay: " + e.getMessage(), e);
        }
    }

    // --- Capture Loop Logic ---
    private Thread captureThread;

    private void startCaptureLoop() {
        if (captureThread != null && captureThread.isAlive()) return;

        captureThread = new Thread(() -> {
            while (isRunning) {
                try {
                    if (mScreenBitmap != null) {
                        NativeLib.detectAndTap(mScreenBitmap);
                    }
                    Thread.sleep(16); // ~60 FPS
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error in capture loop", e);
                }
            }
        });
        captureThread.start();
    }

    private void stopCaptureLoop() {
        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }
    }

    /**
     * Called from C++ via JNI to perform a tap
     */
    public void performTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        GestureDescription.StrokeDescription stroke = 
                new GestureDescription.StrokeDescription(
                        path, 
                        0, 
                        10); // Duration in ms
        
        builder.addStroke(stroke);
        dispatchGesture(builder.build(), null, null);
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        if (sInstance == this) {
            sInstance = null;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        stopCaptureLoop();
        if (overlayView != null && windowManager != null) {
            Log.d(TAG, "Removing overlay view to prevent leaks");
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Overlay view not attached to window manager", e);
            }
            overlayView = null;
        }
        if (sInstance == this) {
            sInstance = null;
        }
        super.onDestroy();
    }

    public static class AimForegroundService extends android.app.Service {
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}