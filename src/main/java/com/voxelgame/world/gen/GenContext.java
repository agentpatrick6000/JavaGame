package com.voxelgame.world.gen;

import com.voxelgame.math.OctaveNoise;
import com.voxelgame.math.Perlin;
import com.voxelgame.math.RNG;
import com.voxelgame.world.WorldConstants;

/**
 * Per-world generation context. Carries the seed, noise instances,
 * config, and scratch buffers shared across generation passes.
 * Created once and shared (thread-safe — all noise generators are immutable after init).
 */
public class GenContext {

    private final long seed;
    private final GenConfig config;

    // Terrain noise generators
    private final OctaveNoise continentalNoise;
    private final OctaveNoise detailNoise;

    // Cave noise generators
    private final Perlin caveNoise1;
    private final Perlin caveNoise2;

    // Tree density noise
    private final Perlin treeDensityNoise;

    public GenContext(long seed, GenConfig config) {
        this.seed = seed;
        this.config = config;

        // Initialize terrain noise
        this.continentalNoise = new OctaveNoise(seed, config.continentalOctaves, 2.0, 0.5);
        this.detailNoise = new OctaveNoise(seed + 1000L, config.detailOctaves, 2.0, 0.45);

        // Initialize cave noise (two Perlin layers for cheese-cave effect)
        this.caveNoise1 = new Perlin(seed + 2000L);
        this.caveNoise2 = new Perlin(seed + 3000L);

        // Tree density noise
        this.treeDensityNoise = new Perlin(seed + 4000L);
    }

    public long getSeed() { return seed; }
    public GenConfig getConfig() { return config; }

    public OctaveNoise getContinentalNoise() { return continentalNoise; }
    public OctaveNoise getDetailNoise() { return detailNoise; }
    public Perlin getCaveNoise1() { return caveNoise1; }
    public Perlin getCaveNoise2() { return caveNoise2; }
    public Perlin getTreeDensityNoise() { return treeDensityNoise; }

    /**
     * Create an RNG seeded for a specific chunk position.
     * Deterministic: same chunk always gets same RNG.
     */
    public RNG chunkRNG(int chunkX, int chunkZ) {
        return new RNG(seed).derive(chunkX, chunkZ);
    }

    /**
     * Compute terrain height at a world (x, z) coordinate.
     * Used by multiple passes (terrain, surface, trees, spawn).
     */
    public int getTerrainHeight(int worldX, int worldZ) {
        double cx = worldX * config.continentalFreq;
        double cz = worldZ * config.continentalFreq;
        double continental = continentalNoise.eval2D(cx, cz);

        double dx = worldX * config.detailFreq;
        double dz = worldZ * config.detailFreq;
        double detail = detailNoise.eval2D(dx, dz);

        // Continental gives broad shape, detail adds variation
        double combined = continental * 0.7 + detail * 0.3;

        int height = config.baseHeight + (int)(combined * config.heightVariation);

        // Clamp to valid range
        return Math.max(1, Math.min(height, WorldConstants.WORLD_HEIGHT - 2));
    }
}
