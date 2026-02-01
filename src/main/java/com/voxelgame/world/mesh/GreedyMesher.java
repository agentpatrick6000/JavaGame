package com.voxelgame.world.mesh;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldAccess;

/**
 * Greedy meshing algorithm. Placeholder — delegates to NaiveMesher for now.
 * Will be implemented in a later phase.
 */
public class GreedyMesher implements Mesher {

    private final Mesher fallback;

    public GreedyMesher(Mesher fallback) {
        this.fallback = fallback;
    }

    @Override
    public MeshResult meshAll(Chunk chunk, WorldAccess world) {
        return fallback.meshAll(chunk, world);
    }

    @Override
    public RawMeshResult meshAllRaw(Chunk chunk, WorldAccess world) {
        return fallback.meshAllRaw(chunk, world);
    }
}
