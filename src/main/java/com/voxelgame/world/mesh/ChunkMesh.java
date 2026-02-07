package com.voxelgame.world.mesh;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * GPU-side mesh data for a chunk. Holds VAO/VBO/EBO handles,
 * vertex count, and manages upload/disposal of vertex data.
 * 
 * Phase 4 vertex format: [x, y, z, u, v, skyVisibility, blockLightR, blockLightG, blockLightB, horizonWeight, indirectR, indirectG, indirectB]
 * - 13 floats per vertex
 * - blockLightR/G/B: RGB block light from colored light sources (torch=orange, lava=red, etc.)
 * - horizonWeight: 0-1 how much horizon vs zenith is visible
 * - indirectRGB: one-bounce GI from irradiance probes
 */
public class ChunkMesh {

    /** Number of floats per vertex in the Phase 4 format (RGB block light). */
    public static final int VERTEX_SIZE = 13;
    
    /** Number of floats per vertex in the Phase 3 format (scalar block light). */
    public static final int PHASE3_VERTEX_SIZE = 11;
    
    /** Number of floats per vertex in the Phase 2 format. */
    public static final int PHASE2_VERTEX_SIZE = 8;
    
    /** Number of floats per vertex in the legacy format (Phase 1). */
    public static final int LEGACY_VERTEX_SIZE = 7;

    private int vao;
    private int vbo;
    private int ebo;
    private int indexCount;
    private boolean uploaded = false;

    /**
     * Upload mesh data to GPU with explicit index array.
     * Vertex format: [x, y, z, u, v, skyVisibility, blockLight, horizonWeight, indirectR, indirectG, indirectB] per vertex.
     * 
     * skyVisibility: 0-1 sky visibility (shader computes actual sky RGB)
     * blockLight: 0-1 block light level (shader applies warm color)
     * horizonWeight: 0-1 how much horizon vs zenith is visible
     * indirectRGB: one-bounce GI from irradiance probes
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
        // Check if this is Phase 4 (13-float), Phase 3 (11-float), Phase 2 (8-float), or legacy (7-float)
        int stride;
        int vertexCount13 = vertices.length / VERTEX_SIZE;
        int vertexCount11 = vertices.length / PHASE3_VERTEX_SIZE;
        int vertexCount8 = vertices.length / PHASE2_VERTEX_SIZE;
        int vertexCount7 = vertices.length / LEGACY_VERTEX_SIZE;
        
        if (vertices.length > 0 && vertices.length == vertexCount13 * VERTEX_SIZE) {
            stride = VERTEX_SIZE * Float.BYTES;
            setupVertexAttributes13(stride);
        } else if (vertices.length > 0 && vertices.length == vertexCount11 * PHASE3_VERTEX_SIZE) {
            stride = PHASE3_VERTEX_SIZE * Float.BYTES;
            setupVertexAttributes11(stride);
        } else if (vertices.length > 0 && vertices.length == vertexCount8 * PHASE2_VERTEX_SIZE) {
            stride = PHASE2_VERTEX_SIZE * Float.BYTES;
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
     * Setup vertex attributes for the Phase 4 13-float format (RGB block light).
     * [x, y, z, u, v, skyVisibility, blockLightR, blockLightG, blockLightB, horizonWeight, indirectR, indirectG, indirectB]
     */
    private void setupVertexAttributes13(int stride) {
        // Position (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // TexCoord (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // SkyVisibility (location 2) — 0-1 sky visibility for shader
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);

        // BlockLightR (location 3) — Phase 4: red channel of block light
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);

        // BlockLightG (location 4) — Phase 4: green channel of block light
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
        glEnableVertexAttribArray(4);

        // BlockLightB (location 5) — Phase 4: blue channel of block light
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 8L * Float.BYTES);
        glEnableVertexAttribArray(5);

        // HorizonWeight (location 6) — 0-1 horizon vs zenith weight
        glVertexAttribPointer(6, 1, GL_FLOAT, false, stride, 9L * Float.BYTES);
        glEnableVertexAttribArray(6);

        // IndirectR (location 7) — indirect lighting R from probes
        glVertexAttribPointer(7, 1, GL_FLOAT, false, stride, 10L * Float.BYTES);
        glEnableVertexAttribArray(7);

        // IndirectG (location 8) — indirect lighting G from probes
        glVertexAttribPointer(8, 1, GL_FLOAT, false, stride, 11L * Float.BYTES);
        glEnableVertexAttribArray(8);

        // IndirectB (location 9) — indirect lighting B from probes
        glVertexAttribPointer(9, 1, GL_FLOAT, false, stride, 12L * Float.BYTES);
        glEnableVertexAttribArray(9);
    }

    /**
     * Setup vertex attributes for the Phase 3 11-float format (scalar block light).
     * [x, y, z, u, v, skyVisibility, blockLight, horizonWeight, indirectR, indirectG, indirectB]
     * This is for backward compatibility with meshes that haven't been regenerated.
     */
    private void setupVertexAttributes11(int stride) {
        // Position (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // TexCoord (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // SkyVisibility (location 2) — 0-1 sky visibility for shader
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);

        // BlockLight scalar (location 3) — Legacy, use as R with warm G/B defaults
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);

        // BlockLightG (location 4) — Default to 0.9 * blockLight (warm color)
        glDisableVertexAttribArray(4);
        glVertexAttrib1f(4, 0.0f);  // Will be computed in shader

        // BlockLightB (location 5) — Default to 0.7 * blockLight (warm color)
        glDisableVertexAttribArray(5);
        glVertexAttrib1f(5, 0.0f);  // Will be computed in shader

        // HorizonWeight (location 6) — 0-1 horizon vs zenith weight
        glVertexAttribPointer(6, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
        glEnableVertexAttribArray(6);

        // IndirectR (location 7) — indirect lighting R from probes
        glVertexAttribPointer(7, 1, GL_FLOAT, false, stride, 8L * Float.BYTES);
        glEnableVertexAttribArray(7);

        // IndirectG (location 8) — indirect lighting G from probes
        glVertexAttribPointer(8, 1, GL_FLOAT, false, stride, 9L * Float.BYTES);
        glEnableVertexAttribArray(8);

        // IndirectB (location 9) — indirect lighting B from probes
        glVertexAttribPointer(9, 1, GL_FLOAT, false, stride, 10L * Float.BYTES);
        glEnableVertexAttribArray(9);
    }

    /**
     * Setup vertex attributes for the Phase 2 8-float format.
     * [x, y, z, u, v, skyVisibility, blockLight, horizonWeight]
     * Maps to Phase 4 layout with scalar blockLight converted to warm RGB.
     */
    private void setupVertexAttributes8(int stride) {
        // Position (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // TexCoord (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // SkyVisibility (location 2)
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);

        // BlockLightR (location 3) — scalar blockLight goes here, shader handles conversion
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);

        // BlockLightG (location 4) — 0, shader will apply warm color
        glDisableVertexAttribArray(4);
        glVertexAttrib1f(4, 0.0f);

        // BlockLightB (location 5) — 0, shader will apply warm color
        glDisableVertexAttribArray(5);
        glVertexAttrib1f(5, 0.0f);

        // HorizonWeight (location 6)
        glVertexAttribPointer(6, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
        glEnableVertexAttribArray(6);

        // IndirectRGB (locations 7-9) — default 0 for Phase 2 meshes
        glDisableVertexAttribArray(7);
        glDisableVertexAttribArray(8);
        glDisableVertexAttribArray(9);
        glVertexAttrib1f(7, 0.0f);
        glVertexAttrib1f(8, 0.0f);
        glVertexAttrib1f(9, 0.0f);
    }

    /**
     * Setup vertex attributes for the legacy 7-float format.
     * [x, y, z, u, v, skyVisibility, blockLight]
     * Maps to Phase 4 layout with defaults.
     */
    private void setupVertexAttributes7(int stride) {
        // Position (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // TexCoord (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // SkyVisibility (location 2)
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);

        // BlockLightR (location 3) — scalar blockLight
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);

        // BlockLightG (location 4) — 0
        glDisableVertexAttribArray(4);
        glVertexAttrib1f(4, 0.0f);

        // BlockLightB (location 5) — 0
        glDisableVertexAttribArray(5);
        glVertexAttrib1f(5, 0.0f);

        // HorizonWeight (location 6) — default 0.3
        glDisableVertexAttribArray(6);
        glVertexAttrib1f(6, 0.3f);

        // IndirectRGB (locations 7-9) — default 0
        glDisableVertexAttribArray(7);
        glDisableVertexAttribArray(8);
        glDisableVertexAttribArray(9);
        glVertexAttrib1f(7, 0.0f);
        glVertexAttrib1f(8, 0.0f);
        glVertexAttrib1f(9, 0.0f);
    }

    /**
     * Upload with automatic vertex format detection.
     * 
     * @param vertices Vertex array
     * @param indices Index array
     * @param vertexSize Number of floats per vertex (7, 8, or 11)
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
            setupVertexAttributes13(stride);
        } else if (vertexSize == PHASE3_VERTEX_SIZE) {
            setupVertexAttributes11(stride);
        } else if (vertexSize == PHASE2_VERTEX_SIZE) {
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

    /**
     * Draw mesh for shadow pass (depth only).
     * Same as draw() but semantically separate for clarity.
     * The shadow shader only uses position (location 0), so no texture binding needed.
     */
    public void renderDepthOnly() {
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
    public int getIndexCount() { return indexCount; }
}
