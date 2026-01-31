package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Fluid filling pass. Places water in all air blocks at or below sea level.
 * Runs after cave carving so cave openings below sea level get flooded.
 * Creates natural lakes and seas in low-lying terrain.
 */
public class FillFluidsPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                for (int y = WorldConstants.SEA_LEVEL; y >= 1; y--) {
                    int block = chunk.getBlock(lx, y, lz);

                    if (block == Blocks.AIR.id()) {
                        // Fill air at or below sea level with water
                        chunk.setBlock(lx, y, lz, Blocks.WATER.id());
                    } else if (block != Blocks.WATER.id()) {
                        // Hit solid ground, stop filling this column
                        break;
                    }
                }
            }
        }
    }
}
