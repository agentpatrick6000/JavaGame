package com.voxelgame.world.gen;

/**
 * World generation configuration. Holds all tuneable parameters
 * for terrain, caves, ores, trees, etc.
 */
public class GenConfig {

    // --- Terrain ---
    public int baseHeight = 64;         // sea level / base height
    public int heightVariation = 40;    // ±blocks from base height (more dramatic terrain)
    public double continentalFreq = 0.0015; // slightly lower freq = broader terrain features
    public double detailFreq = 0.012;       // small-scale detail (slightly more variation)
    public int continentalOctaves = 4;      // more octaves = more detail
    public int detailOctaves = 3;

    // --- Surface ---
    public int dirtDepth = 4;           // dirt layers below grass
    public int mountainThreshold = 90;  // height above which stone is exposed
    public int beachDepth = 3;          // sand depth at beaches (wider beaches)

    // --- Caves ---
    public double caveFreq = 0.035;         // 3D noise frequency for caves (more varied caves)
    public double caveThreshold = 0.50;     // slightly easier to form caves
    public int caveMinY = 5;                // minimum cave floor
    public int caveSurfaceMargin = 5;       // blocks below surface to avoid breaking through

    // --- Ores ---
    public int coalMinY = 5, coalMaxY = 80, coalVeinSize = 10, coalAttemptsPerChunk = 20;
    public int ironMinY = 5, ironMaxY = 64, ironVeinSize = 6, ironAttemptsPerChunk = 12;
    public int goldMinY = 5, goldMaxY = 32, goldVeinSize = 5, goldAttemptsPerChunk = 4;
    public int diamondMinY = 5, diamondMaxY = 16, diamondVeinSize = 3, diamondAttemptsPerChunk = 2;

    // --- Trees ---
    public double treeDensity = 0.02;       // probability per grass block
    public int treeMinTrunk = 4;
    public int treeMaxTrunk = 6;
    public int treeEdgeMargin = 3;          // blocks from chunk edge to avoid cross-chunk issues
    public int treeSlopeMax = 2;            // max height diff for tree placement

    /** Default config suitable for a Minecraft-like terrain. */
    public static GenConfig defaultConfig() {
        return new GenConfig();
    }
}
