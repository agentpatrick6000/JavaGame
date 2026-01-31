package com.voxelgame.world.gen;

/**
 * World generation configuration. Holds all tuneable parameters
 * for terrain, caves, ores, trees, etc.
 */
public class GenConfig {

    // --- Terrain ---
    public int baseHeight = 64;         // sea level / base height
    public int heightVariation = 30;    // ±blocks from base height
    public double continentalFreq = 0.002;  // large-scale terrain shape
    public double detailFreq = 0.01;        // small-scale detail
    public int continentalOctaves = 3;
    public int detailOctaves = 3;

    // --- Surface ---
    public int dirtDepth = 4;           // dirt layers below grass
    public int mountainThreshold = 85;  // height above which stone is exposed
    public int beachDepth = 2;          // sand depth at beaches

    // --- Caves ---
    public double caveFreq = 0.04;          // 3D noise frequency for caves
    public double caveThreshold = 0.55;     // noise value above which = cave
    public int caveMinY = 5;                // minimum cave floor
    public int caveSurfaceMargin = 4;       // blocks below surface to avoid breaking through

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
