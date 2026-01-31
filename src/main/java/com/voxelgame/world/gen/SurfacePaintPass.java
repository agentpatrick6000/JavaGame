package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Surface painting pass. Replaces top stone layers with appropriate
 * surface blocks: grass/dirt above water, sand near beaches,
 * exposed stone on mountains, bedrock at y=0.
 */
public class SurfacePaintPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                int worldX = chunkWorldX + lx;
                int worldZ = chunkWorldZ + lz;

                int height = context.getTerrainHeight(worldX, worldZ);

                // Bedrock at y=0
                chunk.setBlock(lx, 0, lz, Blocks.BEDROCK.id());

                // Determine surface type based on height relative to sea level
                if (height >= config.mountainThreshold) {
                    // Mountain: stone exposed, maybe 1 layer of gravel on top
                    // Keep stone as-is (already placed by BaseTerrainPass)
                    // Just add a single gravel layer on the very top
                    chunk.setBlock(lx, height, lz, Blocks.GRAVEL.id());

                } else if (height <= WorldConstants.SEA_LEVEL + 1 && height >= WorldConstants.SEA_LEVEL - 2) {
                    // Beach zone: sand on top, sand below
                    for (int d = 0; d < config.beachDepth && height - d > 0; d++) {
                        chunk.setBlock(lx, height - d, lz, Blocks.SAND.id());
                    }

                } else if (height < WorldConstants.SEA_LEVEL - 2) {
                    // Below sea level (underwater floor): sand top, gravel, then stone
                    chunk.setBlock(lx, height, lz, Blocks.SAND.id());
                    if (height - 1 > 0) {
                        chunk.setBlock(lx, height - 1, lz, Blocks.SAND.id());
                    }
                    if (height - 2 > 0) {
                        chunk.setBlock(lx, height - 2, lz, Blocks.GRAVEL.id());
                    }

                } else {
                    // Normal terrain above sea level: grass on top, dirt below, stone underneath
                    chunk.setBlock(lx, height, lz, Blocks.GRASS.id());

                    int dirtLayers = config.dirtDepth;
                    // Fewer dirt layers at higher elevations
                    if (height > 75) {
                        dirtLayers = 2;
                    }

                    for (int d = 1; d <= dirtLayers && height - d > 0; d++) {
                        chunk.setBlock(lx, height - d, lz, Blocks.DIRT.id());
                    }
                    // Below dirt is already stone from BaseTerrainPass
                }
            }
        }
    }
}
