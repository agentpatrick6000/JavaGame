package com.voxelgame.world.gen;

import com.voxelgame.math.RNG;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Flower placement pass. Places small patches of red and yellow flowers
 * on grass blocks. Flowers are sparse — roughly 1 patch per 50 chunks,
 * with 1-3 flowers per patch.
 */
public class FlowersPass implements GenPipeline.GenerationPass {

    /** Approximate chance per chunk of having a flower patch. */
    private static final double PATCH_CHANCE = 1.0 / 50.0;

    /** Maximum flowers per patch. */
    private static final int MAX_FLOWERS_PER_PATCH = 3;

    /** Patch spread radius (blocks). */
    private static final int PATCH_RADIUS = 3;

    @Override
    public void apply(Chunk chunk, GenContext context) {
        RNG rng = context.chunkRNG(chunk.getPos().x() * 31, chunk.getPos().z() * 59);

        // Check if this chunk gets a flower patch
        if (rng.nextDouble() > PATCH_CHANCE) return;

        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        // Pick patch center
        int centerX = 2 + rng.nextInt(WorldConstants.CHUNK_SIZE - 4);
        int centerZ = 2 + rng.nextInt(WorldConstants.CHUNK_SIZE - 4);

        // Choose flower type for this patch (or mix)
        boolean redPatch = rng.nextBoolean();

        int flowerCount = 1 + rng.nextInt(MAX_FLOWERS_PER_PATCH);

        for (int i = 0; i < flowerCount; i++) {
            int lx = centerX + rng.nextInt(PATCH_RADIUS * 2 + 1) - PATCH_RADIUS;
            int lz = centerZ + rng.nextInt(PATCH_RADIUS * 2 + 1) - PATCH_RADIUS;

            // Clamp to chunk bounds
            lx = Math.max(0, Math.min(WorldConstants.CHUNK_SIZE - 1, lx));
            lz = Math.max(0, Math.min(WorldConstants.CHUNK_SIZE - 1, lz));

            // Find actual grass by scanning chunk column
            int height = -1;
            for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                if (chunk.getBlock(lx, y, lz) == Blocks.GRASS.id()) {
                    height = y;
                    break;
                }
            }
            if (height < 0 || height <= WorldConstants.SEA_LEVEL) continue;

            // Must have air above
            int above = height + 1;
            if (above >= WorldConstants.WORLD_HEIGHT) continue;
            if (chunk.getBlock(lx, above, lz) != Blocks.AIR.id()) continue;

            // Place flower
            int flowerId = redPatch ? Blocks.RED_FLOWER.id() : Blocks.YELLOW_FLOWER.id();
            // Occasionally mix in the other type
            if (rng.nextInt(4) == 0) {
                flowerId = redPatch ? Blocks.YELLOW_FLOWER.id() : Blocks.RED_FLOWER.id();
            }
            chunk.setBlock(lx, above, lz, flowerId);
        }
    }
}
