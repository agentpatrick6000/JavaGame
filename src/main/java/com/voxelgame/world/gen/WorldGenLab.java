package com.voxelgame.world.gen;

/**
 * Development tool for iterating on worldgen. Generates isolated test worlds,
 * dumps heightmaps, and allows toggling individual passes on/off.
 * Placeholder for future development tooling.
 */
public class WorldGenLab {

    private final GenPipeline pipeline;

    public WorldGenLab(long seed) {
        this.pipeline = GenPipeline.createDefault(seed);
    }

    public GenPipeline getPipeline() {
        return pipeline;
    }
}
