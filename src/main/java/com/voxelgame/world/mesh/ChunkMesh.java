package com.voxelgame.world.mesh;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * GPU-side mesh data for a chunk. Holds VAO/VBO/EBO handles,
 * vertex count, and manages upload/disposal of vertex data.
 * 
 * Phase 2 vertex format: [x, y, z, u, v, skyVisibility, blockLight, horizonWeight]
 * - 8 floats per vertex
 * - horizonWeight: 0-1 how much horizon vs zenith is visible
 */
public class ChunkMesh {

    /** Number of floats per vertex in the Phase 2 format. */
    public static final int VERTEX_SIZE = 8;
    
    /** Number of floats per vertex in the legacy format (Phase 1). */
    public static final int LEGACY_VERTEX_SIZE = 7;

    private int vao;
    private int vbo;
    private int ebo;
    private int indexCount;
    private boolean uploaded = false;

    /**
     * Upload mesh data to GPU with explicit index array.
     * Vertex format: [x, y, z, u, v, skyVisibility, blockLight, horizonWeight] per vertex.
     * 
     * skyVisibility: 0-1 sky visibility (shader computes actual sky RGB)
     * blockLight: 0-1 block light level (shader applies warm color)
     * horizonWeight: 0-1 how much horizon vs zenith is visible
     */
    public void upload(float[] vertices, int[] indices) {
        if (indices.length == 0) {
            indexCount = 0;
            return;
        }

        if (!uploaded) {
            vao = glGenVertexArrays();
            vbo = glGenBuffers();
            ebo = glGenBuffers();
        }

        glBindVertexArray(vao);

        // Upload vertex data
        FloatBuffer vertBuf = MemoryUtil.memAllocFloat(vertices.length);
        vertBuf.put(vertices).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertBuf, GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(vertBuf);

        // Upload index data
        indexCount = indices.length;
        IntBuffer idxBuf = MemoryUtil.memAllocInt(indexCount);
        idxBuf.put(indices).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idxBuf, GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(idxBuf);

        // Detect vertex format based on array size
        int vertexCount = indices.length > 0 ? 
            vertices.length / VERTEX_SIZE : 0;
        int stride;
        
        // Check if this is the new 8-float format or legacy 7-float format
        if (vertices.length > 0 && vertexCount > 0 && 
            vertices.length == vertexCount * VERTEX_SIZE) {
            stride = VERTEX_SIZE * Float.BYTES;
            setupVertexAttributes8(stride);
        } else {
            // Legacy 7-float format (backward compatibility)
            stride = LEGACY_VERTEX_SIZE * Float.BYTES;
            setupVertexAttributes7(stride);
        }

        glBindVertexArray(0);
        uploaded = true;
    }

    /**
     * Setup vertex attributes for the new 8-float format.
     * [x, y, z, u, v, skyVisibility, blockLight, horizonWeight]
     */
    private void setupVertexAttributes8(int stride) {
        // Position (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // TexCoord (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // SkyVisibility (location 2) — 0-1 sky visibility for shader
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);

        // BlockLight (location 3) — 0-1 block light level
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);

        // HorizonWeight (location 4) — 0-1 horizon vs zenith weight
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
        glEnableVertexAttribArray(4);
    }

    /**
     * Setup vertex attributes for the legacy 7-float format.
     * [x, y, z, u, v, skyVisibility, blockLight]
     * Sets horizonWeight to default 0.3 (balanced zenith/horizon).
     */
    private void setupVertexAttributes7(int stride) {
        // Position (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // TexCoord (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // SkyVisibility (location 2) — 0-1 sky visibility for shader
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);

        // BlockLight (location 3) — 0-1 block light level
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);

        // HorizonWeight (location 4) — default value for legacy meshes
        // Use glVertexAttrib to set a constant default value
        glDisableVertexAttribArray(4);
        glVertexAttrib1f(4, 0.3f); // Balanced zenith/horizon by default
    }

    /**
     * Upload with automatic vertex format detection.
     * Uses 8-float format if vertexSize matches, otherwise falls back to 7-float.
     * 
     * @param vertices Vertex array
     * @param indices Index array
     * @param vertexSize Number of floats per vertex (7 or 8)
     */
    public void upload(float[] vertices, int[] indices, int vertexSize) {
        if (indices.length == 0) {
            indexCount = 0;
            return;
        }

        if (!uploaded) {
            vao = glGenVertexArrays();
            vbo = glGenBuffers();
            ebo = glGenBuffers();
        }

        glBindVertexArray(vao);

        // Upload vertex data
        FloatBuffer vertBuf = MemoryUtil.memAllocFloat(vertices.length);
        vertBuf.put(vertices).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertBuf, GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(vertBuf);

        // Upload index data
        indexCount = indices.length;
        IntBuffer idxBuf = MemoryUtil.memAllocInt(indexCount);
        idxBuf.put(indices).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idxBuf, GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(idxBuf);

        int stride = vertexSize * Float.BYTES;
        if (vertexSize == VERTEX_SIZE) {
            setupVertexAttributes8(stride);
        } else {
            setupVertexAttributes7(stride);
        }

        glBindVertexArray(0);
        uploaded = true;
    }

    /**
     * Legacy upload for quad-count based meshes (auto-generates indices).
     * Vertex format: [x, y, z, u, v, skyVisibility, blockLight] per vertex.
     * Quads are turned into triangles via index buffer.
     */
    public void upload(float[] vertices, int quadCount) {
        if (quadCount == 0) {
            indexCount = 0;
            return;
        }

        int[] indices = new int[quadCount * 6];
        for (int i = 0; i < quadCount; i++) {
            int base = i * 4;
            indices[i * 6] = base;
            indices[i * 6 + 1] = base + 1;
            indices[i * 6 + 2] = base + 2;
            indices[i * 6 + 3] = base;
            indices[i * 6 + 4] = base + 2;
            indices[i * 6 + 5] = base + 3;
        }
        upload(vertices, indices);
    }

    public void draw() {
        if (!uploaded || indexCount == 0) return;
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void dispose() {
        if (uploaded) {
            glDeleteVertexArrays(vao);
            glDeleteBuffers(vbo);
            glDeleteBuffers(ebo);
            uploaded = false;
            indexCount = 0;
        }
    }

    public boolean isEmpty() { return indexCount == 0; }
    public boolean isUploaded() { return uploaded; }
}
