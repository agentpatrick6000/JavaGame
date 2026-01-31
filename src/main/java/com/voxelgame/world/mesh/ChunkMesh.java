package com.voxelgame.world.mesh;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * GPU-side mesh data for a chunk. Holds VAO/VBO/EBO handles,
 * vertex count, and manages upload/disposal of vertex data.
 */
public class ChunkMesh {

    private int vao;
    private int vbo;
    private int ebo;
    private int indexCount;
    private boolean uploaded = false;

    /**
     * Upload mesh data to GPU with explicit index array.
     * Vertex format: [x, y, z, u, v, light] per vertex.
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

        int stride = 6 * Float.BYTES; // x, y, z, u, v, light

        // Position (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // TexCoord (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Light (location 2)
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
        uploaded = true;
    }

    /**
     * Legacy upload for quad-count based meshes (auto-generates indices).
     * Vertex format: [x, y, z, u, v, light] per vertex.
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
