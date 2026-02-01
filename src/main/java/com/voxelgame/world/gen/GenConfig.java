package com.voxelgame.world.gen;

/**
 * World generation configuration. Holds all tuneable parameters
 * for terrain, caves, ores, trees, etc.
 *
 * Tuned to match InfDev 611 terrain style:
 * - Combined noise (domain warping) for dramatic, varied terrain
 * - Dual height layers (low/high) with selector noise
 * - Patch-based tree placement for natural forests
 * - Worm-inspired cave carving
 */
public class GenConfig {

    // --- Terrain (InfDev 611 style) ---
    public int baseHeight = 64;             // sea level / base height
    public double terrainScale = 1.3;       // input coordinate scaling (classic uses 1.3)
    public int terrainOctaves = 8;          // octaves for combined noise (classic uses 8)
    public int selectorOctaves = 6;         // octaves for height selector noise
    public double heightLowScale = 6.0;     // divisor for low terrain: noise/6 - 4
    public double heightLowOffset = -4.0;   // offset for low terrain
    public double heightHighScale = 5.0;    // divisor for high terrain: noise/5 + 6
    public double heightHighOffset = 6.0;   // offset for high terrain

    // --- Surface ---
    public int dirtDepth = 4;               // dirt layers below grass
    public int mountainThreshold = 100;     // height above which stone is exposed
    public int beachDepth = 4;              // sand depth at beaches (wider beaches)
    public double beachNoiseScale = 1.0;    // scale for beach noise sampling
    public int beachNoiseOctaves = 8;       // octaves for beach boundary noise

    // --- Caves ---
    public double caveFreq = 0.045;         // 3D noise frequency for caves
    public double caveThreshold = 0.42;     // threshold for spaghetti caves
    public int caveMinY = 2;                // minimum cave floor
    public int caveSurfaceMargin = 5;       // blocks below surface to avoid breaking through
    public double verticalCaveFreq = 0.06;  // frequency for vertical cave shafts
    public double verticalCaveThreshold = 0.03; // narrow threshold for vertical shafts

    // --- Ores ---
    public int coalMinY = 5, coalMaxY = 80, coalVeinSize = 10, coalAttemptsPerChunk = 20;
    public int ironMinY = 5, ironMaxY = 64, ironVeinSize = 6, ironAttemptsPerChunk = 12;
    public int goldMinY = 5, goldMaxY = 32, goldVeinSize = 5, goldAttemptsPerChunk = 4;
    public int diamondMinY = 5, diamondMaxY = 16, diamondVeinSize = 3, diamondAttemptsPerChunk = 2;

    // --- Trees (patch-based, InfDev style) ---
    public double treePatchChance = 0.55;   // chance per chunk of having a tree patch
    public int treePatchAttempts = 12;       // attempts to place trees per patch
    public int treePatchSpread = 5;          // spread radius for tree placement within patch
    public int treeMinTrunk = 4;
    public int treeMaxTrunk = 7;
    public int treeEdgeMargin = 3;           // blocks from chunk edge to avoid cross-chunk issues
    public int treeSlopeMax = 3;             // max height diff for tree placement
    public double forestNoiseThreshold = 0.1; // noise threshold for forest patches

    /** Default config suitable for InfDev 611-style terrain. */
    public static GenConfig defaultConfig() {
        return new GenConfig();
    }
}
