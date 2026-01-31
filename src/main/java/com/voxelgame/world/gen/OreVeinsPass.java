package com.voxelgame.world.gen;

import com.voxelgame.math.RNG;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Ore placement pass. Scatters ore veins (coal, iron, gold, diamond)
 * through stone at configured depth ranges and vein sizes.
 * Uses chunk-seeded RNG for deterministic placement.
 */
public class OreVeinsPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();
        RNG rng = context.chunkRNG(chunk.getPos().x(), chunk.getPos().z());

        // Generate each ore type
        generateOre(chunk, rng, Blocks.COAL_ORE.id(),
                    config.coalMinY, config.coalMaxY, config.coalVeinSize, config.coalAttemptsPerChunk);
        generateOre(chunk, rng, Blocks.IRON_ORE.id(),
                    config.ironMinY, config.ironMaxY, config.ironVeinSize, config.ironAttemptsPerChunk);
        generateOre(chunk, rng, Blocks.GOLD_ORE.id(),
                    config.goldMinY, config.goldMaxY, config.goldVeinSize, config.goldAttemptsPerChunk);
        generateOre(chunk, rng, Blocks.DIAMOND_ORE.id(),
                    config.diamondMinY, config.diamondMaxY, config.diamondVeinSize, config.diamondAttemptsPerChunk);
    }

    private void generateOre(Chunk chunk, RNG rng, int oreId,
                             int minY, int maxY, int veinSize, int attempts) {
        for (int a = 0; a < attempts; a++) {
            // Random origin within chunk
            int ox = rng.nextInt(WorldConstants.CHUNK_SIZE);
            int oy = minY + rng.nextInt(Math.max(1, maxY - minY));
            int oz = rng.nextInt(WorldConstants.CHUNK_SIZE);

            // Place vein using a random walk from origin
            placeVein(chunk, rng, oreId, ox, oy, oz, veinSize);
        }
    }

    /**
     * Place a vein of ore blocks using a random walk from origin.
     * Only replaces STONE blocks.
     */
    private void placeVein(Chunk chunk, RNG rng, int oreId,
                           int startX, int startY, int startZ, int size) {
        int x = startX;
        int y = startY;
        int z = startZ;

        for (int i = 0; i < size; i++) {
            // Only place ore in stone
            if (x >= 0 && x < WorldConstants.CHUNK_SIZE &&
                y >= 1 && y < WorldConstants.WORLD_HEIGHT &&
                z >= 0 && z < WorldConstants.CHUNK_SIZE) {

                if (chunk.getBlock(x, y, z) == Blocks.STONE.id()) {
                    chunk.setBlock(x, y, z, oreId);
                }
            }

            // Random walk to next block in the vein
            int dir = rng.nextInt(6);
            switch (dir) {
                case 0 -> x++;
                case 1 -> x--;
                case 2 -> y++;
                case 3 -> y--;
                case 4 -> z++;
                case 5 -> z--;
            }
        }
    }
}
