package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Manages farming mechanics: crop growth, farmland hydration/decay, sugar cane growth.
 */
public class FarmingManager {

    private final Random random = new Random();
    private static final int RANDOM_TICKS_PER_CHUNK = 3;
    private static final int WATER_RANGE = 4;
    private static final int MAX_SUGAR_CANE_HEIGHT = 3;

    public Set<ChunkPos> tick(World world, float playerX, float playerZ, int tickRadius) {
        Set<ChunkPos> dirty = new HashSet<>();
        int pcx = Math.floorDiv((int) playerX, WorldConstants.CHUNK_SIZE);
        int pcz = Math.floorDiv((int) playerZ, WorldConstants.CHUNK_SIZE);

        for (int cx = pcx - tickRadius; cx <= pcx + tickRadius; cx++) {
            for (int cz = pcz - tickRadius; cz <= pcz + tickRadius; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                Chunk chunk = world.getChunk(pos);
                if (chunk == null) continue;

                int wx0 = cx * WorldConstants.CHUNK_SIZE;
                int wz0 = cz * WorldConstants.CHUNK_SIZE;

                for (int i = 0; i < RANDOM_TICKS_PER_CHUNK; i++) {
                    int lx = random.nextInt(WorldConstants.CHUNK_SIZE);
                    int ly = random.nextInt(WorldConstants.WORLD_HEIGHT);
                    int lz = random.nextInt(WorldConstants.CHUNK_SIZE);
                    int blockId = chunk.getBlock(lx, ly, lz);
                    if (blockId == 0) continue;

                    boolean changed = false;
                    if (Blocks.isWheatCrop(blockId))
                        changed = tickWheat(world, wx0+lx, ly, wz0+lz, blockId);
                    else if (blockId == Blocks.FARMLAND.id())
                        changed = tickFarmland(world, wx0+lx, ly, wz0+lz);
                    else if (blockId == Blocks.SUGAR_CANE.id())
                        changed = tickSugarCane(world, wx0+lx, ly, wz0+lz);

                    if (changed) {
                        dirty.add(pos);
                        if (lx == 0) dirty.add(new ChunkPos(cx-1, cz));
                        if (lx == WorldConstants.CHUNK_SIZE-1) dirty.add(new ChunkPos(cx+1, cz));
                        if (lz == 0) dirty.add(new ChunkPos(cx, cz-1));
                        if (lz == WorldConstants.CHUNK_SIZE-1) dirty.add(new ChunkPos(cx, cz+1));
                    }
                }
            }
        }
        return dirty;
    }

    private boolean tickWheat(World world, int x, int y, int z, int blockId) {
        int stage = Blocks.getWheatStage(blockId);
        if (stage >= 7) return false;
        if (world.getBlock(x, y-1, z) != Blocks.FARMLAND.id()) return false;
        if (world.getSkyLight(x, y, z) < 9) return false;
        float chance = isNearWater(world, x, y-1, z) ? 0.35f : 0.12f;
        if (random.nextFloat() < chance) {
            world.setBlock(x, y, z, Blocks.wheatCropId(stage + 1));
            return true;
        }
        return false;
    }

    private boolean tickFarmland(World world, int x, int y, int z) {
        if (Blocks.isWheatCrop(world.getBlock(x, y+1, z))) return false;
        if (isNearWater(world, x, y, z)) return false;
        if (random.nextFloat() < 0.05f) {
            world.setBlock(x, y, z, Blocks.DIRT.id());
            return true;
        }
        return false;
    }

    private boolean tickSugarCane(World world, int x, int y, int z) {
        int height = 1;
        for (int dy = 1; dy <= MAX_SUGAR_CANE_HEIGHT; dy++) {
            if (world.getBlock(x, y-dy, z) == Blocks.SUGAR_CANE.id()) height++;
            else break;
        }
        if (height >= MAX_SUGAR_CANE_HEIGHT) return false;
        if (world.getBlock(x, y+1, z) != Blocks.AIR.id()) return false;
        if (random.nextFloat() < 0.15f) {
            world.setBlock(x, y+1, z, Blocks.SUGAR_CANE.id());
            return true;
        }
        return false;
    }

    public static boolean isNearWater(World world, int x, int y, int z) {
        for (int dx = -WATER_RANGE; dx <= WATER_RANGE; dx++)
            for (int dz = -WATER_RANGE; dz <= WATER_RANGE; dz++)
                for (int dy = 0; dy >= -1; dy--)
                    if (Blocks.isWater(world.getBlock(x+dx, y+dy, z+dz))) return true;
        return false;
    }
}
