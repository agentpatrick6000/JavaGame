package com.voxelgame.world.mesh;

/**
 * CPU-side mesh data (vertices + indices) before GPU upload.
 * Thread-safe: produced on background threads, consumed on GL thread.
 */
public record MeshData(float[] vertices, int[] indices) {

    /** Check if this mesh data is empty (no geometry). */
    public boolean isEmpty() {
        return indices == null || indices.length == 0;
    }

    /** Upload this data to a ChunkMesh on the GL thread. */
    public ChunkMesh toChunkMesh() {
        if (isEmpty()) {
            ChunkMesh mesh = new ChunkMesh();
            // Return empty mesh (indexCount = 0)
            return mesh;
        }
        ChunkMesh mesh = new ChunkMesh();
        mesh.upload(vertices, indices);
        return mesh;
    }
}
