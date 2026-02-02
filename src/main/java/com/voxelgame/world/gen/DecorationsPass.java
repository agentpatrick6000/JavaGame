package com.voxelgame.world.gen;

import com.voxelgame.math.RNG;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Decoration pass: flowers, tall grass, mushrooms, sugar cane.
 */
public class DecorationsPass implements GenPipeline.GenerationPass {

    private static final double FLOWER_CHANCE = 0.02;
    private static final double TALL_GRASS_CHANCE = 0.15;
    private static final double MUSHROOM_CHANCE = 0.008;
    private static final double SUGAR_CANE_CHANCE = 0.06;

    @Override
    public void apply(Chunk chunk, GenContext context) {
        RNG rng = context.chunkRNG(chunk.getPos().x() * 31, chunk.getPos().z() * 47);
        int cx = chunk.getPos().worldX();
        int cz = chunk.getPos().worldZ();

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                int wx = cx + lx, wz = cz + lz;
                int h = context.getTerrainHeight(wx, wz);
                if (h + 1 >= WorldConstants.WORLD_HEIGHT) continue;

                int ground = chunk.getBlock(lx, h, lz);
                int above = chunk.getBlock(lx, h + 1, lz);

                // Flowers & tall grass on flat grass
                if (h > WorldConstants.SEA_LEVEL && ground == Blocks.GRASS.id() && above == Blocks.AIR.id()) {
                    int maxSlope = Math.max(
                        Math.max(Math.abs(h - context.getTerrainHeight(wx, wz-1)),
                                 Math.abs(h - context.getTerrainHeight(wx, wz+1))),
                        Math.max(Math.abs(h - context.getTerrainHeight(wx+1, wz)),
                                 Math.abs(h - context.getTerrainHeight(wx-1, wz))));
                    if (maxSlope <= 1) {
                        double roll = rng.nextDouble();
                        if (roll < FLOWER_CHANCE) {
                            chunk.setBlock(lx, h+1, lz, rng.nextDouble() < 0.6 ?
                                Blocks.RED_FLOWER.id() : Blocks.YELLOW_FLOWER.id());
                        } else if (roll < FLOWER_CHANCE + TALL_GRASS_CHANCE) {
                            chunk.setBlock(lx, h+1, lz, Blocks.TALL_GRASS.id());
                        }
                    }
                }

                // Mushrooms in shaded areas
                if ((ground == Blocks.GRASS.id() || ground == Blocks.DIRT.id())
                        && above == Blocks.AIR.id() && h > WorldConstants.SEA_LEVEL) {
                    boolean shaded = false;
                    for (int y = h+2; y < Math.min(h+8, WorldConstants.WORLD_HEIGHT); y++) {
                        int b = chunk.getBlock(lx, y, lz);
                        if (b == Blocks.LEAVES.id() || b == Blocks.LOG.id()) { shaded = true; break; }
                    }
                    if (shaded && rng.nextDouble() < MUSHROOM_CHANCE) {
                        chunk.setBlock(lx, h+1, lz, rng.nextDouble() < 0.5 ?
                            Blocks.RED_MUSHROOM.id() : Blocks.BROWN_MUSHROOM.id());
                    }
                }

                // Sugar cane near water
                if ((ground == Blocks.SAND.id() || ground == Blocks.DIRT.id() || ground == Blocks.GRASS.id())
                        && above == Blocks.AIR.id() && h >= WorldConstants.SEA_LEVEL) {
                    boolean nearWater = false;
                    int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
                    for (int[] d : dirs) {
                        int nx = lx+d[0], nz = lz+d[1];
                        if (nx >= 0 && nx < WorldConstants.CHUNK_SIZE && nz >= 0 && nz < WorldConstants.CHUNK_SIZE
                                && Blocks.isWater(chunk.getBlock(nx, h, nz))) {
                            nearWater = true; break;
                        }
                    }
                    if (nearWater && rng.nextDouble() < SUGAR_CANE_CHANCE) {
                        int caneH = 1 + rng.nextInt(3);
                        for (int dy = 1; dy <= caneH; dy++) {
                            int cy = h + dy;
                            if (cy >= WorldConstants.WORLD_HEIGHT || chunk.getBlock(lx, cy, lz) != Blocks.AIR.id()) break;
                            chunk.setBlock(lx, cy, lz, Blocks.SUGAR_CANE.id());
                        }
                    }
                }
            }
        }
    }
}
