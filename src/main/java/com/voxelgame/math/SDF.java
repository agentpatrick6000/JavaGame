package com.voxelgame.math;

/**
 * Signed Distance Functions for procedural shape generation.
 * Used for cave carving, structure generation, and terrain features.
 */
public final class SDF {

    private SDF() {}

    /** Distance from point to sphere center minus radius. Negative = inside. */
    public static double sphere(double x, double y, double z, double cx, double cy, double cz, double radius) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - radius;
    }

    /** Axis-aligned box SDF. */
    public static double box(double x, double y, double z,
                             double cx, double cy, double cz,
                             double hx, double hy, double hz) {
        double dx = Math.abs(x - cx) - hx;
        double dy = Math.abs(y - cy) - hy;
        double dz = Math.abs(z - cz) - hz;
        double outside = Math.sqrt(Math.max(dx, 0) * Math.max(dx, 0) +
                                   Math.max(dy, 0) * Math.max(dy, 0) +
                                   Math.max(dz, 0) * Math.max(dz, 0));
        double inside = Math.min(Math.max(dx, Math.max(dy, dz)), 0);
        return outside + inside;
    }

    /** Union of two SDFs (minimum). */
    public static double union(double d1, double d2) {
        return Math.min(d1, d2);
    }

    /** Intersection of two SDFs (maximum). */
    public static double intersection(double d1, double d2) {
        return Math.max(d1, d2);
    }

    /** Smooth union (smooth blend between two SDFs). */
    public static double smoothUnion(double d1, double d2, double k) {
        double h = Curve.clamp(0.5 + 0.5 * (d2 - d1) / k, 0, 1);
        return Curve.lerp(d2, d1, h) - k * h * (1.0 - h);
    }
}
