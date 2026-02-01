package com.voxelgame.world;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Block lighting system. Computes sky light and block light propagation
 * using a BFS flood-fill approach.
 *
 * Sky light: sunlight from above. Level 15 at the top, propagates downward
 * through air with no decay (direct sunlight column), spreads sideways with
 * -1 per block.
 *
 * Block light: emitted by light-emitting blocks (torches etc). Currently
 * unused but the infrastructure supports it.
 */
public class Lighting {

    private static final int MAX_LIGHT = 15;

    // 6 directions: +X, -X, +Y, -Y, +Z, -Z
    private static final int[][] DIRS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    /**
     * Compute initial sky light for a newly generated chunk.
     * First does a column pass (direct sunlight straight down),
     * then BFS flood-fill for lateral propagation.
     */
    public static void computeInitialSkyLight(Chunk chunk, World world) {
        ChunkPos cPos = chunk.getPos();
        int cx = cPos.x() * WorldConstants.CHUNK_SIZE;
        int cz = cPos.z() * WorldConstants.CHUNK_SIZE;

        Queue<long[]> bfsQueue = new ArrayDeque<>();

        // Column pass: cast sunlight straight down
        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                boolean inSun = true;
                for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                    int blockId = chunk.getBlock(x, y, z);
                    Block block = Blocks.get(blockId);

                    if (isOpaque(block)) {
                        inSun = false;
                        chunk.setSkyLight(x, y, z, 0);
                    } else if (inSun) {
                        chunk.setSkyLight(x, y, z, MAX_LIGHT);
                        // Add to BFS for lateral propagation
                        bfsQueue.add(new long[]{cx + x, y, cz + z, MAX_LIGHT});
                    } else {
                        // Below an opaque block, in a transparent block — light will come from BFS
                        int reduction = getLightReduction(block);
                        if (reduction > 0) {
                            chunk.setSkyLight(x, y, z, 0);
                        } else {
                            chunk.setSkyLight(x, y, z, 0);
                        }
                    }
                }
            }
        }

        // BFS flood-fill for lateral sky light propagation
        propagateSkyLightBFS(bfsQueue, world);

        chunk.setLightDirty(false);
    }

    /**
     * BFS flood-fill sky light propagation. Works with world coordinates
     * so it naturally crosses chunk boundaries.
     */
    private static void propagateSkyLightBFS(Queue<long[]> queue, World world) {
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int wx = (int) entry[0];
            int wy = (int) entry[1];
            int wz = (int) entry[2];
            int lightLevel = (int) entry[3];

            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];

                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int neighborBlock = world.getBlock(nx, ny, nz);
                Block nBlock = Blocks.get(neighborBlock);

                if (isOpaque(nBlock)) continue;

                int reduction = getLightReduction(nBlock);
                // Sunlight going straight down doesn't decay
                int newLight;
                if (dir[1] == -1 && dir[0] == 0 && dir[2] == 0 && lightLevel == MAX_LIGHT) {
                    newLight = MAX_LIGHT - reduction;
                } else {
                    newLight = lightLevel - 1 - reduction;
                }

                if (newLight <= 0) continue;

                int currentLight = world.getSkyLight(nx, ny, nz);
                if (newLight > currentLight) {
                    world.setSkyLight(nx, ny, nz, newLight);
                    queue.add(new long[]{nx, ny, nz, newLight});
                }
            }
        }
    }

    /**
     * Recompute sky light after a block is broken (removed).
     * Light floods into the new air space and propagates outward.
     * Returns the set of chunk positions that were modified.
     */
    public static Set<ChunkPos> onBlockRemoved(World world, int wx, int wy, int wz) {
        Set<ChunkPos> affectedChunks = new HashSet<>();

        // Find the max sky light among neighbors
        int maxNeighborLight = 0;
        boolean hasSunAbove = false;

        // Check if there's direct sunlight above
        boolean sunColumn = true;
        for (int y = wy + 1; y < WorldConstants.WORLD_HEIGHT; y++) {
            int above = world.getBlock(wx, y, wz);
            if (isOpaque(Blocks.get(above))) {
                sunColumn = false;
                break;
            }
        }

        if (sunColumn) {
            hasSunAbove = true;
        }

        // Check all 6 neighbors for existing light
        for (int[] dir : DIRS) {
            int nx = wx + dir[0];
            int ny = wy + dir[1];
            int nz = wz + dir[2];
            if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;
            int nLight = world.getSkyLight(nx, ny, nz);
            maxNeighborLight = Math.max(maxNeighborLight, nLight);
        }

        // Set this block's light
        int newLight;
        if (hasSunAbove) {
            newLight = MAX_LIGHT;
        } else {
            newLight = Math.max(0, maxNeighborLight - 1);
        }

        world.setSkyLight(wx, wy, wz, newLight);
        addAffectedChunk(affectedChunks, wx, wy, wz);

        // If we have sunlight, also propagate downward through the column
        Queue<long[]> bfsQueue = new ArrayDeque<>();
        bfsQueue.add(new long[]{wx, wy, wz, newLight});

        if (hasSunAbove) {
            // Propagate sunlight down through the column
            for (int y = wy - 1; y >= 0; y--) {
                int below = world.getBlock(wx, y, wz);
                Block bBlock = Blocks.get(below);
                if (isOpaque(bBlock)) break;
                int reduction = getLightReduction(bBlock);
                int columnLight = MAX_LIGHT - reduction;
                if (columnLight <= 0) break;
                world.setSkyLight(wx, y, wz, columnLight);
                addAffectedChunk(affectedChunks, wx, y, wz);
                bfsQueue.add(new long[]{wx, y, wz, columnLight});
            }
        }

        // BFS propagate from the changed position outward
        propagateSkyLightBFSTracked(bfsQueue, world, affectedChunks);

        return affectedChunks;
    }

    /**
     * Recompute sky light after a block is placed.
     * The block now blocks light — need to remove light and re-propagate.
     * Returns the set of chunk positions that were modified.
     */
    public static Set<ChunkPos> onBlockPlaced(World world, int wx, int wy, int wz) {
        Set<ChunkPos> affectedChunks = new HashSet<>();

        // Check if this block was in a sunlight column
        boolean wasSunColumn = (world.getSkyLight(wx, wy, wz) == MAX_LIGHT);

        // The placed block is now opaque — set its light to 0
        world.setSkyLight(wx, wy, wz, 0);
        addAffectedChunk(affectedChunks, wx, wy, wz);

        // If this blocked a sunlight column, remove sunlight below
        if (wasSunColumn) {
            for (int y = wy - 1; y >= 0; y--) {
                int below = world.getBlock(wx, y, wz);
                Block bBlock = Blocks.get(below);
                if (isOpaque(bBlock)) break;
                world.setSkyLight(wx, y, wz, 0);
                addAffectedChunk(affectedChunks, wx, y, wz);
            }
        }

        // Remove light that was passing through this position using BFS
        // Collect all positions that need light removed, then re-propagate from boundaries
        Queue<long[]> removeQueue = new ArrayDeque<>();
        Queue<long[]> reproQueue = new ArrayDeque<>();

        // Seed the removal from the placed block's neighbors that had lower light
        for (int[] dir : DIRS) {
            int nx = wx + dir[0];
            int ny = wy + dir[1];
            int nz = wz + dir[2];
            if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

            int nBlockId = world.getBlock(nx, ny, nz);
            if (isOpaque(Blocks.get(nBlockId))) continue;

            int nLight = world.getSkyLight(nx, ny, nz);
            if (nLight > 0) {
                removeQueue.add(new long[]{nx, ny, nz, nLight});
            }
        }

        // Also add positions below if sun column was broken
        if (wasSunColumn) {
            for (int y = wy - 1; y >= 0; y--) {
                int below = world.getBlock(wx, y, wz);
                if (isOpaque(Blocks.get(below))) break;
                removeQueue.add(new long[]{wx, y, wz, 0});
            }
        }

        // BFS light removal
        Set<Long> visited = new HashSet<>();
        while (!removeQueue.isEmpty()) {
            long[] entry = removeQueue.poll();
            int ex = (int) entry[0];
            int ey = (int) entry[1];
            int ez = (int) entry[2];
            int oldLight = (int) entry[3];

            long key = packPos(ex, ey, ez);
            if (visited.contains(key)) continue;
            visited.add(key);

            int currentLight = world.getSkyLight(ex, ey, ez);

            for (int[] dir : DIRS) {
                int nx = ex + dir[0];
                int ny = ey + dir[1];
                int nz = ez + dir[2];
                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int nBlockId = world.getBlock(nx, ny, nz);
                if (isOpaque(Blocks.get(nBlockId))) continue;

                int nLight = world.getSkyLight(nx, ny, nz);

                if (nLight > 0 && nLight < currentLight) {
                    // This neighbor got its light from us — remove it
                    world.setSkyLight(nx, ny, nz, 0);
                    addAffectedChunk(affectedChunks, nx, ny, nz);
                    removeQueue.add(new long[]{nx, ny, nz, nLight});
                } else if (nLight >= currentLight && nLight > 0) {
                    // This neighbor has light from another source — re-propagate from here
                    reproQueue.add(new long[]{nx, ny, nz, nLight});
                }
            }

            // Also zero out this one if it wasn't already
            if (currentLight > 0) {
                world.setSkyLight(ex, ey, ez, 0);
                addAffectedChunk(affectedChunks, ex, ey, ez);
            }
        }

        // Re-propagate light from boundary sources
        propagateSkyLightBFSTracked(reproQueue, world, affectedChunks);

        return affectedChunks;
    }

    /**
     * BFS propagation that tracks which chunks were affected.
     */
    private static void propagateSkyLightBFSTracked(Queue<long[]> queue, World world, Set<ChunkPos> affected) {
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int wx = (int) entry[0];
            int wy = (int) entry[1];
            int wz = (int) entry[2];
            int lightLevel = (int) entry[3];

            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];

                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int neighborBlock = world.getBlock(nx, ny, nz);
                Block nBlock = Blocks.get(neighborBlock);

                if (isOpaque(nBlock)) continue;

                int reduction = getLightReduction(nBlock);
                int newLight;
                if (dir[1] == -1 && dir[0] == 0 && dir[2] == 0 && lightLevel == MAX_LIGHT) {
                    newLight = MAX_LIGHT - reduction;
                } else {
                    newLight = lightLevel - 1 - reduction;
                }

                if (newLight <= 0) continue;

                int currentLight = world.getSkyLight(nx, ny, nz);
                if (newLight > currentLight) {
                    world.setSkyLight(nx, ny, nz, newLight);
                    addAffectedChunk(affected, nx, ny, nz);
                    queue.add(new long[]{nx, ny, nz, newLight});
                }
            }
        }
    }

    // ======== BLOCK LIGHT SYSTEM ========

    /**
     * Compute initial block light for a newly generated chunk.
     * Scans for light-emitting blocks and propagates from them.
     */
    public static void computeInitialBlockLight(Chunk chunk, World world) {
        ChunkPos cPos = chunk.getPos();
        int cx = cPos.x() * WorldConstants.CHUNK_SIZE;
        int cz = cPos.z() * WorldConstants.CHUNK_SIZE;

        Queue<long[]> bfsQueue = new ArrayDeque<>();

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                    int blockId = chunk.getBlock(x, y, z);
                    int emission = Blocks.getLightEmission(blockId);
                    if (emission > 0) {
                        chunk.setBlockLight(x, y, z, emission);
                        bfsQueue.add(new long[]{cx + x, y, cz + z, emission});
                    }
                }
            }
        }

        propagateBlockLightBFS(bfsQueue, world);
    }

    /**
     * BFS flood-fill block light propagation.
     */
    private static void propagateBlockLightBFS(Queue<long[]> queue, World world) {
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int wx = (int) entry[0];
            int wy = (int) entry[1];
            int wz = (int) entry[2];
            int lightLevel = (int) entry[3];

            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];

                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int neighborBlock = world.getBlock(nx, ny, nz);
                Block nBlock = Blocks.get(neighborBlock);

                if (isOpaque(nBlock)) continue;

                int reduction = getLightReduction(nBlock);
                int newLight = lightLevel - 1 - reduction;

                if (newLight <= 0) continue;

                int currentLight = world.getBlockLight(nx, ny, nz);
                if (newLight > currentLight) {
                    world.setBlockLight(nx, ny, nz, newLight);
                    queue.add(new long[]{nx, ny, nz, newLight});
                }
            }
        }
    }

    /**
     * Add block light when a light-emitting block is placed (e.g., torch).
     */
    public static Set<ChunkPos> onLightSourcePlaced(World world, int wx, int wy, int wz) {
        Set<ChunkPos> affectedChunks = new HashSet<>();
        int blockId = world.getBlock(wx, wy, wz);
        int emission = Blocks.getLightEmission(blockId);
        if (emission <= 0) return affectedChunks;

        world.setBlockLight(wx, wy, wz, emission);
        addAffectedChunk(affectedChunks, wx, wy, wz);

        Queue<long[]> bfsQueue = new ArrayDeque<>();
        bfsQueue.add(new long[]{wx, wy, wz, emission});
        propagateBlockLightBFSTracked(bfsQueue, world, affectedChunks);

        return affectedChunks;
    }

    /**
     * Remove block light when a light source is removed (e.g., torch broken).
     */
    public static Set<ChunkPos> onLightSourceRemoved(World world, int wx, int wy, int wz, int oldEmission) {
        Set<ChunkPos> affectedChunks = new HashSet<>();
        if (oldEmission <= 0) return affectedChunks;

        // BFS removal: clear all light that originated from this source
        Queue<long[]> removeQueue = new ArrayDeque<>();
        Queue<long[]> reproQueue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();

        world.setBlockLight(wx, wy, wz, 0);
        addAffectedChunk(affectedChunks, wx, wy, wz);
        removeQueue.add(new long[]{wx, wy, wz, oldEmission});

        while (!removeQueue.isEmpty()) {
            long[] entry = removeQueue.poll();
            int ex = (int) entry[0];
            int ey = (int) entry[1];
            int ez = (int) entry[2];
            int oldLight = (int) entry[3];

            for (int[] dir : DIRS) {
                int nx = ex + dir[0];
                int ny = ey + dir[1];
                int nz = ez + dir[2];
                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                long key = packPos(nx, ny, nz);
                if (visited.contains(key)) continue;

                int nBlockId = world.getBlock(nx, ny, nz);
                if (isOpaque(Blocks.get(nBlockId))) continue;

                int nLight = world.getBlockLight(nx, ny, nz);
                if (nLight > 0 && nLight < oldLight) {
                    world.setBlockLight(nx, ny, nz, 0);
                    addAffectedChunk(affectedChunks, nx, ny, nz);
                    visited.add(key);
                    removeQueue.add(new long[]{nx, ny, nz, nLight});
                } else if (nLight >= oldLight && nLight > 0) {
                    reproQueue.add(new long[]{nx, ny, nz, nLight});
                }
            }
        }

        // Re-propagate from other light sources
        propagateBlockLightBFSTracked(reproQueue, world, affectedChunks);

        return affectedChunks;
    }

    /**
     * BFS block light propagation that tracks affected chunks.
     */
    private static void propagateBlockLightBFSTracked(Queue<long[]> queue, World world, Set<ChunkPos> affected) {
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int wx = (int) entry[0];
            int wy = (int) entry[1];
            int wz = (int) entry[2];
            int lightLevel = (int) entry[3];

            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];

                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int neighborBlock = world.getBlock(nx, ny, nz);
                Block nBlock = Blocks.get(neighborBlock);

                if (isOpaque(nBlock)) continue;

                int reduction = getLightReduction(nBlock);
                int newLight = lightLevel - 1 - reduction;

                if (newLight <= 0) continue;

                int currentLight = world.getBlockLight(nx, ny, nz);
                if (newLight > currentLight) {
                    world.setBlockLight(nx, ny, nz, newLight);
                    addAffectedChunk(affected, nx, ny, nz);
                    queue.add(new long[]{nx, ny, nz, newLight});
                }
            }
        }
    }

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

    private static void addAffectedChunk(Set<ChunkPos> set, int wx, int wy, int wz) {
        int cx = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE);
        set.add(new ChunkPos(cx, cz));
    }

    private static long packPos(int x, int y, int z) {
        return ((long)(x + 30000000) << 36) | ((long)(y & 0xFFF) << 24) | ((long)(z + 30000000) & 0xFFFFFFL);
    }
}
