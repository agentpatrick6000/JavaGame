package com.voxelgame.math;

import java.util.Random;

/**
 * Classic improved Perlin noise implementation.
 * Provides coherent gradient noise in 2D and 3D.
 * Thread-safe after construction (immutable permutation table).
 */
public class Perlin extends Noise {

    private final int[] perm;

    /** Create Perlin noise with a specific seed. */
    public Perlin(long seed) {
        perm = new int[512];
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;

        // Fisher-Yates shuffle with seed
        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }

        // Double the table to avoid wrapping
        for (int i = 0; i < 256; i++) {
            perm[i] = p[i];
            perm[i + 256] = p[i];
        }
    }

    /** Fade curve: 6t^5 - 15t^4 + 10t^3 */
    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** Linear interpolation */
    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** 2D gradient function */
    private static double grad2D(int hash, double x, double z) {
        int h = hash & 3;
        double u = (h & 1) == 0 ? x : -x;
        double v = (h & 2) == 0 ? z : -z;
        return u + v;
    }

    /** 3D gradient function */
    private static double grad3D(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    @Override
    public double eval2D(double x, double z) {
        // Find unit square that contains point
        int xi = (int) Math.floor(x) & 255;
        int zi = (int) Math.floor(z) & 255;

        // Relative position within the square
        double xf = x - Math.floor(x);
        double zf = z - Math.floor(z);

        // Fade curves
        double u = fade(xf);
        double v = fade(zf);

        // Hash coordinates of the 4 corners
        int aa = perm[perm[xi] + zi];
        int ab = perm[perm[xi] + zi + 1];
        int ba = perm[perm[xi + 1] + zi];
        int bb = perm[perm[xi + 1] + zi + 1];

        // Interpolate
        double x1 = lerp(u, grad2D(aa, xf, zf), grad2D(ba, xf - 1, zf));
        double x2 = lerp(u, grad2D(ab, xf, zf - 1), grad2D(bb, xf - 1, zf - 1));

        return lerp(v, x1, x2);
    }

    @Override
    public double eval3D(double x, double y, double z) {
        // Find unit cube that contains point
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        int zi = (int) Math.floor(z) & 255;

        // Relative position within the cube
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double zf = z - Math.floor(z);

        // Fade curves
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        // Hash coordinates of the 8 corners
        int aaa = perm[perm[perm[xi] + yi] + zi];
        int aab = perm[perm[perm[xi] + yi] + zi + 1];
        int aba = perm[perm[perm[xi] + yi + 1] + zi];
        int abb = perm[perm[perm[xi] + yi + 1] + zi + 1];
        int baa = perm[perm[perm[xi + 1] + yi] + zi];
        int bab = perm[perm[perm[xi + 1] + yi] + zi + 1];
        int bba = perm[perm[perm[xi + 1] + yi + 1] + zi];
        int bbb = perm[perm[perm[xi + 1] + yi + 1] + zi + 1];

        // Trilinear interpolation of gradients
        double x1, x2, y1, y2;

        x1 = lerp(u, grad3D(aaa, xf, yf, zf), grad3D(baa, xf - 1, yf, zf));
        x2 = lerp(u, grad3D(aba, xf, yf - 1, zf), grad3D(bba, xf - 1, yf - 1, zf));
        y1 = lerp(v, x1, x2);

        x1 = lerp(u, grad3D(aab, xf, yf, zf - 1), grad3D(bab, xf - 1, yf, zf - 1));
        x2 = lerp(u, grad3D(abb, xf, yf - 1, zf - 1), grad3D(bbb, xf - 1, yf - 1, zf - 1));
        y2 = lerp(v, x1, x2);

        return lerp(w, y1, y2);
    }
}
