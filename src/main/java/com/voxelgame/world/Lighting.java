package com.voxelgame.world;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Unified lighting system - Phase 1 implementation.
 * 
 * Sky Visibility: Simple column-based computation (no BFS propagation).
 * A block has sky visibility = 1.0 if there's an unobstructed path straight up
 * to the sky; 0.0 otherwise. This is computed per-column, not propagated laterally.
 * 
 * Block Light: Still uses BFS flood-fill from emissive blocks (torches, etc.).
 * Will be upgraded to RGB in Phase 4.
 * 
 * The shader computes actual sky/sun RGB from visibility at render time,
 * making lighting respond dynamically to time-of-day without remeshing.
 */
public class Lighting {

    private static final int MAX_LIGHT = 15;

    // 6 directions: +X, -X, +Y, -Y, +Z, -Z
    private static final int[][] DIRS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    // ========================================================================
    // SKY VISIBILITY - Simple column-based computation
    // ========================================================================

    /**
     * Compute initial sky visibility for a newly generated chunk.
     * Simple column pass: trace straight down from sky, visibility = 1.0 until
     * we hit an opaque block, then 0.0 for everything below.
     * 
     * No lateral propagation - that's handled by the shader now.
     */
    public static void computeInitialSkyVisibility(Chunk chunk) {
        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                computeColumnVisibility(chunk, x, z);
            }
        }
        chunk.setLightDirty(false);
    }

    /**
     * Compute sky visibility for a single column in the chunk.
     * Blocks have visibility 1.0 if they can see the sky directly above,
     * 0.0 if there's any opaque block above them.
     */
    private static void computeColumnVisibility(Chunk chunk, int x, int z) {
        boolean canSeeSky = true;
        
        for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
            int blockId = chunk.getBlock(x, y, z);
            Block block = Blocks.get(blockId);
            
            if (isOpaque(block)) {
                // This block is opaque - it and everything below can't see sky
                canSeeSky = false;
                chunk.setSkyVisibility(x, y, z, 0.0f);
            } else if (canSeeSky) {
                // Transparent block with clear sky above
                chunk.setSkyVisibility(x, y, z, 1.0f);
            } else {
                // Transparent block but something opaque is above
                chunk.setSkyVisibility(x, y, z, 0.0f);
            }
        }
    }

    /**
     * Update sky visibility after a block is removed (broken).
     * If this opens up a column to the sky, update visibility for this column.
     * Returns affected chunk positions for mesh rebuild.
     */
    public static Set<ChunkPos> onBlockRemoved(World world, int wx, int wy, int wz) {
        Set<ChunkPos> affectedChunks = new HashSet<>();
        
        // Check if this block was blocking sky visibility
        // Look up to see if there's now a clear path to sky
        boolean clearAbove = true;
        for (int y = wy + 1; y < WorldConstants.WORLD_HEIGHT; y++) {
            int above = world.getBlock(wx, y, wz);
            if (isOpaque(Blocks.get(above))) {
                clearAbove = false;
                break;
            }
        }
        
        if (clearAbove) {
            // This column now has sky access - update visibility for this block and below
            for (int y = wy; y >= 0; y--) {
                int blockId = world.getBlock(wx, y, wz);
                Block block = Blocks.get(blockId);
                
                if (isOpaque(block)) {
                    // Hit an opaque block - stop propagating visibility
                    break;
                }
                
                // Update visibility to 1.0 (can see sky)
                float currentVis = world.getSkyVisibility(wx, y, wz);
                if (currentVis < 1.0f) {
                    world.setSkyVisibility(wx, y, wz, 1.0f);
                    addAffectedChunk(affectedChunks, wx, y, wz);
                }
            }
        }
        
        // The removed block's position now gets visibility based on column state
        world.setSkyVisibility(wx, wy, wz, clearAbove ? 1.0f : 0.0f);
        addAffectedChunk(affectedChunks, wx, wy, wz);
        
        return affectedChunks;
    }

    /**
     * Update sky visibility after a block is placed.
     * If this block is opaque, it blocks sky for everything below.
     * Returns affected chunk positions for mesh rebuild.
     */
    public static Set<ChunkPos> onBlockPlaced(World world, int wx, int wy, int wz) {
        Set<ChunkPos> affectedChunks = new HashSet<>();
        
        int blockId = world.getBlock(wx, wy, wz);
        Block block = Blocks.get(blockId);
        
        if (isOpaque(block)) {
            // Opaque block placed - it blocks sky visibility for itself and below
            world.setSkyVisibility(wx, wy, wz, 0.0f);
            addAffectedChunk(affectedChunks, wx, wy, wz);
            
            // Check if this was a sky-visible column - if so, update everything below
            for (int y = wy - 1; y >= 0; y--) {
                float currentVis = world.getSkyVisibility(wx, y, wz);
                if (currentVis > 0.0f) {
                    int belowId = world.getBlock(wx, y, wz);
                    Block belowBlock = Blocks.get(belowId);
                    
                    if (isOpaque(belowBlock)) {
                        // Already opaque, visibility was already 0 or will be set
                        break;
                    }
                    
                    // Transparent block that lost sky visibility
                    world.setSkyVisibility(wx, y, wz, 0.0f);
                    addAffectedChunk(affectedChunks, wx, y, wz);
                } else {
                    // Already had no visibility, nothing below will have it either
                    break;
                }
            }
        } else {
            // Transparent block placed - inherits visibility from column state
            boolean clearAbove = true;
            for (int y = wy + 1; y < WorldConstants.WORLD_HEIGHT; y++) {
                int above = world.getBlock(wx, y, wz);
                if (isOpaque(Blocks.get(above))) {
                    clearAbove = false;
                    break;
                }
            }
            world.setSkyVisibility(wx, wy, wz, clearAbove ? 1.0f : 0.0f);
            addAffectedChunk(affectedChunks, wx, wy, wz);
        }
        
        return affectedChunks;
    }

    // ========================================================================
    // BLOCK LIGHT SYSTEM - Phase 4: RGB propagation with nonlinear falloff
    // ========================================================================
    
    /** Nonlinear falloff factor: multiply by this each block traveled. */
    private static final float RGB_FALLOFF = 0.8f;
    
    /** Minimum light value to continue propagation (avoids infinite spread). */
    private static final float RGB_MIN_THRESHOLD = 0.01f;

    /**
     * Compute initial RGB block light for a newly generated chunk.
     * Scans for light-emitting blocks and propagates colored light from them.
     * Phase 4: Uses RGB values with nonlinear falloff instead of scalar 0-15.
     */
    public static void computeInitialBlockLight(Chunk chunk, World world) {
        ChunkPos cPos = chunk.getPos();
        int cx = cPos.x() * WorldConstants.CHUNK_SIZE;
        int cz = cPos.z() * WorldConstants.CHUNK_SIZE;

        // Clear existing RGB light in this chunk
        chunk.clearBlockLightRGB();
        
        // Queue stores: [wx, wy, wz, Float.floatToIntBits(r), Float.floatToIntBits(g), Float.floatToIntBits(b)]
        Queue<long[]> bfsQueue = new ArrayDeque<>();

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                    int blockId = chunk.getBlock(x, y, z);
                    float[] lightColor = LightEmitters.getLightColorRGB(blockId);
                    if (lightColor != null) {
                        chunk.setBlockLightRGB(x, y, z, lightColor[0], lightColor[1], lightColor[2]);
                        bfsQueue.add(new long[]{cx + x, y, cz + z, 
                            Float.floatToIntBits(lightColor[0]),
                            Float.floatToIntBits(lightColor[1]),
                            Float.floatToIntBits(lightColor[2])});
                    }
                }
            }
        }

        propagateBlockLightRGBBFS(bfsQueue, world);
    }

    /**
     * BFS flood-fill RGB block light propagation with nonlinear falloff.
     * Phase 4: Each channel attenuates by RGB_FALLOFF per block traveled.
     */
    private static void propagateBlockLightRGBBFS(Queue<long[]> queue, World world) {
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int wx = (int) entry[0];
            int wy = (int) entry[1];
            int wz = (int) entry[2];
            float r = Float.intBitsToFloat((int) entry[3]);
            float g = Float.intBitsToFloat((int) entry[4]);
            float b = Float.intBitsToFloat((int) entry[5]);

            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];

                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int neighborBlock = world.getBlock(nx, ny, nz);
                Block nBlock = Blocks.get(neighborBlock);

                if (isOpaque(nBlock)) continue;

                // Nonlinear falloff: multiply by RGB_FALLOFF
                float extraReduction = getLightReductionFactor(nBlock);
                float falloff = RGB_FALLOFF * extraReduction;
                float nr = r * falloff;
                float ng = g * falloff;
                float nb = b * falloff;

                // Stop if all channels are too dim
                if (nr < RGB_MIN_THRESHOLD && ng < RGB_MIN_THRESHOLD && nb < RGB_MIN_THRESHOLD) continue;

                // Only update if we're brighter than current in any channel
                float[] current = world.getBlockLightRGB(nx, ny, nz);
                if (nr > current[0] || ng > current[1] || nb > current[2]) {
                    // Take max of each channel (allows multiple colored lights to blend)
                    float newR = Math.max(nr, current[0]);
                    float newG = Math.max(ng, current[1]);
                    float newB = Math.max(nb, current[2]);
                    world.setBlockLightRGB(nx, ny, nz, newR, newG, newB);
                    queue.add(new long[]{nx, ny, nz, 
                        Float.floatToIntBits(nr),
                        Float.floatToIntBits(ng),
                        Float.floatToIntBits(nb)});
                }
            }
        }
    }

    /**
     * Add block light when a light-emitting block is placed (e.g., torch).
     * Phase 4: Propagates RGB light with nonlinear falloff.
     */
    public static Set<ChunkPos> onLightSourcePlaced(World world, int wx, int wy, int wz) {
        Set<ChunkPos> affectedChunks = new HashSet<>();
        int blockId = world.getBlock(wx, wy, wz);
        float[] lightColor = LightEmitters.getLightColorRGB(blockId);
        if (lightColor == null) return affectedChunks;

        world.setBlockLightRGB(wx, wy, wz, lightColor[0], lightColor[1], lightColor[2]);
        addAffectedChunk(affectedChunks, wx, wy, wz);

        Queue<long[]> bfsQueue = new ArrayDeque<>();
        bfsQueue.add(new long[]{wx, wy, wz,
            Float.floatToIntBits(lightColor[0]),
            Float.floatToIntBits(lightColor[1]),
            Float.floatToIntBits(lightColor[2])});
        propagateBlockLightRGBBFSTracked(bfsQueue, world, affectedChunks);

        return affectedChunks;
    }

    /**
     * Remove block light when a light source is removed (e.g., torch broken).
     * Phase 4: Clears RGB light and re-propagates from remaining sources.
     */
    public static Set<ChunkPos> onLightSourceRemoved(World world, int wx, int wy, int wz, int oldEmission) {
        Set<ChunkPos> affectedChunks = new HashSet<>();
        if (oldEmission <= 0) return affectedChunks;

        // Calculate max radius that could have been affected
        // With 0.8 falloff: 0.8^n < 0.01 means n > log(0.01)/log(0.8) ≈ 21 blocks
        int maxRadius = 25;

        // Clear light in affected area and collect light sources for re-propagation
        Queue<long[]> reproQueue = new ArrayDeque<>();
        
        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dy = -maxRadius; dy <= maxRadius; dy++) {
                for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                    int nx = wx + dx;
                    int ny = wy + dy;
                    int nz = wz + dz;
                    
                    if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;
                    
                    float[] rgb = world.getBlockLightRGB(nx, ny, nz);
                    if (rgb[0] > 0 || rgb[1] > 0 || rgb[2] > 0) {
                        // Check if this is a light source itself
                        int nBlockId = world.getBlock(nx, ny, nz);
                        float[] sourceColor = LightEmitters.getLightColorRGB(nBlockId);
                        
                        if (sourceColor != null && !(nx == wx && ny == wy && nz == wz)) {
                            // This is another light source - add to repro queue
                            reproQueue.add(new long[]{nx, ny, nz,
                                Float.floatToIntBits(sourceColor[0]),
                                Float.floatToIntBits(sourceColor[1]),
                                Float.floatToIntBits(sourceColor[2])});
                        }
                        
                        // Clear light at this position
                        world.setBlockLightRGB(nx, ny, nz, 0, 0, 0);
                        addAffectedChunk(affectedChunks, nx, ny, nz);
                    }
                }
            }
        }

        // Re-propagate from remaining light sources
        propagateBlockLightRGBBFSTracked(reproQueue, world, affectedChunks);

        return affectedChunks;
    }

    /**
     * BFS RGB block light propagation that tracks affected chunks.
     */
    private static void propagateBlockLightRGBBFSTracked(Queue<long[]> queue, World world, Set<ChunkPos> affected) {
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int wx = (int) entry[0];
            int wy = (int) entry[1];
            int wz = (int) entry[2];
            float r = Float.intBitsToFloat((int) entry[3]);
            float g = Float.intBitsToFloat((int) entry[4]);
            float b = Float.intBitsToFloat((int) entry[5]);

            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];

                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int neighborBlock = world.getBlock(nx, ny, nz);
                Block nBlock = Blocks.get(neighborBlock);

                if (isOpaque(nBlock)) continue;

                float extraReduction = getLightReductionFactor(nBlock);
                float falloff = RGB_FALLOFF * extraReduction;
                float nr = r * falloff;
                float ng = g * falloff;
                float nb = b * falloff;

                if (nr < RGB_MIN_THRESHOLD && ng < RGB_MIN_THRESHOLD && nb < RGB_MIN_THRESHOLD) continue;

                float[] current = world.getBlockLightRGB(nx, ny, nz);
                if (nr > current[0] || ng > current[1] || nb > current[2]) {
                    float newR = Math.max(nr, current[0]);
                    float newG = Math.max(ng, current[1]);
                    float newB = Math.max(nb, current[2]);
                    world.setBlockLightRGB(nx, ny, nz, newR, newG, newB);
                    addAffectedChunk(affected, nx, ny, nz);
                    queue.add(new long[]{nx, ny, nz,
                        Float.floatToIntBits(nr),
                        Float.floatToIntBits(ng),
                        Float.floatToIntBits(nb)});
                }
            }
        }
    }

    // ========================================================================
    // LEGACY API - For backward compatibility during transition
    // ========================================================================

    /**
     * Legacy method - redirects to computeInitialSkyVisibility.
     * @deprecated Use {@link #computeInitialSkyVisibility(Chunk)} instead.
     */
    @Deprecated
    public static void computeInitialSkyLight(Chunk chunk, World world) {
        computeInitialSkyVisibility(chunk);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /** Check if a block is opaque (blocks light completely). */
    private static boolean isOpaque(Block block) {
        return block.solid() && !block.transparent();
    }

    /** Get light reduction for transparent blocks. Water and leaves reduce light. */
    private static int getLightReduction(Block block) {
        if (block.id() == Blocks.WATER.id()) return 2;
        if (block.id() == Blocks.LEAVES.id()) return 1;
        return 0;
    }

    /** Get light reduction factor (0-1 multiplier) for Phase 4 RGB propagation. */
    private static float getLightReductionFactor(Block block) {
        if (Blocks.isWater(block.id())) return 0.7f;  // Water reduces light more
        if (block.id() == Blocks.LEAVES.id()) return 0.85f;  // Leaves slightly reduce light
        return 1.0f;  // No extra reduction
    }

    private static void addAffectedChunk(Set<ChunkPos> set, int wx, int wy, int wz) {
        int cx = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE);
        set.add(new ChunkPos(cx, cz));
    }

    private static long packPos(int x, int y, int z) {
        return ((long)(x + 30000000) << 36) | ((long)(y & 0xFFF) << 24) | ((long)(z + 30000000) & 0xFFFFFFL);
    }
}
