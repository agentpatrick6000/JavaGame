package com.voxelgame.world;

/**
 * Read-only world access interface for subsystems that need to query
 * block state and lighting without modifying it (e.g., meshing, rendering, physics).
 * 
 * Phase 4: Added RGB block light methods for colored light sources.
 */
public interface WorldAccess {
    int getBlock(int x, int y, int z);
    
    /**
     * Get sky visibility (0.0 - 1.0) at world coordinates.
     * Returns 1.0 if the block has an unobstructed column to the sky, 0.0 otherwise.
     */
    float getSkyVisibility(int x, int y, int z);
    
    /**
     * Get sky light level (0-15) at world coordinates.
     * @deprecated Use {@link #getSkyVisibility(int, int, int)} for the new unified lighting model.
     */
    @Deprecated
    int getSkyLight(int x, int y, int z);
    
    /**
     * Get legacy scalar block light level (0-15) at world coordinates.
     * @deprecated Use {@link #getBlockLightRGB(int, int, int)} for Phase 4 colored lighting.
     */
    int getBlockLight(int x, int y, int z);
    
    // ========================================================================
    // Phase 4: RGB Block Light
    // ========================================================================
    
    /**
     * Get RGB block light at world coordinates.
     * @return 3-element float array [R, G, B] in range 0-1
     */
    float[] getBlockLightRGB(int x, int y, int z);
    
    /**
     * Get red channel of block light (0-1).
     */
    float getBlockLightR(int x, int y, int z);
    
    /**
     * Get green channel of block light (0-1).
     */
    float getBlockLightG(int x, int y, int z);
    
    /**
     * Get blue channel of block light (0-1).
     */
    float getBlockLightB(int x, int y, int z);
    
    Chunk getChunk(int cx, int cz);
    boolean isLoaded(int cx, int cz);
}
