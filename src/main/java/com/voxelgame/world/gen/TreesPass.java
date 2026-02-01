package com.voxelgame.world.gen;

import com.voxelgame.math.RNG;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Tree placement pass. Places oak-style trees on valid grass blocks.
 * Uses density noise and chunk-seeded RNG.
 * Trees are kept away from chunk edges to avoid cross-chunk issues.
 */
public class TreesPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();
        RNG rng = context.chunkRNG(chunk.getPos().x() * 7, chunk.getPos().z() * 13);
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        int margin = config.treeEdgeMargin;

        for (int lx = margin; lx < WorldConstants.CHUNK_SIZE - margin; lx++) {
            for (int lz = margin; lz < WorldConstants.CHUNK_SIZE - margin; lz++) {
                int worldX = chunkWorldX + lx;
                int worldZ = chunkWorldZ + lz;

                // Use density noise to create forested and clear areas
                double density = context.getTreeDensityNoise().eval2D(
                    worldX * 0.01, worldZ * 0.01);
                // Remap from [-1,1] to [0, 1] — higher = more trees
                double treeProbability = (density + 1.0) * 0.5 * config.treeDensity * 3.0;

                if (rng.nextDouble() > treeProbability) continue;

                // Find the surface height
                int height = context.getTerrainHeight(worldX, worldZ);

                // Must be at least 3 blocks above sea level (no beach trees)
                if (height <= WorldConstants.SEA_LEVEL + 2) continue;

                // Check that the surface block is grass (not sand, stone, water, etc.)
                int surfBlock = chunk.getBlock(lx, height, lz);
                if (surfBlock != Blocks.GRASS.id()) continue;

                // Extra check: no sand/water neighbors (no trees on beach edges)
                boolean nearBeach = false;
                for (int dx = -1; dx <= 1 && !nearBeach; dx++) {
                    for (int dz2 = -1; dz2 <= 1 && !nearBeach; dz2++) {
                        int nh = context.getTerrainHeight(worldX + dx, worldZ + dz2);
                        if (nh <= WorldConstants.SEA_LEVEL + 1) nearBeach = true;
                    }
                }
                if (nearBeach) continue;

                // Check slope — look at neighboring terrain heights
                int hN = context.getTerrainHeight(worldX, worldZ - 1);
                int hS = context.getTerrainHeight(worldX, worldZ + 1);
                int hE = context.getTerrainHeight(worldX + 1, worldZ);
                int hW = context.getTerrainHeight(worldX - 1, worldZ);

                int maxSlope = Math.max(
                    Math.max(Math.abs(height - hN), Math.abs(height - hS)),
                    Math.max(Math.abs(height - hE), Math.abs(height - hW))
                );

                if (maxSlope > config.treeSlopeMax) continue;

                // Generate tree
                int trunkHeight = config.treeMinTrunk + rng.nextInt(
                    config.treeMaxTrunk - config.treeMinTrunk + 1);

                placeTree(chunk, lx, height + 1, lz, trunkHeight);
            }
        }
    }

    /**
     * Place a simple oak tree at the given local position.
     * Trunk of LOG blocks, topped with a LEAVES canopy.
     */
    private void placeTree(Chunk chunk, int x, int baseY, int z, int trunkHeight) {
        // Ensure tree fits in world height
        int topY = baseY + trunkHeight;
        if (topY + 3 >= WorldConstants.WORLD_HEIGHT) return;

        // Place trunk
        for (int y = baseY; y < topY; y++) {
            chunk.setBlock(x, y, z, Blocks.LOG.id());
        }

        // Place leaf canopy — 3 layers
        // Bottom layer: 5x5 with corners removed (largest)
        int leafBase = topY - 1; // leaves start overlapping top of trunk
        placeLeafLayer(chunk, x, leafBase, z, 2, true);
        // Middle layer: 5x5 with corners removed
        placeLeafLayer(chunk, x, leafBase + 1, z, 2, true);
        // Top layer: 3x3 cross
        placeLeafLayer(chunk, x, leafBase + 2, z, 1, false);
        // Tip: single block on top
        setLeaf(chunk, x, leafBase + 3, z);
    }

    /**
     * Place a horizontal layer of leaves centered at (cx, y, cz).
     * @param radius 1 = 3x3, 2 = 5x5
     * @param removeCorners if true, skip the 4 corners of the square
     */
    private void placeLeafLayer(Chunk chunk, int cx, int y, int cz,
                                int radius, boolean removeCorners) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Skip corners for more natural look
                if (removeCorners && Math.abs(dx) == radius && Math.abs(dz) == radius) {
                    continue;
                }

                int lx = cx + dx;
                int lz = cz + dz;

                setLeaf(chunk, lx, y, lz);
            }
        }
    }

    /** Set a leaf block if within chunk bounds and the position is air. */
    private void setLeaf(Chunk chunk, int lx, int y, int lz) {
        if (lx < 0 || lx >= WorldConstants.CHUNK_SIZE ||
            lz < 0 || lz >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT) {
            return;
        }

        // Only place leaves in air (don't overwrite trunk or solid blocks)
        if (chunk.getBlock(lx, y, lz) == Blocks.AIR.id()) {
            chunk.setBlock(lx, y, lz, Blocks.LEAVES.id());
        }
    }
}
