package com.example.aimbot;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity — Entry point for the Aimbot app.
 *
 * FLOW:
 *   1. Check Accessibility permission → prompt if missing.
 *   2. Check POST_NOTIFICATIONS (Android 13+) → request if missing.
 *   3. User presses "Start Aimbot" → launches AimService which requests
 *      MediaProjection permission via CapturePermissionActivity.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "akbar_debug";
    private static final int NOTIFICATION_REQUEST_CODE = 1002;

    private AimService aimService;
    private Button      btnStartStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i("akbar_debug", "MainActivity: before NativeLib verification");

        try {
            Class.forName(NativeLib.class.getName(), true, NativeLib.class.getClassLoader());
            NativeLib.verifyLoad();
            Log.i("akbar_debug", "MainActivity: NativeLib verification COMPLETE");
        } catch (Throwable e) {
            Log.e("akbar_debug", "MainActivity: NativeLib verification FAILED", e);
            throw new RuntimeException(e);
        }

        btnStartStop = findViewById(R.id.btn_start_stop);
        btnStartStop.setOnClickListener(v -> toggleAimbot());

        checkPermissions();
    }

    /** Checks and requests all required runtime permissions. */
    private void checkPermissions() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this,
                    "Enable Accessibility permission in Settings to use the Aimbot.",
                    Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_REQUEST_CODE);
            }
        }
    }

    private boolean isAccessibilityEnabled() {
        ComponentName serviceName = new ComponentName(this, AimService.class);
        String serviceKey = serviceName.flattenToString();
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;

        return enabledServices.contains(serviceKey);
    }

    private void toggleAimbot() {
        if (aimService == null) {
            aimService = AimService.getInstance();
            if (aimService == null) {
                Toast.makeText(this, "Service not bound yet — try again", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.i(TAG, "Bound to AimService");
        }

        if (aimService.isRunning()) {
            aimService.stopAimbot();
            btnStartStop.setText("Start Aimbot");
            btnStartStop.setBackgroundColor(0xFF4CAF50);
            Toast.makeText(this, "Aimbot stopped", Toast.LENGTH_SHORT).show();
        } else {
            Log.i(TAG, "Starting AimService");
            aimService.startAimbot();
            btnStartStop.setText("Stop Aimbot");
            btnStartStop.setBackgroundColor(0xFFE53935);
            Toast.makeText(this,
                    "Aimbot started — switch to Free Fire",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Notification permission required for foreground service",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (aimService == null) {
            aimService = AimService.getInstance();
        }
        if (aimService != null && aimService.isRunning()) {
            aimService.stopAimbot();
            btnStartStop.setText("Start Aimbot");
            btnStartStop.setBackgroundColor(0xFF4CAF50);
        }
        super.onBackPressed();
    }
}