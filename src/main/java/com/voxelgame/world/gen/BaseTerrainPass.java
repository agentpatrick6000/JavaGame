package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * First generation pass. Fills the chunk with stone/air based on the
 * heightmap computed from InfDev 611-style combined noise.
 * Stone below the terrain height, air above.
 */
public class BaseTerrainPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                int worldX = chunkWorldX + lx;
                int worldZ = chunkWorldZ + lz;

                int height = context.getTerrainHeight(worldX, worldZ);

                for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                    if (y <= height) {
                        chunk.setBlock(lx, y, lz, Blocks.STONE.id());
                    } else {
                        chunk.setBlock(lx, y, lz, Blocks.AIR.id());
                    }
                }
            }
        }
    }
}
