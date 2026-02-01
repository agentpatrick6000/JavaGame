package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import com.voxelgame.sim.Inventory;
import com.voxelgame.sim.Recipe;
import com.voxelgame.sim.RecipeRegistry;
import com.voxelgame.sim.ToolItem;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Full-screen inventory UI with 2x2 crafting grid. Opened with E key.
 */
public class InventoryScreen {

    private static final float SLOT_SIZE = 44.0f;
    private static final float SLOT_GAP = 4.0f;
    private static final float ROW_GAP = 8.0f;
    private static final float PREVIEW_SIZE = 30.0f;
    private static final float BORDER = 2.0f;
    private static final float SELECTED_BORDER = 3.0f;
    private static final float BG_PADDING = 16.0f;
    private static final float CRAFTING_GAP = 16.0f;

    private static final int GRID_SLOT_BASE = 100;
    private static final int RESULT_SLOT = 104;

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
        {0.95f, 0.55f, 0.50f, 1.0f},
        {0.55f, 0.40f, 0.25f, 1.0f},
        {0.70f, 0.55f, 0.30f, 1.0f},
        {0.60f, 0.45f, 0.25f, 1.0f},
        {0.65f, 0.50f, 0.25f, 1.0f},
        {0.58f, 0.42f, 0.20f, 1.0f},
        {0.55f, 0.40f, 0.18f, 1.0f},
        {0.52f, 0.38f, 0.16f, 1.0f},
        {0.50f, 0.50f, 0.52f, 1.0f},
        {0.48f, 0.48f, 0.50f, 1.0f},
        {0.46f, 0.46f, 0.48f, 1.0f},
    };

    private boolean visible = false;
    private Inventory.ItemStack heldItem = null;
    private final Inventory.ItemStack[] craftingGrid = new Inventory.ItemStack[4];
    private Recipe currentRecipe = null;

    private Shader uiShader;
    private int quadVao, quadVbo;
    private BitmapFont font;
    private int sw, sh;
    private float mouseX, mouseY;

    private float invX0, invY0;
    private float craftX0, craftY0;
    private float resultX, resultY;

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
    public void setVisible(boolean v) { visible = v; if (!v) heldItem = null; }
    public void toggle() { visible = !visible; if (!visible) heldItem = null; }
    public boolean isOpen() { return visible; }

    public void close(Inventory inventory) {
        visible = false;
        if (heldItem != null) {
            inventory.addItemStack(heldItem);
            heldItem = null;
        }
        for (int i = 0; i < 4; i++) {
            if (craftingGrid[i] != null && !craftingGrid[i].isEmpty()) {
                inventory.addItemStack(craftingGrid[i]);
                craftingGrid[i] = null;
            }
        }
        currentRecipe = null;
    }

    public void close() { visible = false; heldItem = null; }

    public void updateMouse(double mx, double my, int screenH) {
        this.mouseX = (float) mx;
        this.mouseY = screenH - (float) my;
    }

    // ================================================================
    // Click handling
    // ================================================================

    public void handleClick(Inventory inventory, double mx, double my,
                            int screenW, int screenH, boolean shiftHeld) {
        if (!visible) return;
        this.sw = screenW;
        this.sh = screenH;
        float clickX = (float) mx;
        float clickY = screenH - (float) my;
        computeLayout();

        int clickedSlot = getSlotAt(clickX, clickY);
        if (clickedSlot < 0) return;

        if (clickedSlot >= 0 && clickedSlot < Inventory.TOTAL_SIZE) {
            handleInventorySlotClick(inventory, clickedSlot);
        } else if (clickedSlot >= GRID_SLOT_BASE && clickedSlot <= GRID_SLOT_BASE + 3) {
            handleCraftingGridClick(clickedSlot - GRID_SLOT_BASE);
        } else if (clickedSlot == RESULT_SLOT) {
            handleCraftingResultClick(inventory, shiftHeld);
        }
        updateCraftingResult();
    }

    public void handleClick(Inventory inventory, double mx, double my,
                            int screenW, int screenH) {
        handleClick(inventory, mx, my, screenW, screenH, false);
    }

    private void handleInventorySlotClick(Inventory inventory, int slot) {
        Inventory.ItemStack slotItem = inventory.getSlot(slot);
        if (heldItem == null) {
            if (slotItem != null && !slotItem.isEmpty()) {
                heldItem = slotItem.copy();
                inventory.setSlot(slot, null);
            }
        } else {
            if (slotItem == null || slotItem.isEmpty()) {
                inventory.setSlot(slot, heldItem);
                heldItem = null;
            } else if (slotItem.getBlockId() == heldItem.getBlockId()
                       && !slotItem.isFull()
                       && !slotItem.hasDurability() && !heldItem.hasDurability()) {
                int leftover = slotItem.add(heldItem.getCount());
                if (leftover > 0) heldItem.setCount(leftover);
                else heldItem = null;
            } else {
                inventory.setSlot(slot, heldItem);
                heldItem = slotItem;
            }
        }
    }

    private void handleCraftingGridClick(int gridIdx) {
        Inventory.ItemStack gridItem = craftingGrid[gridIdx];
        if (heldItem == null) {
            if (gridItem != null && !gridItem.isEmpty()) {
                heldItem = gridItem.copy();
                craftingGrid[gridIdx] = null;
            }
        } else {
            if (gridItem == null || gridItem.isEmpty()) {
                craftingGrid[gridIdx] = heldItem;
                heldItem = null;
            } else if (gridItem.getBlockId() == heldItem.getBlockId()
                       && !gridItem.isFull()
                       && !gridItem.hasDurability() && !heldItem.hasDurability()) {
                int leftover = gridItem.add(heldItem.getCount());
                if (leftover > 0) heldItem.setCount(leftover);
                else heldItem = null;
            } else {
                craftingGrid[gridIdx] = heldItem;
                heldItem = gridItem;
            }
        }
    }

    private void handleCraftingResultClick(Inventory inventory, boolean shiftClick) {
        if (currentRecipe == null) return;
        if (shiftClick) {
            int crafted = 0;
            while (currentRecipe != null) {
                int outputId = currentRecipe.getOutputId();
                int outputCount = currentRecipe.getOutputCount();
                if (ToolItem.isTool(outputId)) {
                    if (!inventory.hasSpace()) break;
                    int maxDur = ToolItem.getMaxDurability(outputId);
                    inventory.addItemStack(new Inventory.ItemStack(outputId, maxDur, maxDur));
                } else {
                    if (!inventory.canAdd(outputId)) break;
                    inventory.addItem(outputId, outputCount);
                }
                consumeCraftingIngredients();
                updateCraftingResult();
                crafted++;
                if (crafted > 256) break;
            }
        } else {
            int outputId = currentRecipe.getOutputId();
            int outputCount = currentRecipe.getOutputCount();
            if (heldItem == null) {
                if (ToolItem.isTool(outputId)) {
                    int maxDur = ToolItem.getMaxDurability(outputId);
                    heldItem = new Inventory.ItemStack(outputId, maxDur, maxDur);
                } else {
                    heldItem = new Inventory.ItemStack(outputId, outputCount);
                }
                consumeCraftingIngredients();
            } else if (heldItem.getBlockId() == outputId
                       && !heldItem.hasDurability()
                       && heldItem.getCount() + outputCount <= heldItem.getMaxStack()) {
                heldItem.add(outputCount);
                consumeCraftingIngredients();
            }
        }
        updateCraftingResult();
    }

    private void consumeCraftingIngredients() {
        for (int i = 0; i < 4; i++) {
            if (craftingGrid[i] != null && !craftingGrid[i].isEmpty()) {
                craftingGrid[i].remove(1);
                if (craftingGrid[i].isEmpty()) craftingGrid[i] = null;
            }
        }
    }

    private void updateCraftingResult() {
        int[] grid = new int[4];
        for (int i = 0; i < 4; i++) {
            grid[i] = (craftingGrid[i] != null && !craftingGrid[i].isEmpty())
                     ? craftingGrid[i].getBlockId() : 0;
        }
        currentRecipe = RecipeRegistry.findMatch(grid);
    }

    // ================================================================
    // Layout
    // ================================================================

    private void computeLayout() {
        float gridW = 9 * SLOT_SIZE + 8 * SLOT_GAP;
        float invH = 4 * SLOT_SIZE + 3 * SLOT_GAP + ROW_GAP;
        float craftH = 2 * SLOT_SIZE + SLOT_GAP;
        float totalH = invH + CRAFTING_GAP + craftH;

        invX0 = (sw - gridW) / 2.0f;
        invY0 = (sh - totalH) / 2.0f;

        craftY0 = invY0 + invH + CRAFTING_GAP;
        float craftGridW = 2 * SLOT_SIZE + SLOT_GAP;
        craftX0 = invX0 + gridW - craftGridW - SLOT_SIZE - SLOT_GAP * 3;

        resultX = craftX0 + craftGridW + SLOT_GAP * 4 + 20;
        resultY = craftY0 + (craftH - SLOT_SIZE) / 2.0f;
    }

    private int getSlotAt(float mx, float my) {
        computeLayout();

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = invX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;
                if (row == 0) sy = invY0;
                else sy = invY0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);

                if (mx >= sx && mx <= sx + SLOT_SIZE && my >= sy && my <= sy + SLOT_SIZE) {
                    return (row == 0) ? col : (9 + (row - 1) * 9 + col);
                }
            }
        }

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                float sx = craftX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy = craftY0 + (1 - row) * (SLOT_SIZE + SLOT_GAP);
                if (mx >= sx && mx <= sx + SLOT_SIZE && my >= sy && my <= sy + SLOT_SIZE) {
                    return GRID_SLOT_BASE + row * 2 + col;
                }
            }
        }

        if (mx >= resultX && mx <= resultX + SLOT_SIZE
            && my >= resultY && my <= resultY + SLOT_SIZE) {
            return RESULT_SLOT;
        }

        return -1;
    }

    // ================================================================
    // Rendering
    // ================================================================

    public void render(int screenW, int screenH, Inventory inventory) {
        if (!visible) return;
        this.sw = screenW;
        this.sh = screenH;
        computeLayout();

        uiShader.bind();
        glBindVertexArray(quadVao);

        fillRect(0, 0, sw, sh, 0.0f, 0.0f, 0.0f, 0.6f);

        float gridW = 9 * SLOT_SIZE + 8 * SLOT_GAP;
        float invH = 4 * SLOT_SIZE + 3 * SLOT_GAP + ROW_GAP;
        float craftH = 2 * SLOT_SIZE + SLOT_GAP;
        float totalH = invH + CRAFTING_GAP + craftH;

        fillRect(invX0 - BG_PADDING, invY0 - BG_PADDING,
                 gridW + BG_PADDING * 2, totalH + BG_PADDING * 2,
                 0.15f, 0.15f, 0.15f, 0.9f);

        renderInventorySlots(inventory);
        renderCraftingGrid();
        renderCraftingArrow();
        renderResultSlot();

        if (heldItem != null && !heldItem.isEmpty()) {
            renderItemPreview(mouseX - SLOT_SIZE / 2, mouseY - SLOT_SIZE / 2, heldItem);
        }

        uiShader.unbind();
        if (font != null) {
            float titleY = invY0 + totalH + BG_PADDING + 4;
            font.drawText("Inventory", invX0, titleY, 2.0f, sw, sh, 0.9f, 0.9f, 0.9f, 1.0f);
            font.drawText("Crafting", craftX0, craftY0 + craftH + 4, 1.8f, sw, sh, 0.9f, 0.9f, 0.7f, 1.0f);
            renderInventoryText(inventory);
            renderCraftingGridText();
            renderResultText();
            if (heldItem != null && !heldItem.isEmpty() && heldItem.getCount() > 1 && !heldItem.hasDurability()) {
                String cs = String.valueOf(heldItem.getCount());
                float tx = mouseX - SLOT_SIZE / 2 + SLOT_SIZE - 8 * cs.length() - 2;
                float ty = mouseY - SLOT_SIZE / 2 + 2;
                font.drawText(cs, tx + 1, ty + 1, 1.5f, sw, sh, 0, 0, 0, 0.8f);
                font.drawText(cs, tx, ty, 1.5f, sw, sh, 1, 1, 1, 1);
            }
            if (heldItem != null && heldItem.hasDurability()) {
                String name = ToolItem.getDisplayName(heldItem.getBlockId());
                font.drawText(name, mouseX + SLOT_SIZE / 2 + 4, mouseY, 1.5f, sw, sh, 1, 1, 0.7f, 1);
            }
        }
    }

    private void renderInventorySlots(Inventory inventory) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = invX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;
                if (row == 0) sy = invY0;
                else sy = invY0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
                int slot = (row == 0) ? col : (9 + (row - 1) * 9 + col);
                fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.2f, 0.2f, 0.2f, 0.8f);
                Inventory.ItemStack stack = inventory.getSlot(slot);
                if (stack != null && !stack.isEmpty()) renderItemPreview(sx, sy, stack);
                strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, BORDER, 0.4f, 0.4f, 0.4f, 0.7f);
            }
        }
    }

    private void renderCraftingGrid() {
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                float sx = craftX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy = craftY0 + (1 - row) * (SLOT_SIZE + SLOT_GAP);
                fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.22f, 0.20f, 0.18f, 0.8f);
                int gridIdx = row * 2 + col;
                if (craftingGrid[gridIdx] != null && !craftingGrid[gridIdx].isEmpty())
                    renderItemPreview(sx, sy, craftingGrid[gridIdx]);
                strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, BORDER, 0.5f, 0.45f, 0.3f, 0.8f);
            }
        }
    }

    private void renderCraftingArrow() {
        float craftGridW = 2 * SLOT_SIZE + SLOT_GAP;
        float arrowX = craftX0 + craftGridW + SLOT_GAP * 2;
        float arrowY = craftY0 + (2 * SLOT_SIZE + SLOT_GAP - 8) / 2.0f;
        fillRect(arrowX, arrowY, 20, 4, 0.7f, 0.7f, 0.7f, 0.8f);
        fillRect(arrowX + 16, arrowY - 4, 4, 4, 0.7f, 0.7f, 0.7f, 0.8f);
        fillRect(arrowX + 16, arrowY + 4, 4, 4, 0.7f, 0.7f, 0.7f, 0.8f);
    }

    private void renderResultSlot() {
        float bgR = 0.18f, bgG = 0.22f, bgB = 0.18f;
        if (currentRecipe != null) { bgR = 0.20f; bgG = 0.28f; bgB = 0.20f; }
        fillRect(resultX, resultY, SLOT_SIZE, SLOT_SIZE, bgR, bgG, bgB, 0.8f);
        if (currentRecipe != null) {
            int outputId = currentRecipe.getOutputId();
            Inventory.ItemStack resultStack;
            if (ToolItem.isTool(outputId)) {
                int maxDur = ToolItem.getMaxDurability(outputId);
                resultStack = new Inventory.ItemStack(outputId, maxDur, maxDur);
            } else {
                resultStack = new Inventory.ItemStack(outputId, currentRecipe.getOutputCount());
            }
            renderItemPreview(resultX, resultY, resultStack);
        }
        float bR = 0.7f, bG = 0.6f, bB = 0.2f;
        if (currentRecipe != null) { bR = 1.0f; bG = 0.85f; bB = 0.3f; }
        strokeRect(resultX, resultY, SLOT_SIZE, SLOT_SIZE, SELECTED_BORDER, bR, bG, bB, 0.9f);
    }

    private void renderItemPreview(float sx, float sy, Inventory.ItemStack stack) {
        int bid = stack.getBlockId();
        if (bid > 0 && bid < BLOCK_COLORS.length) {
            float[] c = BLOCK_COLORS[bid];
            float off = (SLOT_SIZE - PREVIEW_SIZE) / 2f;
            if (stack.hasDurability()) {
                float headH = PREVIEW_SIZE * 0.5f;
                float handleW = PREVIEW_SIZE * 0.25f;
                float handleH = PREVIEW_SIZE * 0.5f;
                fillRect(sx + off, sy + off + handleH, PREVIEW_SIZE, headH, c[0], c[1], c[2], c[3]);
                float hx = sx + off + (PREVIEW_SIZE - handleW) / 2;
                fillRect(hx, sy + off, handleW, handleH, 0.5f, 0.35f, 0.15f, 1.0f);
                float durFrac = stack.getDurabilityFraction();
                if (durFrac >= 0 && durFrac < 1.0f) {
                    fillRect(sx + 2, sy + 2, SLOT_SIZE - 4, 3, 0.1f, 0.1f, 0.1f, 0.7f);
                    float r = durFrac < 0.5f ? 1.0f : durFrac * 2 - 1;
                    float g = durFrac > 0.5f ? 1.0f : durFrac * 2;
                    fillRect(sx + 2, sy + 2, (SLOT_SIZE - 4) * durFrac, 3, r, g, 0.2f, 0.9f);
                }
            } else {
                fillRect(sx + off, sy + off, PREVIEW_SIZE, PREVIEW_SIZE, c[0], c[1], c[2], c[3]);
            }
        }
    }

    private void renderInventoryText(Inventory inventory) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = invX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;
                if (row == 0) sy = invY0;
                else sy = invY0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
                int slot = (row == 0) ? col : (9 + (row - 1) * 9 + col);
                Inventory.ItemStack stack = inventory.getSlot(slot);
                if (stack != null && stack.getCount() > 1 && !stack.hasDurability())
                    renderCountText(sx, sy, stack.getCount());
            }
        }
    }

    private void renderCraftingGridText() {
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                float sx = craftX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy = craftY0 + (1 - row) * (SLOT_SIZE + SLOT_GAP);
                int gridIdx = row * 2 + col;
                if (craftingGrid[gridIdx] != null && craftingGrid[gridIdx].getCount() > 1
                    && !craftingGrid[gridIdx].hasDurability())
                    renderCountText(sx, sy, craftingGrid[gridIdx].getCount());
            }
        }
    }

    private void renderResultText() {
        if (currentRecipe != null && currentRecipe.getOutputCount() > 1
            && !ToolItem.isTool(currentRecipe.getOutputId()))
            renderCountText(resultX, resultY, currentRecipe.getOutputCount());
    }

    private void renderCountText(float sx, float sy, int count) {
        String cs = String.valueOf(count);
        float tx = sx + SLOT_SIZE - 8 * cs.length() - 2;
        float ty = sy + 2;
        font.drawText(cs, tx + 1, ty + 1, 1.5f, sw, sh, 0, 0, 0, 0.8f);
        font.drawText(cs, tx, ty, 1.5f, sw, sh, 1, 1, 1, 1);
    }

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
