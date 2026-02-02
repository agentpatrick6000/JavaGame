package com.voxelgame.world;

import java.util.*;

/**
 * Fluid physics simulator — Minecraft Infdev 611 style.
 *
 * Implements water and lava flow with:
 * - Source blocks (permanent) vs flowing blocks (decay without source)
 * - Water: spreads 7 blocks horizontally, prefers to flow toward edges/drops
 * - Lava: spreads 3 blocks on surface (y >= SEA_LEVEL), 7 underground
 * - Vertical fall: infinite (fluids always flow down when air below)
 * - Water + lava interactions: cobblestone/obsidian generation
 * - Scheduled tick system for performance (not every game tick)
 * - Infinite water: 2+ adjacent water sources create new source
 *
 * Water updates every 5 ticks (0.25s at 20 TPS).
 * Lava updates every 30 ticks on surface (1.5s), 10 underground (0.5s).
 */
public class FluidSimulator {

    /** Delay in ticks before water updates. */
    private static final int WATER_TICK_DELAY = 5;
    /** Delay in ticks before surface lava updates. */
    private static final int LAVA_SURFACE_TICK_DELAY = 30;
    /** Delay in ticks before underground lava updates. */
    private static final int LAVA_UNDERGROUND_TICK_DELAY = 10;
    /** Maximum spread distance for water. */
    private static final int WATER_MAX_SPREAD = 7;
    /** Maximum spread distance for lava on surface. */
    private static final int LAVA_SURFACE_MAX_SPREAD = 3;
    /** Maximum spread distance for lava underground. */
    private static final int LAVA_UNDERGROUND_MAX_SPREAD = 7;
    /** Max fluid updates per tick to prevent lag spikes. */
    private static final int MAX_UPDATES_PER_TICK = 512;
    /** Max depth for flow-toward-edge search. */
    private static final int FLOW_SEARCH_DEPTH = 4;

    private static final int[][] HORIZONTAL_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final World world;
    private long currentTick = 0;

    /**
     * Scheduled fluid updates. Key = packed position, value = tick to update.
     * Using a map for deduplication (only one update per position).
     */
    private final Map<Long, Long> scheduledUpdates = new LinkedHashMap<>();

    /** Chunks that need mesh rebuilding after fluid changes. */
    private final Set<ChunkPos> dirtyChunks = new HashSet<>();

    /** Positions where block light needs updating (lava placement/removal). */
    private final Set<Long> lightUpdates = new HashSet<>();

    public FluidSimulator(World world) {
        this.world = world;
    }

    /**
     * Advance the simulation by one tick. Processes scheduled updates.
     * Call this at 20 TPS (every 0.05 seconds).
     */
    public void tick() {
        currentTick++;

        // Collect updates ready to process
        List<long[]> ready = new ArrayList<>();
        Iterator<Map.Entry<Long, Long>> it = scheduledUpdates.entrySet().iterator();
        int count = 0;
        while (it.hasNext() && count < MAX_UPDATES_PER_TICK) {
            Map.Entry<Long, Long> entry = it.next();
            if (entry.getValue() <= currentTick) {
                long packed = entry.getKey();
                ready.add(new long[]{
                    unpackX(packed), unpackY(packed), unpackZ(packed)
                });
                it.remove();
                count++;
            }
        }

        // Process each update
        for (long[] pos : ready) {
            int x = (int) pos[0], y = (int) pos[1], z = (int) pos[2];
            int blockId = world.getBlock(x, y, z);

            if (Blocks.isWater(blockId)) {
                updateWater(x, y, z, blockId);
            } else if (Blocks.isLava(blockId)) {
                updateLava(x, y, z, blockId);
            } else if (blockId == Blocks.AIR.id()) {
                // Air block — check if neighboring fluid should flow in
                checkFluidFlowIn(x, y, z);
            }
        }
    }

    /**
     * Notify the simulator that a block changed at (x, y, z).
     * Schedules the position and its neighbors for fluid update.
     * Call after block break/place.
     */
    public void notifyBlockChange(int x, int y, int z) {
        scheduleUpdate(x, y, z, 1);
        scheduleUpdate(x + 1, y, z, 2);
        scheduleUpdate(x - 1, y, z, 2);
        scheduleUpdate(x, y + 1, z, 2);
        scheduleUpdate(x, y - 1, z, 2);
        scheduleUpdate(x, y, z + 1, 2);
        scheduleUpdate(x, y, z - 1, 2);
    }

    /**
     * Schedule a fluid update at (x, y, z) after 'delay' ticks.
     */
    public void scheduleUpdate(int x, int y, int z, int delay) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        long packed = packPos(x, y, z);
        long scheduledTick = currentTick + delay;
        // Only schedule if not already scheduled for an earlier or same time
        Long existing = scheduledUpdates.get(packed);
        if (existing == null || existing > scheduledTick) {
            scheduledUpdates.put(packed, scheduledTick);
        }
    }

    /**
     * Drain dirty chunks for mesh rebuilding. Called by GameLoop.
     */
    public Set<ChunkPos> drainDirtyChunks() {
        if (dirtyChunks.isEmpty()) return Collections.emptySet();
        Set<ChunkPos> result = new HashSet<>(dirtyChunks);
        dirtyChunks.clear();
        return result;
    }

    /**
     * Drain positions needing light updates. Called by GameLoop.
     */
    public List<long[]> drainLightUpdates() {
        if (lightUpdates.isEmpty()) return Collections.emptyList();
        List<long[]> result = new ArrayList<>();
        for (long packed : lightUpdates) {
            result.add(new long[]{unpackX(packed), unpackY(packed), unpackZ(packed)});
        }
        lightUpdates.clear();
        return result;
    }

    // ========================================================================
    // Water flow logic
    // ========================================================================

    private void updateWater(int x, int y, int z, int blockId) {
        boolean isSource = (blockId == Blocks.WATER.id());
        int currentLevel = Blocks.getWaterLevel(blockId);

        if (isSource) {
            // Source blocks are permanent — only spread
            spreadWater(x, y, z, 0);
            return;
        }

        // Flowing water: check if still has a valid source
        int effectiveLevel = calculateWaterLevel(x, y, z);

        if (effectiveLevel < 0 || effectiveLevel > 7) {
            // No source feeding this — remove
            setBlock(x, y, z, Blocks.AIR.id());
            scheduleNeighborUpdates(x, y, z, WATER_TICK_DELAY);
            return;
        }

        // Update level if changed
        if (effectiveLevel != currentLevel) {
            if (effectiveLevel == 0) {
                // Became a source (infinite water rule)
                setBlock(x, y, z, Blocks.WATER.id());
            } else {
                setBlock(x, y, z, Blocks.flowingWaterId(effectiveLevel));
            }
            scheduleNeighborUpdates(x, y, z, WATER_TICK_DELAY);
        }

        // Spread from current position
        spreadWater(x, y, z, effectiveLevel);
    }

    /**
     * Calculate what water level should be at this position based on neighbors.
     * Returns the effective level (0-7) or -1 if block should be removed.
     */
    private int calculateWaterLevel(int x, int y, int z) {
        // Check above — water from above makes this block level 1 (nearly full)
        int above = world.getBlock(x, y + 1, z);
        if (Blocks.isWater(above)) {
            return 1;
        }

        // Check for infinite water: 2+ adjacent source blocks
        int adjacentSources = 0;
        for (int[] dir : HORIZONTAL_DIRS) {
            int adj = world.getBlock(x + dir[0], y, z + dir[1]);
            if (adj == Blocks.WATER.id()) adjacentSources++;
        }
        if (adjacentSources >= 2) return 0; // Becomes a source!

        // Find minimum level among adjacent water blocks
        int minAdjacentLevel = 8;
        for (int[] dir : HORIZONTAL_DIRS) {
            int adj = world.getBlock(x + dir[0], y, z + dir[1]);
            int adjLevel = Blocks.getWaterLevel(adj);
            if (adjLevel >= 0 && adjLevel < minAdjacentLevel) {
                minAdjacentLevel = adjLevel;
            }
        }

        if (minAdjacentLevel >= 8) return -1; // No adjacent water
        return minAdjacentLevel + 1;
    }

    /**
     * Spread water from position (x, y, z) at the given level.
     */
    private void spreadWater(int x, int y, int z, int level) {
        // Priority: flow downward first
        if (y > 0) {
            int below = world.getBlock(x, y - 1, z);
            if (Blocks.canFluidReplace(below)) {
                // Flow down — nearly full flowing water below
                setBlock(x, y - 1, z, Blocks.flowingWaterId(1));
                scheduleUpdate(x, y - 1, z, WATER_TICK_DELAY);
                markDirty(x, y - 1, z);
                // Don't return — also spread horizontally for cascading falls
            } else if (Blocks.isLava(below)) {
                handleWaterLavaInteraction(x, y - 1, z, below);
            }
        }

        // Horizontal spread — limited by max spread distance
        if (level >= WATER_MAX_SPREAD) return;

        int nextLevel = level + 1;

        // Find preferred flow directions (toward edges/drops)
        boolean[] preferredDirs = findFlowDirections(x, y, z, level, true);

        for (int i = 0; i < 4; i++) {
            if (!preferredDirs[i]) continue; // Skip non-preferred directions

            int nx = x + HORIZONTAL_DIRS[i][0];
            int nz = z + HORIZONTAL_DIRS[i][1];

            int neighbor = world.getBlock(nx, y, nz);

            if (Blocks.isLava(neighbor)) {
                handleWaterLavaInteraction(nx, y, nz, neighbor);
                continue;
            }

            if (Blocks.canFluidReplace(neighbor)) {
                setBlock(nx, y, nz, Blocks.flowingWaterId(nextLevel));
                scheduleUpdate(nx, y, nz, WATER_TICK_DELAY);
                markDirty(nx, y, nz);
            } else {
                // Check if neighbor is higher-level water that should be updated
                int neighborLevel = Blocks.getWaterLevel(neighbor);
                if (neighborLevel > 0 && nextLevel < neighborLevel) {
                    setBlock(nx, y, nz, Blocks.flowingWaterId(nextLevel));
                    scheduleUpdate(nx, y, nz, WATER_TICK_DELAY);
                    markDirty(nx, y, nz);
                }
            }
        }
    }

    // ========================================================================
    // Lava flow logic
    // ========================================================================

    private void updateLava(int x, int y, int z, int blockId) {
        boolean isSource = (blockId == Blocks.LAVA.id());
        int currentLevel = Blocks.getLavaLevel(blockId);
        boolean underground = y < WorldConstants.SEA_LEVEL;
        int maxSpread = underground ? LAVA_UNDERGROUND_MAX_SPREAD : LAVA_SURFACE_MAX_SPREAD;
        int tickDelay = underground ? LAVA_UNDERGROUND_TICK_DELAY : LAVA_SURFACE_TICK_DELAY;

        if (isSource) {
            spreadLava(x, y, z, 0, maxSpread, tickDelay);
            return;
        }

        // Flowing lava: check if still has valid source
        int effectiveLevel = calculateLavaLevel(x, y, z, maxSpread);

        if (effectiveLevel < 0 || effectiveLevel > maxSpread) {
            setBlock(x, y, z, Blocks.AIR.id());
            scheduleNeighborUpdates(x, y, z, tickDelay);
            lightUpdates.add(packPos(x, y, z));
            return;
        }

        if (effectiveLevel != currentLevel) {
            if (effectiveLevel == 0) {
                setBlock(x, y, z, Blocks.LAVA.id());
            } else {
                setBlock(x, y, z, Blocks.flowingLavaId(effectiveLevel));
            }
            scheduleNeighborUpdates(x, y, z, tickDelay);
        }

        spreadLava(x, y, z, effectiveLevel, maxSpread, tickDelay);
    }

    private int calculateLavaLevel(int x, int y, int z, int maxSpread) {
        int above = world.getBlock(x, y + 1, z);
        if (Blocks.isLava(above)) return 1;

        int minAdjacentLevel = maxSpread + 1;
        for (int[] dir : HORIZONTAL_DIRS) {
            int adj = world.getBlock(x + dir[0], y, z + dir[1]);
            int adjLevel = Blocks.getLavaLevel(adj);
            if (adjLevel >= 0 && adjLevel < minAdjacentLevel) {
                minAdjacentLevel = adjLevel;
            }
        }

        if (minAdjacentLevel > maxSpread) return -1;
        return minAdjacentLevel + 1;
    }

    private void spreadLava(int x, int y, int z, int level, int maxSpread, int tickDelay) {
        // Flow downward
        if (y > 0) {
            int below = world.getBlock(x, y - 1, z);
            if (Blocks.isWater(below)) {
                handleLavaWaterInteraction(x, y - 1, z, below);
            } else if (Blocks.canFluidReplace(below)) {
                setBlock(x, y - 1, z, Blocks.flowingLavaId(1));
                scheduleUpdate(x, y - 1, z, tickDelay);
                markDirty(x, y - 1, z);
                lightUpdates.add(packPos(x, y - 1, z));
                // Check if lava should ignite flammable neighbors
                tryIgniteNeighbors(x, y - 1, z);
            }
        }

        // Horizontal spread
        if (level >= maxSpread) return;

        int nextLevel = level + 1;
        boolean[] preferredDirs = findFlowDirections(x, y, z, level, false);

        for (int i = 0; i < 4; i++) {
            if (!preferredDirs[i]) continue;

            int nx = x + HORIZONTAL_DIRS[i][0];
            int nz = z + HORIZONTAL_DIRS[i][1];
            int neighbor = world.getBlock(nx, y, nz);

            if (Blocks.isWater(neighbor)) {
                handleLavaWaterInteraction(nx, y, nz, neighbor);
                continue;
            }

            if (Blocks.canFluidReplace(neighbor)) {
                setBlock(nx, y, nz, Blocks.flowingLavaId(nextLevel));
                scheduleUpdate(nx, y, nz, tickDelay);
                markDirty(nx, y, nz);
                lightUpdates.add(packPos(nx, y, nz));
                tryIgniteNeighbors(nx, y, nz);
            } else {
                int neighborLevel = Blocks.getLavaLevel(neighbor);
                if (neighborLevel > 0 && nextLevel < neighborLevel) {
                    setBlock(nx, y, nz, Blocks.flowingLavaId(nextLevel));
                    scheduleUpdate(nx, y, nz, tickDelay);
                    markDirty(nx, y, nz);
                }
            }
        }
    }

    // ========================================================================
    // Water + Lava interaction
    // ========================================================================

    /**
     * Water flowing into a lava block.
     * Lava source → obsidian. Flowing lava → cobblestone.
     */
    private void handleWaterLavaInteraction(int x, int y, int z, int lavaBlockId) {
        if (lavaBlockId == Blocks.LAVA.id()) {
            setBlock(x, y, z, Blocks.OBSIDIAN.id());
        } else {
            setBlock(x, y, z, Blocks.COBBLESTONE.id());
        }
        markDirty(x, y, z);
        lightUpdates.add(packPos(x, y, z));
        scheduleNeighborUpdates(x, y, z, 1);
    }

    /**
     * Lava flowing into a water block.
     * Always produces cobblestone (stone in some versions, but cobble is standard).
     */
    private void handleLavaWaterInteraction(int x, int y, int z, int waterBlockId) {
        setBlock(x, y, z, Blocks.COBBLESTONE.id());
        markDirty(x, y, z);
        scheduleNeighborUpdates(x, y, z, 1);
    }

    // ========================================================================
    // Flow direction optimization (Infdev 611 "smart flow")
    // ========================================================================

    /**
     * Find preferred flow directions by looking for nearby drop-offs.
     * Water/lava prefers to flow toward the nearest edge where it can fall.
     * If no drop-off is found within search depth, all directions are valid.
     *
     * @return boolean[4] — true for each preferred direction (N/S/E/W)
     */
    private boolean[] findFlowDirections(int x, int y, int z, int level, boolean isWater) {
        boolean[] preferred = new boolean[4];
        int searchDepth = isWater ? FLOW_SEARCH_DEPTH : 2;
        int minDist = searchDepth + 1;
        boolean foundDrop = false;

        for (int i = 0; i < 4; i++) {
            int dist = findDropoff(x + HORIZONTAL_DIRS[i][0], y, z + HORIZONTAL_DIRS[i][1],
                                   HORIZONTAL_DIRS[i][0], HORIZONTAL_DIRS[i][1], searchDepth);
            if (dist >= 0) {
                if (dist < minDist) {
                    minDist = dist;
                    Arrays.fill(preferred, false);
                    preferred[i] = true;
                    foundDrop = true;
                } else if (dist == minDist) {
                    preferred[i] = true;
                }
            }
        }

        if (!foundDrop) {
            // No drop-off found — spread in all 4 directions
            Arrays.fill(preferred, true);
        }

        return preferred;
    }

    /**
     * Search along a direction for a drop-off (air or replaceable block below).
     * Returns the distance to the nearest drop-off, or -1 if none found.
     */
    private int findDropoff(int x, int y, int z, int dx, int dz, int maxDist) {
        for (int d = 0; d < maxDist; d++) {
            int bx = x + dx * d;
            int bz = z + dz * d;

            int blockHere = world.getBlock(bx, y, bz);

            // Can't flow through solid blocks
            Block block = Blocks.get(blockHere);
            if (block.solid() && !Blocks.isFluid(blockHere)) return -1;

            // Check if there's a drop below
            if (y > 0) {
                int below = world.getBlock(bx, y - 1, bz);
                if (Blocks.canFluidReplace(below)) return d;
            }
        }
        return -1;
    }

    // ========================================================================
    // Helper: when air appears next to fluid
    // ========================================================================

    /**
     * Check if any adjacent fluid should flow into this air position.
     */
    private void checkFluidFlowIn(int x, int y, int z) {
        // Check above for fluid falling down
        if (y < WorldConstants.WORLD_HEIGHT - 1) {
            int above = world.getBlock(x, y + 1, z);
            if (Blocks.isFluid(above)) {
                scheduleUpdate(x, y + 1, z, 1);
            }
        }

        // Check horizontal neighbors for fluid spreading in
        for (int[] dir : HORIZONTAL_DIRS) {
            int adj = world.getBlock(x + dir[0], y, z + dir[1]);
            if (Blocks.isFluid(adj)) {
                scheduleUpdate(x + dir[0], y, z + dir[1],
                    Blocks.isWater(adj) ? WATER_TICK_DELAY : LAVA_SURFACE_TICK_DELAY);
            }
        }
    }

    // ========================================================================
    // Lava fire spreading
    // ========================================================================

    /**
     * Check if lava should ignite flammable neighbors (logs, leaves, planks).
     * In Infdev 611, lava sets nearby flammable blocks on fire.
     * Since we don't have a fire block, we'll just destroy them.
     */
    private void tryIgniteNeighbors(int x, int y, int z) {
        int[][] allDirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] dir : allDirs) {
            int nx = x + dir[0], ny = y + dir[1], nz = z + dir[2];
            if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;
            int neighbor = world.getBlock(nx, ny, nz);
            if (isFlammable(neighbor)) {
                // 1 in 3 chance to ignite per lava tick (gradual, not instant)
                // Use position-based hash for determinism
                int h = hash(nx * 7 + ny * 13 + nz * 31 + (int)(currentTick & 0xFF));
                if ((h & 7) < 2) { // ~25% chance
                    setBlock(nx, ny, nz, Blocks.AIR.id());
                    markDirty(nx, ny, nz);
                }
            }
        }
    }

    private boolean isFlammable(int blockId) {
        return blockId == Blocks.LOG.id()
            || blockId == Blocks.LEAVES.id()
            || blockId == Blocks.PLANKS.id();
    }

    // ========================================================================
    // Utilities
    // ========================================================================

    private void setBlock(int x, int y, int z, int blockId) {
        world.setBlock(x, y, z, blockId);
        markDirty(x, y, z);
    }

    private void markDirty(int x, int y, int z) {
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        dirtyChunks.add(new ChunkPos(cx, cz));

        // Also mark adjacent chunks if on boundary
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        if (lx == 0)  dirtyChunks.add(new ChunkPos(cx - 1, cz));
        if (lx == 15) dirtyChunks.add(new ChunkPos(cx + 1, cz));
        if (lz == 0)  dirtyChunks.add(new ChunkPos(cx, cz - 1));
        if (lz == 15) dirtyChunks.add(new ChunkPos(cx, cz + 1));
    }

    private void scheduleNeighborUpdates(int x, int y, int z, int delay) {
        scheduleUpdate(x + 1, y, z, delay);
        scheduleUpdate(x - 1, y, z, delay);
        scheduleUpdate(x, y + 1, z, delay);
        scheduleUpdate(x, y - 1, z, delay);
        scheduleUpdate(x, y, z + 1, delay);
        scheduleUpdate(x, y, z - 1, delay);
    }

    private static long packPos(int x, int y, int z) {
        return ((long)(x + 30000000) << 36) | ((long)(y & 0xFFF) << 24)
            | ((long)(z + 30000000) & 0xFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int)(packed >> 36) - 30000000;
    }

    private static int unpackY(long packed) {
        return (int)((packed >> 24) & 0xFFF);
    }

    private static int unpackZ(long packed) {
        return (int)(packed & 0xFFFFFFL) - 30000000;
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }

    private static int hash(int v) {
        v = ((v >> 16) ^ v) * 0x45d9f3b;
        v = ((v >> 16) ^ v) * 0x45d9f3b;
        v = (v >> 16) ^ v;
        return v;
    }

    /** Get number of pending scheduled updates (for debug overlay). */
    public int getPendingUpdateCount() {
        return scheduledUpdates.size();
    }
}
