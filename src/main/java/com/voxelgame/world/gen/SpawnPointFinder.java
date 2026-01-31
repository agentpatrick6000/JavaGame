package com.voxelgame.world.gen;

import com.voxelgame.world.WorldConstants;

/**
 * Finds a suitable player spawn point. Searches outward from (0,0)
 * for a grass block above sea level. Player spawns on the highest
 * solid block at that position.
 */
public class SpawnPointFinder {

    /**
     * Result of spawn point search.
     */
    public record SpawnPoint(double x, double y, double z) {}

    /**
     * Find a good spawn point using the generation context.
     * Searches in a spiral pattern from world origin (0,0).
     *
     * @param context Generation context with terrain height function
     * @return spawn point (x, y, z) where y is eye level above ground
     */
    public static SpawnPoint find(GenContext context) {
        // Spiral search outward from origin
        int maxRadius = 200;

        for (int radius = 0; radius < maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check the ring, not the interior (optimization)
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int wx = dx;
                    int wz = dz;

                    int height = context.getTerrainHeight(wx, wz);

                    // Must be above sea level with some margin
                    if (height > WorldConstants.SEA_LEVEL + 2 && height < 90) {
                        // Good spawn point — place player at eye level
                        // Player eye height is 1.62, so spawn at surface + 1.62
                        return new SpawnPoint(wx + 0.5, height + 1 + 1.62, wz + 0.5);
                    }
                }
            }
        }

        // Fallback: spawn at origin at a safe height
        int fallbackHeight = context.getTerrainHeight(0, 0);
        return new SpawnPoint(0.5, fallbackHeight + 1 + 1.62, 0.5);
    }
}
