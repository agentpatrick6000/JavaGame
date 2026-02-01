package com.voxelgame.world.gen;

import com.voxelgame.math.CombinedNoise;
import com.voxelgame.math.OctaveNoise;
import com.voxelgame.math.Perlin;
import com.voxelgame.math.RNG;
import com.voxelgame.world.WorldConstants;

/**
 * Per-world generation context. Carries the seed, noise instances,
 * config, and scratch buffers shared across generation passes.
 *
 * Terrain generation uses InfDev 611-style combined noise:
 * - Two CombinedNoise fields for dual height layers (low/high)
 * - A selector noise to choose between them
 * - Domain warping creates the characteristic varied terrain
 *
 * Classic Minecraft's octave noise returns [-128, 128] for 8 octaves
 * (unnormalized sum). Our OctaveNoise normalizes to [-1, 1], so we
 * multiply by 128 to match Classic's range.
 *
 * Created once and shared (thread-safe — all noise generators are immutable after init).
 */
public class GenContext {

    private final long seed;
    private final GenConfig config;

    // Classic-range amplitude factor: OctaveNoise normalizes to [-1,1],
    // Classic returns [-128, 128] for 8 octaves.
    // We use a higher value to compensate for CombinedNoise not reaching
    // full range (the warping distributes values away from extremes).
    private static final double CLASSIC_AMPLITUDE = 260.0;

    // InfDev-style combined noise for terrain (two height layers)
    private final CombinedNoise combinedNoise1; // for heightLow
    private final CombinedNoise combinedNoise2; // for heightHigh
    private final OctaveNoise selectorNoise;    // chooses between low/high

    // Cave noise generators
    private final Perlin caveNoise1;
    private final Perlin caveNoise2;
    private final Perlin caveNoise3; // for vertical shafts

    // Tree/forest density noise
    private final Perlin treeDensityNoise;
    private final Perlin forestNoise; // larger-scale forest patches

    // Beach noise (determines sand placement near water)
    private final OctaveNoise beachNoise;

    // Erosion noise — creates varied dirt depth
    private final OctaveNoise erosionNoise;

    public GenContext(long seed, GenConfig config) {
        this.seed = seed;
        this.config = config;

        // InfDev-style combined noise for height calculation
        // CombinedNoise = octave1(x + octave2(x, z), z) — domain warping
        this.combinedNoise1 = new CombinedNoise(
            new OctaveNoise(seed, config.terrainOctaves, 2.0, 0.5),
            new OctaveNoise(seed + 100L, config.terrainOctaves, 2.0, 0.5)
        );
        this.combinedNoise2 = new CombinedNoise(
            new OctaveNoise(seed + 200L, config.terrainOctaves, 2.0, 0.5),
            new OctaveNoise(seed + 300L, config.terrainOctaves, 2.0, 0.5)
        );
        this.selectorNoise = new OctaveNoise(seed + 400L, config.selectorOctaves, 2.0, 0.5);

        // Initialize cave noise (two Perlin layers for spaghetti caves + one for vertical)
        this.caveNoise1 = new Perlin(seed + 2000L);
        this.caveNoise2 = new Perlin(seed + 3000L);
        this.caveNoise3 = new Perlin(seed + 3500L);

        // Tree/forest density noise
        this.treeDensityNoise = new Perlin(seed + 4000L);
        this.forestNoise = new Perlin(seed + 4500L);

        // Beach noise (octave-8, like Classic)
        this.beachNoise = new OctaveNoise(seed + 5000L, config.beachNoiseOctaves, 2.0, 0.5);

        // Erosion noise for variable dirt depth
        this.erosionNoise = new OctaveNoise(seed + 6000L, 8, 2.0, 0.5);
    }

    public long getSeed() { return seed; }
    public GenConfig getConfig() { return config; }

    public CombinedNoise getCombinedNoise1() { return combinedNoise1; }
    public CombinedNoise getCombinedNoise2() { return combinedNoise2; }
    public OctaveNoise getSelectorNoise() { return selectorNoise; }
    public Perlin getCaveNoise1() { return caveNoise1; }
    public Perlin getCaveNoise2() { return caveNoise2; }
    public Perlin getCaveNoise3() { return caveNoise3; }
    public Perlin getTreeDensityNoise() { return treeDensityNoise; }
    public Perlin getForestNoise() { return forestNoise; }
    public OctaveNoise getBeachNoise() { return beachNoise; }
    public OctaveNoise getErosionNoise() { return erosionNoise; }

    /**
     * Create an RNG seeded for a specific chunk position.
     * Deterministic: same chunk always gets same RNG.
     */
    public RNG chunkRNG(int chunkX, int chunkZ) {
        return new RNG(seed).derive(chunkX, chunkZ);
    }

    /**
     * Compute terrain height at a world (x, z) coordinate.
     *
     * InfDev 611 algorithm (adapted for normalized noise):
     * Classic's octave-8 noise returns [-128, 128]. Our OctaveNoise normalizes to [-1, 1].
     * We multiply by CLASSIC_AMPLITUDE (128) to match the Classic range.
     *
     * Input scaling: Classic uses x*1.3 with unnormalized noise that already has
     * low-frequency components from persistence=2. Our noise has persistence=0.5
     * (fine detail at higher frequencies), so we use a much lower input scale
     * to get broad 80-100 block terrain features.
     *
     * Steps:
     * 1. Scale inputs to ~0.01 for broad features (equivalent to Classic's behavior)
     * 2. Sample combined noise fields and scale to Classic range [-128, 128]
     * 3. heightLow  = raw1 / 6 - 4  →  range ~[-25, 17]
     * 4. heightHigh = raw2 / 5 + 6  →  range ~[-20, 32]
     * 5. Selector noise chooses between low and high
     * 6. Halve result, dampen negatives
     * 7. Add to water level (64)
     */
    public int getTerrainHeight(int worldX, int worldZ) {
        // Input scaling: ~0.013 gives dominant features of ~80 blocks wavelength
        // CombinedNoise warping adds extra variation at these scales
        double sx = worldX * 0.013;
        double sz = worldZ * 0.013;

        // Sample combined noise fields — normalized [-1, 1], then scale to Classic range
        double raw1 = combinedNoise1.eval2D(sx, sz) * CLASSIC_AMPLITUDE;
        double raw2 = combinedNoise2.eval2D(sx, sz) * CLASSIC_AMPLITUDE;

        // Two height layers (Classic formula)
        double heightLow = raw1 / config.heightLowScale + config.heightLowOffset;
        double heightHigh = raw2 / config.heightHighScale + config.heightHighOffset;

        // Selector noise: use lower frequency for broad region selection
        double selector = selectorNoise.eval2D(worldX * 0.005, worldZ * 0.005);

        double heightResult;
        if (selector > 0) {
            heightResult = heightLow;
        } else {
            heightResult = Math.max(heightLow, heightHigh);
        }

        // Halve the result (Classic does this to bring to reasonable range)
        heightResult = heightResult / 2.0;

        // Dampen negative values (prevents too-deep ocean floors)
        if (heightResult < 0) {
            heightResult = heightResult * 0.8;
        }

        int height = config.baseHeight + (int) heightResult;

        // Clamp to valid range
        return Math.max(1, Math.min(height, WorldConstants.WORLD_HEIGHT - 2));
    }
}
