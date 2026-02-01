package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Surface painting pass. Replaces top stone layers with appropriate
 * surface blocks using InfDev 611-style rules:
 * - Sand where beach noise > threshold near water level
 * - Gravel on some underwater floors
 * - Grass/dirt for normal terrain
 * - Stone exposed on mountains
 * - Variable dirt depth from erosion noise
 * - Bedrock at y=0
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

                // Variable dirt depth from erosion noise (like Classic's strata)
                double erosionVal = context.getErosionNoise().eval2D(
                    worldX * 0.04, worldZ * 0.04);
                int dirtThickness = config.dirtDepth + (int)(erosionVal * 2.0);
                dirtThickness = Math.max(2, Math.min(dirtThickness, 6));

                // Beach noise — determines sand placement (Classic style)
                // Classic's octave-8 returns [-128, 128], checks > 8.
                // Our normalized noise returns [-1, 1], so threshold = 8/128 ≈ 0.06
                double beachVal = context.getBeachNoise().eval2D(
                    worldX * config.beachNoiseScale, worldZ * config.beachNoiseScale);
                boolean isSandyArea = beachVal > 0.06;

                // Gravel noise — for underwater gravel
                // Classic checks octave-8 > 12 → normalized threshold = 12/128 ≈ 0.09
                double gravelVal = context.getErosionNoise().eval2D(
                    worldX * 0.5 + 1000, worldZ * 0.5 + 1000);
                boolean isGravelArea = gravelVal > 0.09;

                // Determine surface type
                if (height >= config.mountainThreshold) {
                    // Mountain: exposed stone with thin gravel cap
                    chunk.setBlock(lx, height, lz, Blocks.GRAVEL.id());

                } else if (height < WorldConstants.SEA_LEVEL - 2) {
                    // Underwater floor
                    if (isGravelArea) {
                        chunk.setBlock(lx, height, lz, Blocks.GRAVEL.id());
                    } else {
                        chunk.setBlock(lx, height, lz, Blocks.SAND.id());
                    }
                    if (height - 1 > 0) {
                        chunk.setBlock(lx, height - 1, lz, Blocks.SAND.id());
                    }
                    if (height - 2 > 0) {
                        chunk.setBlock(lx, height - 2, lz, Blocks.SAND.id());
                    }

                } else if (height <= WorldConstants.SEA_LEVEL + 3 && isSandyArea) {
                    // Beach zone: sand near water where beach noise says so
                    for (int d = 0; d < config.beachDepth && height - d > 0; d++) {
                        chunk.setBlock(lx, height - d, lz, Blocks.SAND.id());
                    }

                } else if (height <= WorldConstants.SEA_LEVEL + 1) {
                    // Just above water but not sandy — still sand (narrow beach)
                    for (int d = 0; d < 3 && height - d > 0; d++) {
                        chunk.setBlock(lx, height - d, lz, Blocks.SAND.id());
                    }

                } else {
                    // Normal terrain above sea level: grass on top, dirt below
                    chunk.setBlock(lx, height, lz, Blocks.GRASS.id());

                    // Variable dirt depth (thinner at higher elevations)
                    int actualDirt = dirtThickness;
                    if (height > 85) {
                        actualDirt = Math.max(1, actualDirt - 2);
                    } else if (height > 75) {
                        actualDirt = Math.max(2, actualDirt - 1);
                    }

                    for (int d = 1; d <= actualDirt && height - d > 0; d++) {
                        chunk.setBlock(lx, height - d, lz, Blocks.DIRT.id());
                    }
                }
            }
        }
    }
}
