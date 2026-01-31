package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Cave carving pass. Uses dual 3D Perlin noise (cheese caves) to carve
 * tunnel systems through terrain. Cave density increases with depth.
 * Avoids breaking through the surface unless on a hillside.
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

                    double freq = config.caveFreq;

                    // Sample two noise fields — cave exists where both are high
                    double n1 = context.getCaveNoise1().eval3D(
                        worldX * freq, y * freq, worldZ * freq);
                    double n2 = context.getCaveNoise2().eval3D(
                        worldX * freq + 500, y * freq + 500, worldZ * freq + 500);

                    // Use the product of the two noise values for spaghetti-like caves
                    // Both must be above threshold for a cave to exist
                    double combined = n1 * n1 + n2 * n2;

                    // Depth factor: caves are more common deeper down
                    double depthFactor = 1.0 - ((double) y / surfaceHeight);
                    depthFactor = 0.7 + depthFactor * 0.3; // range [0.7, 1.0]

                    // Cave threshold — lower combined value = more likely to be cave
                    // When combined < threshold, we carve
                    double threshold = config.caveThreshold * depthFactor;

                    if (combined < threshold * threshold * 0.3) {
                        // Don't carve if block directly above is air/grass (surface protection)
                        // But allow it if we're deep enough below surface
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
