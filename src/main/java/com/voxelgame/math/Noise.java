package com.voxelgame.math;

/**
 * Base abstract class for noise generators.
 * Defines the contract for 2D and 3D noise sampling.
 * All noise implementations return values in roughly [-1, 1].
 */
public abstract class Noise {

    /** Sample 2D noise at the given coordinates. */
    public abstract double eval2D(double x, double z);

    /** Sample 3D noise at the given coordinates. */
    public abstract double eval3D(double x, double y, double z);
}
