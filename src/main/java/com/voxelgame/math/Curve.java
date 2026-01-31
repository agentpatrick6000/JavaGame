package com.voxelgame.math;

/**
 * Curve/easing functions for terrain shaping. Provides remapping,
 * clamping, smoothstep, and custom falloff curves.
 */
public final class Curve {

    private Curve() {}

    /** Linear interpolation between a and b by t. */
    public static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    /** Clamp value between min and max. */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Hermite smoothstep: 3t² - 2t³ (smooth between 0..1) */
    public static double smoothstep(double t) {
        t = clamp(t, 0, 1);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Quintic smootherstep: 6t⁵ - 15t⁴ + 10t³ */
    public static double smootherstep(double t) {
        t = clamp(t, 0, 1);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** Remap value from [inMin, inMax] to [outMin, outMax]. */
    public static double remap(double value, double inMin, double inMax, double outMin, double outMax) {
        double t = (value - inMin) / (inMax - inMin);
        return lerp(outMin, outMax, t);
    }

    /** Remap and clamp to output range. */
    public static double remapClamped(double value, double inMin, double inMax, double outMin, double outMax) {
        double t = clamp((value - inMin) / (inMax - inMin), 0, 1);
        return lerp(outMin, outMax, t);
    }

    /** Power curve — raises input (0..1) to a power. */
    public static double power(double t, double exponent) {
        return Math.pow(clamp(t, 0, 1), exponent);
    }

    /** Inverse lerp — returns where value falls between a and b (0..1). */
    public static double inverseLerp(double a, double b, double value) {
        if (Math.abs(b - a) < 1e-10) return 0;
        return (value - a) / (b - a);
    }
}
