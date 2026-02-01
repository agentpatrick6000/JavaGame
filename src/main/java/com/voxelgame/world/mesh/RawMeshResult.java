package com.voxelgame.world.mesh;

/**
 * CPU-side mesh result with raw vertex/index data for both passes.
 * Used for background meshing — no GL calls involved.
 */
public record RawMeshResult(MeshData opaqueData, MeshData transparentData) {

    /** Convert to GPU-resident MeshResult (must call on GL thread). */
    public MeshResult upload() {
        ChunkMesh opaque = opaqueData != null ? opaqueData.toChunkMesh() : new ChunkMesh();
        ChunkMesh transparent = transparentData != null ? transparentData.toChunkMesh() : new ChunkMesh();
        return new MeshResult(opaque, transparent);
    }
}
