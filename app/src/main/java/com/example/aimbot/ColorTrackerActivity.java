package com.example.aimbot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

public class ColorTrackerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            Class.forName(NativeLib.class.getName());
            NativeLib.verifyLoad();
        } catch (Throwable ignored) {}

        // Check if accessibility is enabled
        if (!checkAccessibility()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else {
            startForegroundService();
        }
        
        finish(); // Close activity, let service run in background
    }

    private boolean checkAccessibility() {
        int accessibilityEnabled = android.provider.Settings.Secure.getInt(
                getContentResolver(),
                "accessibility_enabled", 0);
        if (accessibilityEnabled == 0) return false;
        
        String service = getPackageName() + "/" + AimService.class.getCanonicalName();
        try {
            String enabledServices = Settings.Secure.getString(
                    getContentResolver(),
                    "enabled_accessibility_services");
            if (enabledServices != null) {
                return enabledServices.contains(service);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void startForegroundService() {
        Intent intent = new Intent(this, AimService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}