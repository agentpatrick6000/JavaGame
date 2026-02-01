package com.voxelgame.world.gen;

import com.voxelgame.math.RNG;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Decoration pass. Places flowers, tall grass, and other small surface features.
 * Red and yellow flowers spawn rarely on grass blocks above sea level.
 */
public class DecorationsPass implements GenPipeline.GenerationPass {

    // ~1 flower per 50 chunks on average
    private static final double FLOWER_CHANCE = 0.02; // per grass block

    @Override
    public void apply(Chunk chunk, GenContext context) {
        RNG rng = context.chunkRNG(chunk.getPos().x() * 31, chunk.getPos().z() * 47);
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                int worldX = chunkWorldX + lx;
                int worldZ = chunkWorldZ + lz;

                int height = context.getTerrainHeight(worldX, worldZ);

                // Only place flowers above sea level on grass
                if (height <= WorldConstants.SEA_LEVEL) continue;
                if (height + 1 >= WorldConstants.WORLD_HEIGHT) continue;
                if (chunk.getBlock(lx, height, lz) != Blocks.GRASS.id()) continue;
                if (chunk.getBlock(lx, height + 1, lz) != Blocks.AIR.id()) continue;

                // Check slope - flowers prefer flat terrain
                int hN = context.getTerrainHeight(worldX, worldZ - 1);
                int hS = context.getTerrainHeight(worldX, worldZ + 1);
                int hE = context.getTerrainHeight(worldX + 1, worldZ);
                int hW = context.getTerrainHeight(worldX - 1, worldZ);
                int maxSlope = Math.max(
                    Math.max(Math.abs(height - hN), Math.abs(height - hS)),
                    Math.max(Math.abs(height - hE), Math.abs(height - hW))
                );
                if (maxSlope > 1) continue;

                // Random chance for flower
                if (rng.nextDouble() > FLOWER_CHANCE) continue;

                // Pick flower type (60% red, 40% yellow)
                int flowerId;
                if (rng.nextDouble() < 0.6) {
                    flowerId = Blocks.RED_FLOWER.id();
                } else {
                    flowerId = Blocks.YELLOW_FLOWER.id();
                }

                chunk.setBlock(lx, height + 1, lz, flowerId);
            }
        }
    }
}
