package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import com.voxelgame.sim.Player;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Heads-up display. Renders a crosshair at screen center
 * and a hotbar at the bottom center showing 9 block slots.
 * Uses a simple ui shader (vec2 position → uProjection, flat uColor).
 */
public class Hud {

    private static final float CROSSHAIR_SIZE = 12.0f;
    private static final float CROSSHAIR_THICKNESS = 2.0f;

    // Hotbar dimensions (in pixels)
    private static final float SLOT_SIZE = 40.0f;
    private static final float SLOT_GAP = 4.0f;
    private static final float HOTBAR_MARGIN_BOTTOM = 10.0f;
    private static final float PREVIEW_SIZE = 28.0f;
    private static final float BORDER = 2.0f;
    private static final float SELECTED_BORDER = 3.0f;

    /**
     * Approximate flat colors for each block ID used in the hotbar preview.
     * Index = block ID. {R, G, B, A} in 0..1 range.
     */
    private static final float[][] BLOCK_COLORS = {
        {0.0f, 0.0f, 0.0f, 0.0f},       // 0 AIR
        {0.47f, 0.47f, 0.47f, 1.0f},     // 1 STONE
        {0.39f, 0.39f, 0.39f, 1.0f},     // 2 COBBLESTONE
        {0.53f, 0.38f, 0.26f, 1.0f},     // 3 DIRT
        {0.30f, 0.60f, 0.00f, 1.0f},     // 4 GRASS
        {0.84f, 0.81f, 0.60f, 1.0f},     // 5 SAND
        {0.51f, 0.49f, 0.49f, 1.0f},     // 6 GRAVEL
        {0.39f, 0.27f, 0.16f, 1.0f},     // 7 LOG
        {0.20f, 0.51f, 0.04f, 0.9f},     // 8 LEAVES
        {0.12f, 0.31f, 0.78f, 0.6f},     // 9 WATER
        {0.35f, 0.35f, 0.35f, 1.0f},     // 10 COAL_ORE
        {0.55f, 0.45f, 0.35f, 1.0f},     // 11 IRON_ORE
        {0.75f, 0.65f, 0.20f, 1.0f},     // 12 GOLD_ORE
        {0.39f, 0.86f, 1.00f, 1.0f},     // 13 DIAMOND_ORE
        {0.16f, 0.16f, 0.16f, 1.0f},     // 14 BEDROCK
    };

    private Shader uiShader;
    private int crosshairVao, crosshairVbo;
    private int quadVao, quadVbo;

    // Set each frame so helper methods can reference them
    private int sw, sh;

    public void init() {
        uiShader = new Shader("shaders/ui.vert", "shaders/ui.frag");
        buildCrosshairVAO();
        buildQuadVAO();
    }

    /* ---- VAO creation ---- */

    private void buildCrosshairVAO() {
        float s = CROSSHAIR_SIZE, t = CROSSHAIR_THICKNESS / 2.0f;
        float[] v = {
            -s,-t,  s,-t,  s, t,   -s,-t,  s, t, -s, t,   // horizontal
            -t,-s,  t,-s,  t, s,   -t,-s,  t, s, -t, s,   // vertical
        };
        crosshairVao = glGenVertexArrays();
        crosshairVbo = glGenBuffers();
        glBindVertexArray(crosshairVao);
        glBindBuffer(GL_ARRAY_BUFFER, crosshairVbo);
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(v.length);
            fb.put(v).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    /** A unit quad (0,0)-(1,1), 6 verts. */
    private void buildQuadVAO() {
        float[] v = { 0,0, 1,0, 1,1,  0,0, 1,1, 0,1 };
        quadVao = glGenVertexArrays();
        quadVbo = glGenBuffers();
        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(v.length);
            fb.put(v).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    /* ---- Public render entry point ---- */

    public void render(int screenW, int screenH, Player player) {
        this.sw = screenW;
        this.sh = screenH;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        uiShader.bind();

        renderCrosshair();
        if (player != null) renderHotbar(player);

        uiShader.unbind();
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    /** Backward-compatible overload (crosshair only). */
    public void render(int screenW, int screenH) {
        render(screenW, screenH, null);
    }

    /* ---- Crosshair ---- */

    private void renderCrosshair() {
        // Centered ortho so the +‑shape geometry at (0,0) ends up at screen center.
        float cx = sw / 2.0f, cy = sh / 2.0f;
        setProjection(new Matrix4f().ortho(-cx, sw - cx, -cy, sh - cy, -1, 1));
        uiShader.setVec4("uColor", 1f, 1f, 1f, 0.85f);
        glBindVertexArray(crosshairVao);
        glDrawArrays(GL_TRIANGLES, 0, 12);
    }

    /* ---- Hotbar ---- */

    private void renderHotbar(Player player) {
        glBindVertexArray(quadVao);

        float totalW = Player.HOTBAR_SIZE * SLOT_SIZE + (Player.HOTBAR_SIZE - 1) * SLOT_GAP;
        float x0 = (sw - totalW) / 2.0f;
        float y0 = HOTBAR_MARGIN_BOTTOM;

        for (int i = 0; i < Player.HOTBAR_SIZE; i++) {
            float sx = x0 + i * (SLOT_SIZE + SLOT_GAP);
            boolean sel = (i == player.getSelectedSlot());

            // Slot background
            fillRect(sx, y0, SLOT_SIZE, SLOT_SIZE, 0.1f, 0.1f, 0.1f, 0.75f);

            // Block preview
            int bid = player.getHotbarBlock(i);
            if (bid > 0 && bid < BLOCK_COLORS.length) {
                float[] c = BLOCK_COLORS[bid];
                float off = (SLOT_SIZE - PREVIEW_SIZE) / 2f;
                fillRect(sx + off, y0 + off, PREVIEW_SIZE, PREVIEW_SIZE, c[0], c[1], c[2], c[3]);
            }

            // Border
            if (sel) {
                strokeRect(sx, y0, SLOT_SIZE, SLOT_SIZE, SELECTED_BORDER, 1f, 1f, 1f, 1f);
            } else {
                strokeRect(sx, y0, SLOT_SIZE, SLOT_SIZE, BORDER, 0.5f, 0.5f, 0.5f, 0.6f);
            }
        }
    }

    /* ---- Drawing helpers (unit-quad based) ---- */

    /**
     * Draw a filled axis-aligned rectangle at pixel position (x,y) with size (w,h).
     * Works by computing an ortho projection that maps the unit quad (0,0)-(1,1)
     * directly to the desired pixel rectangle on screen.
     *
     * Derivation: for ortho(L,R,B,T), vertex v maps to NDC = 2(v-L)/(R-L)-1.
     *   We want v=0→NDC of pixel x, v=1→NDC of pixel x+w (with screen ortho(0,sw,0,sh)).
     *   Solving gives L = -x/w, R = (sw-x)/w  and similarly for y.
     */
    private void fillRect(float x, float y, float w, float h,
                           float r, float g, float b, float a) {
        setProjection(new Matrix4f().ortho(
            -x / w, (sw - x) / w,
            -y / h, (sh - y) / h,
            -1, 1));
        uiShader.setVec4("uColor", r, g, b, a);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    /** Stroke a rectangle border (4 thin filled rects). */
    private void strokeRect(float x, float y, float w, float h, float bw,
                             float r, float g, float b, float a) {
        fillRect(x, y + h - bw, w, bw, r, g, b, a);          // top
        fillRect(x, y, w, bw, r, g, b, a);                    // bottom
        fillRect(x, y, bw, h, r, g, b, a);                    // left
        fillRect(x + w - bw, y, bw, h, r, g, b, a);           // right
    }

    /** Upload a projection matrix to the ui shader. */
    private void setProjection(Matrix4f proj) {
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(16);
            proj.get(fb);
            glUniformMatrix4fv(
                glGetUniformLocation(uiShader.getProgramId(), "uProjection"),
                false, fb);
        }
    }

    /* ---- Cleanup ---- */

    public void cleanup() {
        if (crosshairVbo != 0) glDeleteBuffers(crosshairVbo);
        if (crosshairVao != 0) glDeleteVertexArrays(crosshairVao);
        if (quadVbo != 0) glDeleteBuffers(quadVbo);
        if (quadVao != 0) glDeleteVertexArrays(quadVao);
        if (uiShader != null) uiShader.cleanup();
    }
}
