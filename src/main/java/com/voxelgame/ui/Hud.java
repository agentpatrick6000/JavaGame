package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Heads-up display. Renders a crosshair (+ shape) at screen center.
 * Uses a simple line shader with an orthographic projection.
 */
public class Hud {

    private static final float CROSSHAIR_SIZE = 12.0f;  // pixels
    private static final float CROSSHAIR_THICKNESS = 2.0f;

    private Shader uiShader;
    private int crosshairVao;
    private int crosshairVbo;

    public void init() {
        uiShader = new Shader("shaders/ui.vert", "shaders/ui.frag");
        createCrosshairMesh();
    }

    /**
     * Create crosshair geometry: two perpendicular lines forming a + shape.
     * Vertices are in pixel coordinates, centered at (0, 0) — will be offset
     * to screen center via the orthographic projection origin.
     */
    private void createCrosshairMesh() {
        float s = CROSSHAIR_SIZE;
        float t = CROSSHAIR_THICKNESS / 2.0f;

        // Two quads forming a +, each as 2 triangles (6 verts × 2 floats)
        float[] verts = {
            // Horizontal bar
            -s, -t,   s, -t,   s,  t,
            -s, -t,   s,  t,  -s,  t,
            // Vertical bar
            -t, -s,   t, -s,   t,  s,
            -t, -s,   t,  s,  -t,  s,
        };

        crosshairVao = glGenVertexArrays();
        crosshairVbo = glGenBuffers();

        glBindVertexArray(crosshairVao);
        glBindBuffer(GL_ARRAY_BUFFER, crosshairVbo);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(verts.length);
            buf.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        }

        // aPos (location 0): vec2
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
    }

    /**
     * Render the crosshair at screen center.
     */
    public void render(int screenW, int screenH) {
        glDisable(GL_DEPTH_TEST);

        uiShader.bind();

        // Orthographic projection centered on screen
        float cx = screenW / 2.0f;
        float cy = screenH / 2.0f;
        Matrix4f ortho = new Matrix4f().ortho(
            -cx, screenW - cx,
            -cy, screenH - cy,
            -1, 1
        );

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            ortho.get(fb);
            glUniformMatrix4fv(
                glGetUniformLocation(uiShader.getProgramId(), "uProjection"),
                false, fb
            );
        }

        // White with slight transparency
        uiShader.setVec4("uColor", 1.0f, 1.0f, 1.0f, 0.85f);

        glBindVertexArray(crosshairVao);
        glDrawArrays(GL_TRIANGLES, 0, 12); // 2 quads × 6 verts

        glBindVertexArray(0);
        uiShader.unbind();

        glEnable(GL_DEPTH_TEST);
    }

    public void cleanup() {
        if (crosshairVbo != 0) glDeleteBuffers(crosshairVbo);
        if (crosshairVao != 0) glDeleteVertexArrays(crosshairVao);
        if (uiShader != null) uiShader.cleanup();
    }
}
