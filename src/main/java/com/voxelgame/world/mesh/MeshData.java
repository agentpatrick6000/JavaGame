package com.voxelgame.world.mesh;

/**
 * CPU-side mesh data (vertices + indices) before GPU upload.
 * Thread-safe: produced on background threads, consumed on GL thread.
 * 
 * Phase 2: Supports both 7-float (legacy) and 8-float (with horizonWeight) vertex formats.
 */
public record MeshData(float[] vertices, int[] indices, int vertexSize) {

    /** Shared empty mesh data instance. */
    public static final MeshData EMPTY = new MeshData(new float[0], new int[0], ChunkMesh.VERTEX_SIZE);

    /**
     * Constructor with default vertex size (8 floats, Phase 2 format).
     */
    public MeshData(float[] vertices, int[] indices) {
        this(vertices, indices, ChunkMesh.VERTEX_SIZE);
    }

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
        mesh.upload(vertices, indices, vertexSize);
        return mesh;
    }
}
