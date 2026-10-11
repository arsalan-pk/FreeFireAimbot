package com.example.aimbot;

/**
 * Constants exposed to AimService for logging and debugging.
 * Mirrors the #define values in aimbot.cpp so Java and C++ stay in sync.
 */
public final class aimbot {
    public static final int ROI_X = 360;
    public static final int ROI_Y = 540;
    public static final int ROI_W =  80;
    public static final int ROI_H =  80;

    public static final int HEAD_COLOR_R = 230;
    public static final int HEAD_COLOR_G = 120;
    public static final int HEAD_COLOR_B =  30;
    public static final int HEAD_COLOR_TOLERANCE = 50;

    private aimbot() {}
}