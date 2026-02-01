package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import com.voxelgame.sim.Inventory;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Full-screen inventory UI. Opened with E key.
 * Shows 4 rows of 9 slots:
 *   - Row 0 (bottom): hotbar (slots 0-8)
 *   - Rows 1-3: main inventory (slots 9-35)
 *
 * Players can click slots to swap items between them.
 */
public class InventoryScreen {

    private static final float SLOT_SIZE = 44.0f;
    private static final float SLOT_GAP = 4.0f;
    private static final float ROW_GAP = 8.0f;
    private static final float PREVIEW_SIZE = 30.0f;
    private static final float BORDER = 2.0f;
    private static final float SELECTED_BORDER = 3.0f;
    private static final float BG_PADDING = 16.0f;

    private static final float[][] BLOCK_COLORS = {
        {0.0f, 0.0f, 0.0f, 0.0f},
        {0.47f, 0.47f, 0.47f, 1.0f},
        {0.39f, 0.39f, 0.39f, 1.0f},
        {0.53f, 0.38f, 0.26f, 1.0f},
        {0.30f, 0.60f, 0.00f, 1.0f},
        {0.84f, 0.81f, 0.60f, 1.0f},
        {0.51f, 0.49f, 0.49f, 1.0f},
        {0.39f, 0.27f, 0.16f, 1.0f},
        {0.20f, 0.51f, 0.04f, 0.9f},
        {0.12f, 0.31f, 0.78f, 0.6f},
        {0.35f, 0.35f, 0.35f, 1.0f},
        {0.55f, 0.45f, 0.35f, 1.0f},
        {0.75f, 0.65f, 0.20f, 1.0f},
        {0.39f, 0.86f, 1.00f, 1.0f},
        {0.16f, 0.16f, 0.16f, 1.0f},
    };

    private boolean visible = false;
    private int heldSlot = -1;

    private Shader uiShader;
    private int quadVao, quadVbo;
    private BitmapFont font;

    private int sw, sh;

    public void init(BitmapFont font) {
        this.font = font;
        uiShader = new Shader("shaders/ui.vert", "shaders/ui.frag");

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

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { visible = v; heldSlot = -1; }
    public void toggle() { visible = !visible; heldSlot = -1; }
    public void close() { visible = false; heldSlot = -1; }

    // Alias for compat
    public boolean isOpen() { return visible; }

    /**
     * Handle a mouse click at screen position (mx, my) — GLFW coordinates (top-left origin).
     */
    public void handleClick(Inventory inventory, double mx, double my, int screenW, int screenH) {
        if (!visible) return;

        this.sw = screenW;
        this.sh = screenH;

        // Convert GLFW top-left origin to OpenGL bottom-left origin
        float clickX = (float) mx;
        float clickY = screenH - (float) my;

        int clickedSlot = getSlotAt(clickX, clickY);
        if (clickedSlot < 0) {
            heldSlot = -1;
            return;
        }

        if (heldSlot < 0) {
            Inventory.ItemStack stack = inventory.getSlot(clickedSlot);
            if (stack != null && !stack.isEmpty()) {
                heldSlot = clickedSlot;
            }
        } else {
            if (clickedSlot != heldSlot) {
                inventory.swapSlots(heldSlot, clickedSlot);
            }
            heldSlot = -1;
        }
    }

    private int getSlotAt(float mx, float my) {
        float gridW = 9 * SLOT_SIZE + 8 * SLOT_GAP;
        float gridH = 4 * SLOT_SIZE + 3 * SLOT_GAP + ROW_GAP;
        float x0 = (sw - gridW) / 2.0f;
        float y0 = (sh - gridH) / 2.0f;

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = x0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;

                if (row == 0) {
                    sy = y0;
                } else {
                    sy = y0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
                }

                if (mx >= sx && mx <= sx + SLOT_SIZE && my >= sy && my <= sy + SLOT_SIZE) {
                    if (row == 0) return col;
                    return 9 + (row - 1) * 9 + col;
                }
            }
        }
        return -1;
    }

    /**
     * Render the inventory screen.
     */
    public void render(int screenW, int screenH, Inventory inventory) {
        if (!visible) return;

        this.sw = screenW;
        this.sh = screenH;

        uiShader.bind();
        glBindVertexArray(quadVao);

        // Semi-transparent background
        fillRect(0, 0, sw, sh, 0.0f, 0.0f, 0.0f, 0.6f);

        // Grid
        float gridW = 9 * SLOT_SIZE + 8 * SLOT_GAP;
        float gridH = 4 * SLOT_SIZE + 3 * SLOT_GAP + ROW_GAP;
        float x0 = (sw - gridW) / 2.0f;
        float y0 = (sh - gridH) / 2.0f;

        // Background panel
        fillRect(x0 - BG_PADDING, y0 - BG_PADDING,
                 gridW + BG_PADDING * 2, gridH + BG_PADDING * 2,
                 0.15f, 0.15f, 0.15f, 0.9f);

        float titleX = sw / 2.0f - 40;
        float titleY = y0 + gridH + BG_PADDING + 4;

        // Render rows
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = x0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;

                if (row == 0) {
                    sy = y0;
                } else {
                    sy = y0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
                }

                int slot = (row == 0) ? col : (9 + (row - 1) * 9 + col);

                // Slot background
                float bgR = 0.2f, bgG = 0.2f, bgB = 0.2f;
                if (slot == heldSlot) {
                    bgR = 0.4f; bgG = 0.4f; bgB = 0.2f;
                }
                fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, bgR, bgG, bgB, 0.8f);

                // Block preview
                Inventory.ItemStack stack = inventory.getSlot(slot);
                if (stack != null && !stack.isEmpty()) {
                    int bid = stack.getBlockId();
                    if (bid > 0 && bid < BLOCK_COLORS.length) {
                        float[] c = BLOCK_COLORS[bid];
                        float off = (SLOT_SIZE - PREVIEW_SIZE) / 2f;
                        fillRect(sx + off, sy + off, PREVIEW_SIZE, PREVIEW_SIZE,
                                 c[0], c[1], c[2], c[3]);
                    }
                }

                // Border
                if (slot == heldSlot) {
                    strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, SELECTED_BORDER, 1f, 1f, 0.3f, 1f);
                } else {
                    strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, BORDER, 0.4f, 0.4f, 0.4f, 0.7f);
                }
            }
        }

        // Render text (title + item counts)
        uiShader.unbind();
        if (font != null) {
            font.drawText("Inventory", titleX, titleY, 2.0f, sw, sh, 0.9f, 0.9f, 0.9f, 1.0f);

            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    float sx = x0 + col * (SLOT_SIZE + SLOT_GAP);
                    float sy;
                    if (row == 0) {
                        sy = y0;
                    } else {
                        sy = y0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
                    }

                    int slot = (row == 0) ? col : (9 + (row - 1) * 9 + col);
                    Inventory.ItemStack stack = inventory.getSlot(slot);
                    if (stack != null && stack.getCount() > 1) {
                        String countStr = String.valueOf(stack.getCount());
                        float textX = sx + SLOT_SIZE - 8 * countStr.length() - 2;
                        float textY = sy + 2;
                        font.drawText(countStr, textX + 1, textY + 1, 1.5f, sw, sh,
                                     0.0f, 0.0f, 0.0f, 0.8f);
                        font.drawText(countStr, textX, textY, 1.5f, sw, sh,
                                     1.0f, 1.0f, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    /* ---- Drawing helpers ---- */

    private void fillRect(float x, float y, float w, float h,
                           float r, float g, float b, float a) {
        setProjection(new Matrix4f().ortho(
            -x / w, (sw - x) / w,
            -y / h, (sh - y) / h,
            -1, 1));
        uiShader.setVec4("uColor", r, g, b, a);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private void strokeRect(float x, float y, float w, float h, float bw,
                              float r, float g, float b, float a) {
        fillRect(x, y + h - bw, w, bw, r, g, b, a);
        fillRect(x, y, w, bw, r, g, b, a);
        fillRect(x, y, bw, h, r, g, b, a);
        fillRect(x + w - bw, y, bw, h, r, g, b, a);
    }

    private void setProjection(Matrix4f proj) {
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(16);
            proj.get(fb);
            glUniformMatrix4fv(
                glGetUniformLocation(uiShader.getProgramId(), "uProjection"),
                false, fb);
        }
    }

    public void cleanup() {
        if (quadVbo != 0) glDeleteBuffers(quadVbo);
        if (quadVao != 0) glDeleteVertexArrays(quadVao);
        if (uiShader != null) uiShader.cleanup();
    }
}
