package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Cave carving pass. Uses multiple 3D Perlin noise systems to create
 * InfDev 611-style cave networks:
 * - Primary spaghetti caves (two noise fields, carve where both near zero)
 * - Secondary tunnel system at different frequency
 * - Large cavern rooms
 * - Vertical cave shafts
 * - Depth-dependent density (more caves deeper down)
 */
public class CarveCavesPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                int worldX = chunkWorldX + lx;
                int worldZ = chunkWorldZ + lz;

                int surfaceHeight = context.getTerrainHeight(worldX, worldZ);

                // Carve from above bedrock up to below surface
                int maxCaveY = Math.min(surfaceHeight - config.caveSurfaceMargin,
                                        WorldConstants.WORLD_HEIGHT - 1);

                for (int y = config.caveMinY; y <= maxCaveY; y++) {
                    // Only carve solid blocks
                    int currentBlock = chunk.getBlock(lx, y, lz);
                    if (currentBlock == Blocks.AIR.id() || currentBlock == Blocks.BEDROCK.id()) {
                        continue;
                    }

                    // Depth factor: caves are more common deeper
                    double depthRatio = 1.0 - ((double) y / surfaceHeight);
                    double depthFactor = 0.5 + depthRatio * 0.5; // range [0.5, 1.0]

                    boolean shouldCarve = false;

                    // === Primary spaghetti caves ===
                    double freq = config.caveFreq;
                    double n1 = context.getCaveNoise1().eval3D(
                        worldX * freq, y * freq * 0.7, worldZ * freq);
                    double n2 = context.getCaveNoise2().eval3D(
                        worldX * freq + 500, y * freq * 0.7 + 500, worldZ * freq + 500);

                    // Spaghetti: carve where BOTH noise values are near zero
                    // Using y*0.7 makes caves wider than tall (oblate, like Classic)
                    double combined = n1 * n1 + n2 * n2;
                    double threshold = config.caveThreshold * depthFactor;
                    double thresholdSq = threshold * threshold * 0.25;

                    if (combined < thresholdSq) {
                        shouldCarve = true;
                    }

                    // === Secondary tunnel system (different frequency for interconnections) ===
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

                    // === Large cavern rooms (low frequency, wider threshold) ===
                    if (!shouldCarve) {
                        double roomFreq = freq * 0.35;
                        double roomN = context.getCaveNoise1().eval3D(
                            worldX * roomFreq + 1000, y * roomFreq * 0.5 + 1000, worldZ * roomFreq + 1000);
                        if (Math.abs(roomN) < 0.06 * depthFactor) {
                            shouldCarve = true;
                        }
                    }

                    // === Vertical cave shafts ===
                    if (!shouldCarve && y < surfaceHeight - 10) {
                        double vFreq = config.verticalCaveFreq;
                        // Vertical shafts: sample 2D noise (x,z only) — same carve column top to bottom
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
                        // Extra surface protection: don't carve within margin of surface
                        if (y >= surfaceHeight - config.caveSurfaceMargin) {
                            continue;
                        }
                        chunk.setBlock(lx, y, lz, Blocks.AIR.id());
                    }
                }
            }
        }
    }
}
