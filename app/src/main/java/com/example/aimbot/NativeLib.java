package com.example.aimbot;

import android.util.Log;

public class NativeLib {
    private static final String TAG = "akbar_debug";

    static {
        Log.i(TAG, "NativeLib static initializer START");

        try {
            Log.i(TAG, "NativeLib: attempting System.loadLibrary(\"aimbot\")");

            System.loadLibrary("aimbot");

            Log.i(TAG, "NativeLib: SUCCESS libaimbot.so loaded");
        } catch (Throwable e) {
            Log.e(TAG, "NativeLib: FAILED to load libaimbot.so", e);
            throw e;
        }

        Log.i(TAG, "NativeLib static initializer END");
    }

    public static void verifyLoad() {
        Log.i(TAG, "NativeLib.verifyLoad() REACHED");
    }

    // Pass the service instance to C++
    public static native void setServiceObject(AimService service);
    
    // Cache the method ID
    public static native void initTapMethod();

    // Run detection
    public static native int detectAndTap(android.graphics.Bitmap bitmap);
}