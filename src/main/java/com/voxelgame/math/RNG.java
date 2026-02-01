package com.voxelgame.math;

import java.util.Random;

/** Seedable random number generator for deterministic worldgen. */
public class RNG {

    private final Random random;
    private final long seed;

    public RNG(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    public RNG derive(int x, int z) {
        return new RNG(seed ^ Hashing.hash2D(x, z, seed));
    }

    public int nextInt(int bound) { return random.nextInt(bound); }

    public float nextFloat() { return random.nextFloat(); }

    public boolean nextBoolean() { return random.nextBoolean(); }

    public double nextDouble() { return random.nextDouble(); }

    public long getSeed() { return seed; }
}
