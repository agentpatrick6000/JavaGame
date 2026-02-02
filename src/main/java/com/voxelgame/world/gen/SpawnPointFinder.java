package com.voxelgame.world.gen;

import com.voxelgame.world.WorldConstants;

/**
 * Finds a suitable player spawn point.
 * Handles different world types:
 * - Standard: searches for grass above sea level
 * - Flat world: spawns at surface of flat world
 * - Floating islands: searches for highest solid block
 */
public class SpawnPointFinder {

    public record SpawnPoint(double x, double y, double z) {}

    public static SpawnPoint find(GenContext context) {
        GenConfig config = context.getConfig();

        if (config.flatWorld) {
            return findFlatSpawn(config);
        }

        if (config.floatingIslands) {
            return findIslandSpawn(context, config);
        }

        return findStandardSpawn(context, config);
    }

    /** Standard terrain: search outward from origin for suitable land. */
    private static SpawnPoint findStandardSpawn(GenContext context, GenConfig config) {
        int maxRadius = 200;
        int seaLevel = config.seaLevel;

        for (int radius = 0; radius < maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int height = context.getTerrainHeight(dx, dz);

                    if (height > seaLevel + 2 && height < seaLevel + 30) {
                        return new SpawnPoint(dx + 0.5, height + 1 + 1.62, dz + 0.5);
                    }
                }
            }
        }

        int fallbackHeight = context.getTerrainHeight(0, 0);
        return new SpawnPoint(0.5, fallbackHeight + 1 + 1.62, 0.5);
    }

    /** Flat world: spawn at surface (layers height). */
    private static SpawnPoint findFlatSpawn(GenConfig config) {
        int surfaceY = (config.flatLayers != null) ? config.flatLayers.length : config.flatWorldHeight + 1;
        return new SpawnPoint(0.5, surfaceY + 1.62, 0.5);
    }

    /** Floating islands: search for a solid block in the island band. */
    private static SpawnPoint findIslandSpawn(GenContext context, GenConfig config) {
        int midY = (config.islandMinY + config.islandMaxY) / 2;

        // Search outward in a spiral for a position with solid ground
        for (int radius = 0; radius < 100; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    // Try to find surface by height query
                    int height = context.getTerrainHeight(dx, dz);
                    if (height > config.islandMinY && height < config.islandMaxY) {
                        return new SpawnPoint(dx + 0.5, height + 1 + 1.62, dz + 0.5);
                    }
                }
            }
        }

        // Fallback: spawn in the middle of the island band
        return new SpawnPoint(0.5, midY + 20 + 1.62, 0.5);
    }
}
