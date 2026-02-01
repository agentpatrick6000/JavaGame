package com.voxelgame.math;

/**
 * Combined noise function used in Classic/Infdev Minecraft terrain generation.
 * Creates domain warping by using one noise function's output to offset
 * the input of another, producing varied terrain with both gentle and steep areas.
 *
 * compute(x, z) = noise1(x + noise2(x, z), z)
 *
 * Thread-safe after construction.
 */
public class CombinedNoise extends Noise {

    private final Noise noise1;
    private final Noise noise2;

    public CombinedNoise(Noise noise1, Noise noise2) {
        this.noise1 = noise1;
        this.noise2 = noise2;
    }

    @Override
    public double eval2D(double x, double z) {
        return noise1.eval2D(x + noise2.eval2D(x, z), z);
    }

    @Override
    public double eval3D(double x, double y, double z) {
        return noise1.eval3D(x + noise2.eval3D(x, y, z), y, z);
    }
}
