package com.example.aimbot;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

/**
 * CapturePermissionActivity — Transparent Activity to receive MediaProjection
 * screen-capture permission.
 *
 * AccessibilityService cannot receive onActivityResult(), so we launch this
 * Activity to handle the permission dialog.  It immediately forwards the
 * result to AimService and finishes.
 */
public class CapturePermissionActivity extends Activity {

    private static final String TAG = "CapturePermissionActivity";
    private static final int REQUEST_MEDIA_PROJECTION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mpm == null) {
            Log.e(TAG, "MediaProjectionManager not found");
            finish();
            return;
        }

        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MEDIA_PROJECTION) {
            finish();
            return;
        }
        AimService service = AimService.getInstance();
        if (service != null) {
            service.onMediaProjectionResult(resultCode, data);
        } else {
            Log.w(TAG, "AimService instance not available");
        }
        finish();
    }
}