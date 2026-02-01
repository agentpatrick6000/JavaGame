package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import com.voxelgame.sim.BlockBreakProgress;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.Inventory;
import com.voxelgame.sim.Player;
import com.voxelgame.sim.ToolItem;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Heads-up display. Renders a crosshair at screen center,
 * a hotbar at the bottom center showing 9 block slots with item counts,
 * a health bar (hearts) above the hotbar in survival modes,
 * and a block-breaking progress bar below the crosshair.
 */
public class Hud {

    private static final float CROSSHAIR_SIZE = 12.0f;
    private static final float CROSSHAIR_THICKNESS = 2.0f;

    private static final float SLOT_SIZE = 40.0f;
    private static final float SLOT_GAP = 4.0f;
    private static final float HOTBAR_MARGIN_BOTTOM = 10.0f;
    private static final float PREVIEW_SIZE = 28.0f;
    private static final float BORDER = 2.0f;
    private static final float SELECTED_BORDER = 3.0f;

    private static final float HEART_SIZE = 12.0f;
    private static final float HEART_GAP = 2.0f;
    private static final float HEART_MARGIN_ABOVE_HOTBAR = 6.0f;
    private static final int   HEARTS_COUNT = 10;

    private static final float BREAK_BAR_WIDTH = 60.0f;
    private static final float BREAK_BAR_HEIGHT = 4.0f;
    private static final float BREAK_BAR_OFFSET_Y = 20.0f;

    private static final float[][] BLOCK_COLORS = {
        {0.0f, 0.0f, 0.0f, 0.0f},       //  0 AIR
        {0.47f, 0.47f, 0.47f, 1.0f},     //  1 STONE
        {0.39f, 0.39f, 0.39f, 1.0f},     //  2 COBBLESTONE
        {0.53f, 0.38f, 0.26f, 1.0f},     //  3 DIRT
        {0.30f, 0.60f, 0.00f, 1.0f},     //  4 GRASS
        {0.84f, 0.81f, 0.60f, 1.0f},     //  5 SAND
        {0.51f, 0.49f, 0.49f, 1.0f},     //  6 GRAVEL
        {0.39f, 0.27f, 0.16f, 1.0f},     //  7 LOG
        {0.20f, 0.51f, 0.04f, 0.9f},     //  8 LEAVES
        {0.12f, 0.31f, 0.78f, 0.6f},     //  9 WATER
        {0.35f, 0.35f, 0.35f, 1.0f},     // 10 COAL_ORE
        {0.55f, 0.45f, 0.35f, 1.0f},     // 11 IRON_ORE
        {0.75f, 0.65f, 0.20f, 1.0f},     // 12 GOLD_ORE
        {0.39f, 0.86f, 1.00f, 1.0f},     // 13 DIAMOND_ORE
        {0.16f, 0.16f, 0.16f, 1.0f},     // 14 BEDROCK
        {0.95f, 0.55f, 0.50f, 1.0f},     // 15 RAW_PORKCHOP
        {0.55f, 0.40f, 0.25f, 1.0f},     // 16 ROTTEN_FLESH
        {0.70f, 0.55f, 0.30f, 1.0f},     // 17 PLANKS
        {0.60f, 0.45f, 0.25f, 1.0f},     // 18 CRAFTING_TABLE
        {0.65f, 0.50f, 0.25f, 1.0f},     // 19 STICK
        {0.58f, 0.42f, 0.20f, 1.0f},     // 20 WOODEN_PICKAXE
        {0.55f, 0.40f, 0.18f, 1.0f},     // 21 WOODEN_AXE
        {0.52f, 0.38f, 0.16f, 1.0f},     // 22 WOODEN_SHOVEL
        {0.50f, 0.50f, 0.52f, 1.0f},     // 23 STONE_PICKAXE
        {0.48f, 0.48f, 0.50f, 1.0f},     // 24 STONE_AXE
        {0.46f, 0.46f, 0.48f, 1.0f},     // 25 STONE_SHOVEL
    };

    private Shader uiShader;
    private int crosshairVao, crosshairVbo;
    private int quadVao, quadVbo;
    private BitmapFont font;

    // Breaking progress (0..1, set externally by GameLoop)
    private float breakProgress = 0;

    private int sw, sh;

    public void init() {
        uiShader = new Shader("shaders/ui.vert", "shaders/ui.frag");
        buildCrosshairVAO();
        buildQuadVAO();
    }

    /** Set the BitmapFont for rendering item counts. */
    public void setFont(BitmapFont font) {
        this.font = font;
    }

    /** Set the current block-breaking progress (0 = not breaking, 0..1 = progress). */
    public void setBreakProgress(float progress) {
        this.breakProgress = Math.max(0, Math.min(1, progress));
    }

    private void buildCrosshairVAO() {
        float s = CROSSHAIR_SIZE, t = CROSSHAIR_THICKNESS / 2.0f;
        float[] v = {
            -s,-t,  s,-t,  s, t,   -s,-t,  s, t, -s, t,
            -t,-s,  t,-s,  t, s,   -t,-s,  t, s, -t, s,
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

        uiShader.bind();

        renderCrosshair();
        if (breakProgress > 0) {
            renderBreakProgressBar();
        }
        if (player != null) {
            renderHotbar(player);
            if (player.getGameMode() != GameMode.CREATIVE) {
                renderHealthBar(player);
            }
            renderDamageFlash(player);
        }

        uiShader.unbind();
    }

    /** Overload that accepts BlockBreakProgress (used by GameLoop). */
    public void render(int screenW, int screenH, Player player, BlockBreakProgress bp) {
        if (bp != null && bp.isActive()) {
            setBreakProgress(bp.getProgressFraction());
        } else {
            setBreakProgress(0);
        }
        render(screenW, screenH, player);
    }

    public void render(int screenW, int screenH) {
        render(screenW, screenH, null);
    }

    /* ---- Crosshair ---- */

    private void renderCrosshair() {
        float cx = sw / 2.0f, cy = sh / 2.0f;
        setProjection(new Matrix4f().ortho(-cx, sw - cx, -cy, sh - cy, -1, 1));
        uiShader.setVec4("uColor", 1f, 1f, 1f, 0.85f);
        glBindVertexArray(crosshairVao);
        glDrawArrays(GL_TRIANGLES, 0, 12);
    }

    /* ---- Breaking progress bar ---- */

    private void renderBreakProgressBar() {
        glBindVertexArray(quadVao);

        float barX = (sw - BREAK_BAR_WIDTH) / 2.0f;
        float barY = sh / 2.0f - BREAK_BAR_OFFSET_Y - BREAK_BAR_HEIGHT;

        // Background
        fillRect(barX, barY, BREAK_BAR_WIDTH, BREAK_BAR_HEIGHT, 0.1f, 0.1f, 0.1f, 0.7f);

        // Fill (green → yellow → red)
        float fillW = BREAK_BAR_WIDTH * breakProgress;
        float r = breakProgress < 0.5f ? breakProgress * 2 : 1.0f;
        float g = breakProgress < 0.5f ? 1.0f : 1.0f - (breakProgress - 0.5f) * 2;
        fillRect(barX, barY, fillW, BREAK_BAR_HEIGHT, r, g, 0.2f, 0.9f);

        strokeRect(barX, barY, BREAK_BAR_WIDTH, BREAK_BAR_HEIGHT, 1.0f,
                   0.4f, 0.4f, 0.4f, 0.8f);
    }

    /* ---- Hotbar ---- */

    private void renderHotbar(Player player) {
        glBindVertexArray(quadVao);

        float totalW = Player.HOTBAR_SIZE * SLOT_SIZE + (Player.HOTBAR_SIZE - 1) * SLOT_GAP;
        float x0 = (sw - totalW) / 2.0f;
        float y0 = HOTBAR_MARGIN_BOTTOM;

        Inventory inventory = player.getInventory();

        for (int i = 0; i < Player.HOTBAR_SIZE; i++) {
            float sx = x0 + i * (SLOT_SIZE + SLOT_GAP);
            boolean sel = (i == player.getSelectedSlot());

            // Slot background
            fillRect(sx, y0, SLOT_SIZE, SLOT_SIZE, 0.1f, 0.1f, 0.1f, 0.75f);

            // Block preview from inventory
            Inventory.ItemStack stack = inventory.getSlot(i);
            int bid = (stack != null) ? stack.getBlockId() : 0;
            if (bid > 0 && bid < BLOCK_COLORS.length && stack != null && !stack.isEmpty()) {
                float[] c = BLOCK_COLORS[bid];
                float off = (SLOT_SIZE - PREVIEW_SIZE) / 2f;

                if (stack.hasDurability()) {
                    // Tool: render with distinctive shape
                    float headH = PREVIEW_SIZE * 0.5f;
                    float handleW = PREVIEW_SIZE * 0.25f;
                    float handleH = PREVIEW_SIZE * 0.5f;
                    fillRect(sx + off, y0 + off + handleH, PREVIEW_SIZE, headH, c[0], c[1], c[2], c[3]);
                    float hx = sx + off + (PREVIEW_SIZE - handleW) / 2;
                    fillRect(hx, y0 + off, handleW, handleH, 0.5f, 0.35f, 0.15f, 1.0f);
                    float durFrac = stack.getDurabilityFraction();
                    if (durFrac >= 0 && durFrac < 1.0f) {
                        float barH = 3.0f;
                        fillRect(sx + 2, y0 + 2, SLOT_SIZE - 4, barH, 0.1f, 0.1f, 0.1f, 0.7f);
                        float dr = durFrac < 0.5f ? 1.0f : durFrac * 2 - 1;
                        float dg = durFrac > 0.5f ? 1.0f : durFrac * 2;
                        fillRect(sx + 2, y0 + 2, (SLOT_SIZE - 4) * durFrac, barH, dr, dg, 0.2f, 0.9f);
                    }
                } else {
                    fillRect(sx + off, y0 + off, PREVIEW_SIZE, PREVIEW_SIZE, c[0], c[1], c[2], c[3]);
                    if (player.getGameMode() != GameMode.CREATIVE && stack.getCount() < Inventory.MAX_STACK) {
                        float countFrac = (float) stack.getCount() / Inventory.MAX_STACK;
                        float barH = 3.0f;
                        fillRect(sx + 2, y0 + 2, (SLOT_SIZE - 4) * countFrac, barH,
                                 0.2f, 0.8f, 0.2f, 0.7f);
                    }
                }

                // Render numeric count using BitmapFont
                if (!stack.hasDurability() && stack.getCount() > 1 && player.getGameMode() != GameMode.CREATIVE && font != null) {
                    uiShader.unbind();
                    String countStr = String.valueOf(stack.getCount());
                    float textX = sx + SLOT_SIZE - 6 - countStr.length() * 7;
                    float textY = y0 + 3;
                    // Shadow
                    font.drawText(countStr, textX + 1, textY + 1, 1.5f, sw, sh,
                                 0.0f, 0.0f, 0.0f, 0.8f);
                    font.drawText(countStr, textX, textY, 1.5f, sw, sh,
                                 1.0f, 1.0f, 1.0f, 1.0f);
                    uiShader.bind();
                    glBindVertexArray(quadVao);
                }
            }

            // Border
            if (sel) {
                strokeRect(sx, y0, SLOT_SIZE, SLOT_SIZE, SELECTED_BORDER, 1f, 1f, 1f, 1f);
            } else {
                strokeRect(sx, y0, SLOT_SIZE, SLOT_SIZE, BORDER, 0.5f, 0.5f, 0.5f, 0.6f);
            }
        }
    }

    /* ---- Health bar ---- */

    private void renderHealthBar(Player player) {
        glBindVertexArray(quadVao);

        float health = player.getHealth();

        float totalHotbarW = Player.HOTBAR_SIZE * SLOT_SIZE + (Player.HOTBAR_SIZE - 1) * SLOT_GAP;
        float hotbarX0 = (sw - totalHotbarW) / 2.0f;
        float heartsY = HOTBAR_MARGIN_BOTTOM + SLOT_SIZE + HEART_MARGIN_ABOVE_HOTBAR;

        for (int i = 0; i < HEARTS_COUNT; i++) {
            float hx = hotbarX0 + i * (HEART_SIZE + HEART_GAP);
            float heartHP = (i + 1) * 2.0f;
            float prevHP  = i * 2.0f;

            fillRect(hx, heartsY, HEART_SIZE, HEART_SIZE, 0.15f, 0.05f, 0.05f, 0.8f);
            strokeRect(hx, heartsY, HEART_SIZE, HEART_SIZE, 1.0f, 0.3f, 0.1f, 0.1f, 0.9f);

            if (health >= heartHP) {
                float inset = 2.0f;
                fillRect(hx + inset, heartsY + inset,
                         HEART_SIZE - inset * 2, HEART_SIZE - inset * 2,
                         0.85f, 0.1f, 0.1f, 1.0f);
            } else if (health > prevHP) {
                float inset = 2.0f;
                float fraction = (health - prevHP) / 2.0f;
                float fillW = (HEART_SIZE - inset * 2) * fraction;
                fillRect(hx + inset, heartsY + inset,
                         fillW, HEART_SIZE - inset * 2,
                         0.85f, 0.1f, 0.1f, 1.0f);
            }
        }
    }

    /* ---- Damage flash ---- */

    private void renderDamageFlash(Player player) {
        float intensity = player.getDamageFlashIntensity();
        if (intensity <= 0) return;

        glBindVertexArray(quadVao);
        setProjection(new Matrix4f().ortho(0, 1, 0, 1, -1, 1));
        uiShader.setVec4("uColor", 0.8f, 0.0f, 0.0f, 0.3f * intensity);
        glDrawArrays(GL_TRIANGLES, 0, 6);
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
        if (crosshairVbo != 0) glDeleteBuffers(crosshairVbo);
        if (crosshairVao != 0) glDeleteVertexArrays(crosshairVao);
        if (quadVbo != 0) glDeleteBuffers(quadVbo);
        if (quadVao != 0) glDeleteVertexArrays(quadVao);
        if (uiShader != null) uiShader.cleanup();
    }
}
