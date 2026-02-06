package com.voxelgame.world;

/**
 * Heightfield-based sky visibility computation - Phase 2 implementation.
 * 
 * Computes what fraction of the sky hemisphere is visible from any position
 * by tracing rays in multiple directions and finding elevation angles to obstructions.
 * 
 * Key features:
 * - Multi-directional ray tracing (8 horizontal directions + vertical)
 * - Computes both overall sky visibility (0-1) and horizon weight (0-1)
 * - Under overhangs: low visibility, high horizon weight (warm tint at sunset)
 * - In caves: zero visibility
 * - On open surfaces: high visibility, balanced horizon weight
 * 
 * Performance:
 * - Results are computed per-vertex during meshing (cached in mesh data)
 * - No per-frame computation required
 * - Reasonably fast (~9 ray traces per vertex with early termination)
 */
public class HeightfieldVisibility {

    // 8 horizontal directions for horizon tracing (N, NE, E, SE, S, SW, W, NW)
    private static final int[][] HORIZONTAL_DIRS = {
        { 0, -1},  // North (-Z)
        { 1, -1},  // NE
        { 1,  0},  // East (+X)
        { 1,  1},  // SE
        { 0,  1},  // South (+Z)
        {-1,  1},  // SW
        {-1,  0},  // West (-X)
        {-1, -1},  // NW
    };

    /** Maximum distance to trace rays (blocks). */
    private static final int MAX_TRACE_DISTANCE = 32;

    /** Vertical check distance (blocks above position). */
    private static final int VERTICAL_CHECK_HEIGHT = 48;

    /**
     * Sky visibility result containing both overall visibility and horizon weight.
     */
    public static class VisibilityResult {
        /** Overall sky visibility (0-1). 0 = cave, 1 = fully open sky. */
        public final float visibility;
        
        /** Horizon weight (0-1). 0 = zenith visible (open sky), 1 = only horizon visible (overhang). */
        public final float horizonWeight;

        public VisibilityResult(float visibility, float horizonWeight) {
            this.visibility = visibility;
            this.horizonWeight = horizonWeight;
        }

        /** Fully occluded (cave/underground). */
        public static final VisibilityResult NONE = new VisibilityResult(0.0f, 0.0f);
        
        /** Fully open sky. */
        public static final VisibilityResult FULL = new VisibilityResult(1.0f, 0.3f);
    }

    /**
     * Compute sky visibility for a position in the world.
     * 
     * Traces rays upward in 8 horizontal directions plus straight up to find
     * what fraction of the sky hemisphere is visible.
     * 
     * @param world World access for block queries
     * @param worldX World X coordinate
     * @param worldY World Y coordinate
     * @param worldZ World Z coordinate
     * @return VisibilityResult with visibility (0-1) and horizonWeight (0-1)
     */
    public static VisibilityResult computeSkyVisibility(WorldAccess world, 
                                                         int worldX, int worldY, int worldZ) {
        // Quick check: if we're at or above world height, full visibility
        if (worldY >= WorldConstants.WORLD_HEIGHT - 1) {
            return VisibilityResult.FULL;
        }

        // First, check straight up (zenith visibility)
        boolean zenithClear = isZenithClear(world, worldX, worldY, worldZ);
        
        if (!zenithClear) {
            // Zenith blocked - check if we have any horizon visibility
            float horizonVisibility = computeHorizonVisibility(world, worldX, worldY, worldZ);
            
            if (horizonVisibility <= 0.01f) {
                // Completely enclosed (cave)
                return VisibilityResult.NONE;
            }
            
            // Under an overhang - some horizon visible, no zenith
            // horizonWeight = 1.0 means only horizon light (warm tint at sunset)
            return new VisibilityResult(horizonVisibility * 0.4f, 1.0f);
        }

        // Zenith is clear - compute how much of the horizon is also visible
        float horizonVisibility = computeHorizonVisibility(world, worldX, worldY, worldZ);
        
        // Combine zenith and horizon visibility
        // Open sky: zenith clear + most horizon clear = high visibility, low horizon weight
        // Partial cover: zenith clear but horizon blocked = medium visibility, low horizon weight
        float totalVisibility = 0.6f + horizonVisibility * 0.4f;
        float horizonWeight = 0.3f + (1.0f - horizonVisibility) * 0.2f;
        
        return new VisibilityResult(totalVisibility, horizonWeight);
    }

    /**
     * Check if there's a clear path straight up to the sky.
     * This is the simple column check from Phase 1.
     */
    private static boolean isZenithClear(WorldAccess world, int x, int y, int z) {
        for (int checkY = y + 1; checkY < WorldConstants.WORLD_HEIGHT; checkY++) {
            int blockId = world.getBlock(x, checkY, z);
            Block block = Blocks.get(blockId);
            if (block.solid() && !block.transparent()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compute what fraction of the horizon is visible.
     * Traces rays outward and upward in 8 directions.
     * 
     * @return 0-1 fraction of horizon directions that are clear
     */
    private static float computeHorizonVisibility(WorldAccess world, 
                                                   int startX, int startY, int startZ) {
        int clearDirections = 0;
        
        for (int[] dir : HORIZONTAL_DIRS) {
            if (isDirectionClear(world, startX, startY, startZ, dir[0], dir[1])) {
                clearDirections++;
            }
        }
        
        return clearDirections / (float) HORIZONTAL_DIRS.length;
    }

    /**
     * Check if a direction toward the horizon is clear (can see sky in that direction).
     * Traces a ray outward and slightly upward.
     */
    private static boolean isDirectionClear(WorldAccess world, 
                                            int startX, int startY, int startZ,
                                            int dx, int dz) {
        // Trace outward, looking for a path to sky
        // We consider the direction "clear" if we can:
        // 1. Travel outward without hitting solid blocks at eye level
        // 2. Eventually reach a position where the sky is visible above
        
        int x = startX;
        int z = startZ;
        
        for (int step = 1; step <= MAX_TRACE_DISTANCE; step++) {
            x = startX + dx * step;
            z = startZ + dz * step;
            
            // Check if this position has sky access
            boolean canSeeSkyHere = canPositionSeeSky(world, x, startY, z);
            if (canSeeSkyHere) {
                return true;
            }
            
            // Check if we're blocked at eye level (can't continue tracing)
            int blockAtLevel = world.getBlock(x, startY, z);
            Block block = Blocks.get(blockAtLevel);
            if (block.solid() && !block.transparent()) {
                // Path blocked - check if we can see over it
                // Try looking up from current position
                for (int upStep = 1; upStep <= 4; upStep++) {
                    if (startY + upStep >= WorldConstants.WORLD_HEIGHT) {
                        return true; // Reached sky
                    }
                    int blockAbove = world.getBlock(x, startY + upStep, z);
                    Block aboveBlock = Blocks.get(blockAbove);
                    if (aboveBlock.solid() && !aboveBlock.transparent()) {
                        return false; // Wall continues up
                    }
                    if (canPositionSeeSky(world, x, startY + upStep, z)) {
                        return true;
                    }
                }
                return false;
            }
        }
        
        // Reached max distance - check final position for sky access
        return canPositionSeeSky(world, x, startY, z);
    }

    /**
     * Quick check if a position can see the sky (simplified column check).
     */
    private static boolean canPositionSeeSky(WorldAccess world, int x, int y, int z) {
        // Check a limited distance up (not the full world height for performance)
        int checkLimit = Math.min(y + VERTICAL_CHECK_HEIGHT, WorldConstants.WORLD_HEIGHT);
        
        for (int checkY = y + 1; checkY < checkLimit; checkY++) {
            int blockId = world.getBlock(x, checkY, z);
            Block block = Blocks.get(blockId);
            if (block.solid() && !block.transparent()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fast visibility computation for meshing.
     * Uses the existing column-based visibility as a base and adds horizon check
     * only when under an overhang (zenith blocked).
     * 
     * @param world World access
     * @param worldX World X coordinate
     * @param worldY World Y coordinate  
     * @param worldZ World Z coordinate
     * @param existingVisibility The simple column-based visibility already computed
     * @return VisibilityResult with visibility and horizonWeight
     */
    public static VisibilityResult computeWithHint(WorldAccess world,
                                                    int worldX, int worldY, int worldZ,
                                                    float existingVisibility) {
        if (worldY >= WorldConstants.WORLD_HEIGHT - 1) {
            return VisibilityResult.FULL;
        }

        if (existingVisibility > 0.5f) {
            // Zenith is clear (column visibility says so)
            // Quick horizon check for horizon weight
            float horizonVis = computeHorizonVisibilityFast(world, worldX, worldY, worldZ);
            float totalVis = 0.6f + horizonVis * 0.4f;
            float horizonWeight = 0.3f + (1.0f - horizonVis) * 0.2f;
            return new VisibilityResult(totalVis, horizonWeight);
        }
        
        // Zenith blocked - check horizon
        float horizonVis = computeHorizonVisibilityFast(world, worldX, worldY, worldZ);
        
        if (horizonVis <= 0.01f) {
            return VisibilityResult.NONE;
        }
        
        // Under overhang
        return new VisibilityResult(horizonVis * 0.4f, 1.0f);
    }

    /**
     * Fast horizon visibility check - only traces 4 cardinal directions.
     * Used during meshing for better performance.
     */
    private static float computeHorizonVisibilityFast(WorldAccess world,
                                                       int startX, int startY, int startZ) {
        int clearDirections = 0;
        
        // Only check 4 cardinal directions (N, E, S, W)
        int[][] fastDirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        
        for (int[] dir : fastDirs) {
            if (isDirectionClearFast(world, startX, startY, startZ, dir[0], dir[1])) {
                clearDirections++;
            }
        }
        
        return clearDirections / 4.0f;
    }

    /**
     * Fast direction check with shorter trace distance.
     */
    private static boolean isDirectionClearFast(WorldAccess world,
                                                 int startX, int startY, int startZ,
                                                 int dx, int dz) {
        // Shorter trace for performance
        int maxDist = 16;
        int x = startX;
        int z = startZ;
        
        for (int step = 1; step <= maxDist; step++) {
            x = startX + dx * step;
            z = startZ + dz * step;
            
            // Check if blocked at this level
            int blockId = world.getBlock(x, startY, z);
            Block block = Blocks.get(blockId);
            
            if (block.solid() && !block.transparent()) {
                // Hit a wall - direction is blocked
                return false;
            }
            
            // Check if this position has quick sky access (just 8 blocks up)
            boolean blocked = false;
            for (int up = 1; up <= 8; up++) {
                int checkY = startY + up;
                if (checkY >= WorldConstants.WORLD_HEIGHT) {
                    return true; // Reached sky
                }
                int above = world.getBlock(x, checkY, z);
                Block aboveBlock = Blocks.get(above);
                if (aboveBlock.solid() && !aboveBlock.transparent()) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                return true; // Found sky access in this direction
            }
        }
        
        return false;
    }
}
