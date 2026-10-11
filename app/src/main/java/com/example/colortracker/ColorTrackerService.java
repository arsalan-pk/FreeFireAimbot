package com.example.colortracker;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;

public class ColorTrackerService extends Service {
    private static final String TAG = "akbar_debug";
    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";

    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private Surface surface;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (resultCode == Activity.RESULT_OK && data != null) {
                initMediaProjection(resultCode, data);
            }
        }
        return START_STICKY;
    }

    public void initMediaProjection(int resultCode, Intent data) {
        Log.d(TAG, "Initializing MediaProjection");
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mpm == null) {
            Log.e(TAG, "MediaProjectionManager not available");
            return;
        }

        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection != null) {
            // Create ImageReader for RGB_565 (fastest format for simple color detection)
            imageReader = ImageReader.newInstance(
                1920, 1080, // Default HD resolution
                ImageFormat.RGB_565, 
                2 // Max images
            );
            
            surface = imageReader.getSurface();
            
            mediaProjection.createVirtualDisplay(
                "ColorTracker",
                1920, 1080, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            );
            
            // Start listening for frames
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    processFrame(image);
                    image.close();
                }
            }, null);
        }
    }

    private void processFrame(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            return;
        }
        ByteBuffer buffer = planes[0].getBuffer();
        
        if (buffer.isDirect()) {
            long address = getAddress(buffer);
            // Pass direct pointer to C++
            scanForColor(image.getWidth(), image.getHeight(), address);
        } else {
            // Fallback: Copy to direct buffer
            buffer.rewind();
            byte[] pixels = new byte[buffer.remaining()];
            buffer.get(pixels);
            // Convert to direct and pass (slower)
            // For now, we'll skip this path to ensure performance
        }
    }

    private long getAddress(ByteBuffer buffer) {
        try {
            java.lang.reflect.Field field = buffer.getClass().getDeclaredField("address");
            field.setAccessible(true);
            return field.getLong(buffer);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get buffer address", e);
            return 0;
        }
    }

    // Native method to scan pixels
    public native void scanForColor(int width, int height, long bufferPtr);

    @Override
    public void onDestroy() {
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        super.onDestroy();
    }
}