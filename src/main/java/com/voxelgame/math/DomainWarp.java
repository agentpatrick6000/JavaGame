package com.voxelgame.math;

/**
 * Domain warping utility. Distorts input coordinates using noise
 * before sampling, creating organic-looking terrain distortion.
 * Thread-safe after construction.
 */
public class DomainWarp {

    private final Noise warpNoiseX;
    private final Noise warpNoiseZ;
    private final double strength;

    /**
     * @param seed     Base seed for warp noise
     * @param strength How many units to displace coordinates
     */
    public DomainWarp(long seed, double strength) {
        this.warpNoiseX = new Perlin(seed + 1000L);
        this.warpNoiseZ = new Perlin(seed + 2000L);
        this.strength = strength;
    }

    /**
     * Apply 2D domain warp. Returns warped coordinates as [x, z].
     */
    public double[] warp2D(double x, double z, double frequency) {
        double wx = x + warpNoiseX.eval2D(x * frequency, z * frequency) * strength;
        double wz = z + warpNoiseZ.eval2D(x * frequency + 100, z * frequency + 100) * strength;
        return new double[]{wx, wz};
    }

    /**
     * Apply 3D domain warp. Returns warped coordinates as [x, y, z].
     */
    public double[] warp3D(double x, double y, double z, double frequency) {
        double wx = x + warpNoiseX.eval3D(x * frequency, y * frequency, z * frequency) * strength;
        double wy = y + warpNoiseX.eval3D(x * frequency + 100, y * frequency + 100, z * frequency + 100) * strength;
        double wz = z + warpNoiseZ.eval3D(x * frequency + 200, y * frequency + 200, z * frequency + 200) * strength;
        return new double[]{wx, wy, wz};
    }
}
