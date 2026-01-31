package com.voxelgame.world.gen;

import com.voxelgame.world.Chunk;

/**
 * Final decoration pass. Places small features like flowers, tall grass,
 * sugarcane, and other surface details.
 * Currently a no-op — to be expanded in future phases.
 */
public class DecorationsPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        // No decorations yet — will be implemented in a future phase
    }
}
