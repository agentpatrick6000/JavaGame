package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Cave carving pass. Uses 3D Perlin noise to create cave networks.
 * Works with the 3D density terrain by scanning actual chunk blocks
 * to find the surface, rather than relying on a height function.
 *
 * Cave types:
 * - Primary spaghetti caves (two noise fields intersecting near zero)
 * - Vertical cave shafts
 * - Depth-dependent density (more caves deeper)
 */
public class CarveCavesPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();
        if (!config.cavesEnabled) return;  // skip if caves disabled

        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                int worldX = chunkWorldX + lx;
                int worldZ = chunkWorldZ + lz;

                // Find actual surface by scanning down from top
                int surfaceHeight = findSurfaceHeight(chunk, lx, lz);

                // Don't carve above terrain or near surface
                int maxCaveY = Math.min(surfaceHeight - config.caveSurfaceMargin,
                                        WorldConstants.WORLD_HEIGHT - 1);

                for (int y = config.caveMinY; y <= maxCaveY; y++) {
                    int currentBlock = chunk.getBlock(lx, y, lz);
                    if (currentBlock == Blocks.AIR.id() ||
                        currentBlock == Blocks.BEDROCK.id() ||
                        Blocks.isWater(currentBlock) || Blocks.isLava(currentBlock)) {
                        continue;
                    }

                    // Depth factor: caves more common deeper
                    double depthRatio = 1.0 - ((double) y / Math.max(surfaceHeight, 1));
                    double depthFactor = 0.5 + depthRatio * 0.5;

                    boolean shouldCarve = false;

                    // === Spaghetti caves ===
                    double freq = config.caveFreq;
                    double n1 = context.getCaveNoise1().eval3D(
                        worldX * freq, y * freq * 0.7, worldZ * freq);
                    double n2 = context.getCaveNoise2().eval3D(
                        worldX * freq + 500, y * freq * 0.7 + 500, worldZ * freq + 500);

                    double combined = n1 * n1 + n2 * n2;
                    double threshold = config.caveThreshold * depthFactor;
                    double thresholdSq = threshold * threshold * 0.25;

                    if (combined < thresholdSq) {
                        shouldCarve = true;
                    }

                    // === Secondary tunnel system ===
                    if (!shouldCarve) {
                        double freq2 = freq * 0.65;
                        double t1 = context.getCaveNoise2().eval3D(
                            worldX * freq2 + 2000, y * freq2 * 0.7 + 2000, worldZ * freq2 + 2000);
                        double t2 = context.getCaveNoise1().eval3D(
                            worldX * freq2 + 3000, y * freq2 * 0.7 + 3000, worldZ * freq2 + 3000);
                        double combined2 = t1 * t1 + t2 * t2;

                        if (combined2 < thresholdSq * 0.7) {
                            shouldCarve = true;
                        }
                    }

                    // === Vertical cave shafts ===
                    if (!shouldCarve && y < surfaceHeight - 10) {
                        double vFreq = config.verticalCaveFreq;
                        double v1 = context.getCaveNoise3().eval3D(
                            worldX * vFreq, y * vFreq * 2.0, worldZ * vFreq);
                        double v2 = context.getCaveNoise3().eval3D(
                            worldX * vFreq + 7000, y * vFreq * 2.0 + 7000, worldZ * vFreq + 7000);
                        double verticalCombined = v1 * v1 + v2 * v2;

                        if (verticalCombined < config.verticalCaveThreshold * depthFactor) {
                            shouldCarve = true;
                        }
                    }

                    if (shouldCarve) {
                        // Don't carve through water or near surface
                        if (y >= surfaceHeight - config.caveSurfaceMargin) continue;
                        
                        // Don't carve if there's water above (prevents draining oceans)
                        if (y < WorldConstants.SEA_LEVEL) {
                            int aboveBlock = chunk.getBlock(lx, y + 1, lz);
                            if (Blocks.isWater(aboveBlock)) continue;
                        }
                        
                        chunk.setBlock(lx, y, lz, Blocks.AIR.id());
                    }
                }
            }
        }
    }

    /**
     * Find the actual surface height by scanning the chunk column top-down.
     * Returns the Y of the highest non-air, non-water block.
     */
    private int findSurfaceHeight(Chunk chunk, int lx, int lz) {
        for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
            int block = chunk.getBlock(lx, y, lz);
            if (block != Blocks.AIR.id() && !Blocks.isFluid(block)) {
                return y;
            }
        }
        return 0;
    }
}
