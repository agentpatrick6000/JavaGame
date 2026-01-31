package com.voxelgame.render;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders a wireframe cube outline around the block the player is looking at.
 * Uses GL_LINES with a dedicated line shader.
 */
public class BlockHighlight {

    /** Slight expansion so the highlight doesn't z-fight with block faces. */
    private static final float E = 0.002f;

    private Shader lineShader;
    private int vao, vbo;

    public void init() {
        lineShader = new Shader("shaders/line.vert", "shaders/line.frag");
        buildCubeWireframe();
    }

    /**
     * Build a unit cube wireframe (0,0,0)-(1,1,1) with 12 edges = 24 verts.
     * Slightly expanded by E on each side to avoid z-fighting.
     */
    private void buildCubeWireframe() {
        float lo = -E;
        float hi = 1.0f + E;

        // 12 edges, 2 vertices each, 3 floats per vertex = 72 floats
        float[] verts = {
            // Bottom face edges
            lo,lo,lo,  hi,lo,lo,
            hi,lo,lo,  hi,lo,hi,
            hi,lo,hi,  lo,lo,hi,
            lo,lo,hi,  lo,lo,lo,
            // Top face edges
            lo,hi,lo,  hi,hi,lo,
            hi,hi,lo,  hi,hi,hi,
            hi,hi,hi,  lo,hi,hi,
            lo,hi,hi,  lo,hi,lo,
            // Vertical edges
            lo,lo,lo,  lo,hi,lo,
            hi,lo,lo,  hi,hi,lo,
            hi,lo,hi,  hi,hi,hi,
            lo,lo,hi,  lo,hi,hi,
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }

        // aPos (location 0): vec3
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    /**
     * Render a wireframe highlight around the block at world position (bx, by, bz).
     *
     * @param camera     active camera (for view + projection matrices)
     * @param windowW    viewport width
     * @param windowH    viewport height
     * @param bx         block world X
     * @param by         block world Y
     * @param bz         block world Z
     */
    public void render(Camera camera, int windowW, int windowH, int bx, int by, int bz) {
        Matrix4f projection = camera.getProjectionMatrix(windowW, windowH);
        // Bake the block-position translation into the view matrix
        Matrix4f view = new Matrix4f(camera.getViewMatrix()).translate(bx, by, bz);

        lineShader.bind();
        lineShader.setMat4("uProjection", projection);
        lineShader.setMat4("uView", view);
        lineShader.setVec4("uColor", 0.0f, 0.0f, 0.0f, 0.6f);

        glLineWidth(2.0f);
        glDisable(GL_DEPTH_TEST);   // render on top of geometry
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindVertexArray(vao);
        glDrawArrays(GL_LINES, 0, 24);
        glBindVertexArray(0);

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        lineShader.unbind();
    }

    public void cleanup() {
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (lineShader != null) lineShader.cleanup();
    }
}
