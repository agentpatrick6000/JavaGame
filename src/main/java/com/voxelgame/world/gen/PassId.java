package com.voxelgame.world.gen;

/**
 * Enum identifying each generation pass. Used for profiling,
 * ordering, and selectively enabling/disabling passes in the lab.
 */
public enum PassId {
    BASE_TERRAIN,
    CARVE_CAVES,
    FILL_FLUIDS,
    SURFACE_PAINT,
    ORE_VEINS,
    TREES,
    FLOWERS,
    DECORATIONS
}
