package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import com.voxelgame.render.TextureAtlas;
import com.voxelgame.sim.Inventory;
import com.voxelgame.sim.Recipe;
import com.voxelgame.sim.RecipeRegistry;
import com.voxelgame.sim.ToolItem;
import com.voxelgame.world.Block;
import com.voxelgame.world.Blocks;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Full-screen inventory UI with 2x2 crafting grid.
 * Supports: textured items, tooltips, right-click, shift-click, drag distribution.
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
        {0.60f, 0.40f, 0.20f, 1.0f},     // 26 CHEST
        {0.45f, 0.45f, 0.45f, 1.0f},     // 27 RAIL
        {0.85f, 0.20f, 0.15f, 1.0f},     // 28 TNT
        {0.55f, 0.35f, 0.15f, 1.0f},     // 29 BOAT
        {0.50f, 0.50f, 0.55f, 1.0f},     // 30 MINECART
        {0.45f, 0.45f, 0.48f, 1.0f},     // 31 FURNACE
        {0.75f, 0.55f, 0.25f, 1.0f},     // 32 TORCH
        {0.15f, 0.15f, 0.15f, 1.0f},     // 33 COAL
        {0.80f, 0.78f, 0.75f, 1.0f},     // 34 IRON_INGOT
        {0.85f, 0.92f, 0.95f, 0.7f},     // 35 GLASS
        {0.78f, 0.51f, 0.31f, 1.0f},     // 36 COOKED_PORKCHOP
        {0.86f, 0.12f, 0.12f, 1.0f},     // 37 RED_FLOWER
        {1.00f, 0.90f, 0.20f, 1.0f},     // 38 YELLOW_FLOWER
        {0.39f, 0.86f, 1.00f, 1.0f},     // 39 DIAMOND
        {0.60f, 0.60f, 0.65f, 1.0f},     // 40 IRON_PICKAXE
        {0.60f, 0.60f, 0.65f, 1.0f},     // 41 IRON_AXE
        {0.60f, 0.60f, 0.65f, 1.0f},     // 42 IRON_SHOVEL
        {0.60f, 0.60f, 0.65f, 1.0f},     // 43 IRON_SWORD
        {0.58f, 0.42f, 0.20f, 1.0f},     // 44 WOODEN_SWORD
        {0.50f, 0.50f, 0.52f, 1.0f},     // 45 STONE_SWORD
        {0.20f, 0.20f, 0.20f, 1.0f},     // 46 CHARCOAL
    };

    private boolean visible = false;
    private Inventory.ItemStack heldItem = null;
    private final Inventory.ItemStack[] craftingGrid = new Inventory.ItemStack[4];
    private Recipe currentRecipe = null;

    private Shader uiShader;
    private Shader texShader;
    private int quadVao, quadVbo;
    private BitmapFont font;
    private TextureAtlas atlas;
    private int sw, sh;
    private float mouseX, mouseY;

    private float invX0, invY0;
    private float craftX0, craftY0;
    private float resultX, resultY;

    // ---- Tooltip state ----
    private int hoveredSlot = -1;

    // ---- Drag state ----
    private boolean isDragging = false;
    private boolean dragIsRight = false;
    private final List<Integer> dragSlots = new ArrayList<>();
    private Inventory.ItemStack dragOriginal = null; // snapshot of held item at drag start

    public void init(BitmapFont font) {
        this.font = font;
        uiShader = new Shader("shaders/ui.vert", "shaders/ui.frag");
        texShader = new Shader("shaders/ui_tex.vert", "shaders/ui_tex.frag");

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

    public void setAtlas(TextureAtlas atlas) { 
        this.atlas = atlas;
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { visible = v; if (!v) { heldItem = null; cancelDrag(); } }
    public void toggle() { visible = !visible; if (!visible) { heldItem = null; cancelDrag(); } }
    public boolean isOpen() { return visible; }

    /** Get the currently hovered slot index, or -1 if none. */
    public int getHoveredSlot() {
        return hoveredSlot;
    }

    public void close(Inventory inventory) {
        visible = false;
        cancelDrag();
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

    public void close() { visible = false; heldItem = null; cancelDrag(); }

    public void updateMouse(double mx, double my, int screenH) {
        this.mouseX = (float) mx;
        this.mouseY = screenH - (float) my;
    }

    // ================================================================
    // Click handling — left click
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

        if (shiftHeld) {
            handleShiftClick(inventory, clickedSlot);
        } else if (clickedSlot >= 0 && clickedSlot < Inventory.TOTAL_SIZE) {
            handleInventorySlotClick(inventory, clickedSlot);
        } else if (clickedSlot >= GRID_SLOT_BASE && clickedSlot <= GRID_SLOT_BASE + 3) {
            handleCraftingGridClick(clickedSlot - GRID_SLOT_BASE);
        } else if (clickedSlot == RESULT_SLOT) {
            handleCraftingResultClick(inventory, false);
        }
        updateCraftingResult();
    }

    public void handleClick(Inventory inventory, double mx, double my,
                            int screenW, int screenH) {
        handleClick(inventory, mx, my, screenW, screenH, false);
    }

    // ================================================================
    // Right-click handling
    // ================================================================

    public void handleRightClick(Inventory inventory, double mx, double my,
                                  int screenW, int screenH) {
        if (!visible) return;
        this.sw = screenW;
        this.sh = screenH;
        float clickX = (float) mx;
        float clickY = screenH - (float) my;
        computeLayout();

        int clickedSlot = getSlotAt(clickX, clickY);
        if (clickedSlot < 0) return;

        if (clickedSlot >= 0 && clickedSlot < Inventory.TOTAL_SIZE) {
            handleInventorySlotRightClick(inventory, clickedSlot);
        } else if (clickedSlot >= GRID_SLOT_BASE && clickedSlot <= GRID_SLOT_BASE + 3) {
            handleCraftingGridRightClick(clickedSlot - GRID_SLOT_BASE);
        } else if (clickedSlot == RESULT_SLOT) {
            handleCraftingResultClick(inventory, false);
        }
        updateCraftingResult();
    }

    private void handleInventorySlotRightClick(Inventory inventory, int slot) {
        Inventory.ItemStack slotItem = inventory.getSlot(slot);
        if (heldItem == null) {
            // Pick up half (round up)
            if (slotItem != null && !slotItem.isEmpty()) {
                int total = slotItem.getCount();
                int takeAmount = (total + 1) / 2; // round up
                heldItem = slotItem.copy();
                heldItem.setCount(takeAmount);
                slotItem.setCount(total - takeAmount);
                if (slotItem.isEmpty()) inventory.setSlot(slot, null);
            }
        } else {
            // Place 1 item
            if (slotItem == null || slotItem.isEmpty()) {
                Inventory.ItemStack placed = heldItem.copy();
                placed.setCount(1);
                inventory.setSlot(slot, placed);
                heldItem.remove(1);
                if (heldItem.isEmpty()) heldItem = null;
            } else if (slotItem.getBlockId() == heldItem.getBlockId()
                       && !slotItem.isFull()
                       && !slotItem.hasDurability() && !heldItem.hasDurability()) {
                slotItem.add(1);
                heldItem.remove(1);
                if (heldItem.isEmpty()) heldItem = null;
            }
        }
    }

    private void handleCraftingGridRightClick(int gridIdx) {
        Inventory.ItemStack gridItem = craftingGrid[gridIdx];
        if (heldItem == null) {
            // Pick up half (round up)
            if (gridItem != null && !gridItem.isEmpty()) {
                int total = gridItem.getCount();
                int takeAmount = (total + 1) / 2;
                heldItem = gridItem.copy();
                heldItem.setCount(takeAmount);
                gridItem.setCount(total - takeAmount);
                if (gridItem.isEmpty()) craftingGrid[gridIdx] = null;
            }
        } else {
            // Place 1 item
            if (gridItem == null || gridItem.isEmpty()) {
                Inventory.ItemStack placed = heldItem.copy();
                placed.setCount(1);
                craftingGrid[gridIdx] = placed;
                heldItem.remove(1);
                if (heldItem.isEmpty()) heldItem = null;
            } else if (gridItem.getBlockId() == heldItem.getBlockId()
                       && !gridItem.isFull()
                       && !gridItem.hasDurability() && !heldItem.hasDurability()) {
                gridItem.add(1);
                heldItem.remove(1);
                if (heldItem.isEmpty()) heldItem = null;
            }
        }
    }

    // ================================================================
    // Shift-click handling
    // ================================================================

    private void handleShiftClick(Inventory inventory, int clickedSlot) {
        if (clickedSlot == RESULT_SLOT) {
            handleCraftingResultClick(inventory, true);
            return;
        }

        if (clickedSlot >= GRID_SLOT_BASE && clickedSlot <= GRID_SLOT_BASE + 3) {
            // Move crafting grid item to inventory
            int gridIdx = clickedSlot - GRID_SLOT_BASE;
            if (craftingGrid[gridIdx] != null && !craftingGrid[gridIdx].isEmpty()) {
                inventory.addItemStack(craftingGrid[gridIdx]);
                craftingGrid[gridIdx] = null;
            }
            return;
        }

        if (clickedSlot < 0 || clickedSlot >= Inventory.TOTAL_SIZE) return;

        Inventory.ItemStack slotItem = inventory.getSlot(clickedSlot);
        if (slotItem == null || slotItem.isEmpty()) return;

        boolean isHotbar = clickedSlot < Inventory.HOTBAR_SIZE;

        if (isHotbar) {
            // Move hotbar → storage (slots 9-35): try stacking first, then empty slots
            moveToRange(inventory, clickedSlot, Inventory.HOTBAR_SIZE, Inventory.TOTAL_SIZE);
        } else {
            // Move storage → hotbar (slots 0-8): try stacking first, then empty slots
            moveToRange(inventory, clickedSlot, 0, Inventory.HOTBAR_SIZE);
        }
    }

    private void moveToRange(Inventory inventory, int sourceSlot, int destStart, int destEnd) {
        Inventory.ItemStack source = inventory.getSlot(sourceSlot);
        if (source == null || source.isEmpty()) return;

        // First pass: try to stack with existing matching slots
        if (!source.hasDurability()) {
            for (int i = destStart; i < destEnd && !source.isEmpty(); i++) {
                Inventory.ItemStack dest = inventory.getSlot(i);
                if (dest != null && !dest.isEmpty()
                    && dest.getBlockId() == source.getBlockId()
                    && !dest.isFull() && !dest.hasDurability()) {
                    int leftover = dest.add(source.getCount());
                    source.setCount(leftover);
                }
            }
        }

        // Second pass: find empty slots
        for (int i = destStart; i < destEnd && !source.isEmpty(); i++) {
            Inventory.ItemStack dest = inventory.getSlot(i);
            if (dest == null || dest.isEmpty()) {
                inventory.setSlot(i, source.copy());
                source.setCount(0);
            }
        }

        if (source.isEmpty()) {
            inventory.setSlot(sourceSlot, null);
        }
    }

    // ================================================================
    // Drag handling
    // ================================================================

    /**
     * Called each frame to update drag state.
     * @param leftDown  true if left mouse button is held
     * @param rightDown true if right mouse button is held
     */
    public void updateDrag(Inventory inventory, boolean leftDown, boolean rightDown,
                           double mx, double my, int screenW, int screenH) {
        if (!visible) return;
        this.sw = screenW;
        this.sh = screenH;
        float cx = (float) mx;
        float cy = screenH - (float) my;
        computeLayout();

        if (heldItem == null || heldItem.isEmpty()) {
            cancelDrag();
            return;
        }

        // Start drag if moving with button held and we have items
        if (!isDragging && (leftDown || rightDown)) {
            int slot = getSlotAt(cx, cy);
            if (slot >= 0 && dragSlots.isEmpty()) {
                isDragging = true;
                dragIsRight = rightDown;
                dragOriginal = heldItem.copy();
                dragSlots.clear();
            }
        }

        if (isDragging) {
            int slot = getSlotAt(cx, cy);
            if (slot >= 0 && slot != RESULT_SLOT && !dragSlots.contains(slot)) {
                // Check if this slot is compatible for dragging
                boolean canAdd = false;
                if (slot < Inventory.TOTAL_SIZE) {
                    Inventory.ItemStack slotItem = inventory.getSlot(slot);
                    canAdd = (slotItem == null || slotItem.isEmpty()
                              || (slotItem.getBlockId() == heldItem.getBlockId()
                                  && !slotItem.isFull() && !slotItem.hasDurability()));
                } else if (slot >= GRID_SLOT_BASE && slot <= GRID_SLOT_BASE + 3) {
                    int gi = slot - GRID_SLOT_BASE;
                    Inventory.ItemStack gridItem = craftingGrid[gi];
                    canAdd = (gridItem == null || gridItem.isEmpty()
                              || (gridItem.getBlockId() == heldItem.getBlockId()
                                  && !gridItem.isFull() && !gridItem.hasDurability()));
                }
                if (canAdd) {
                    dragSlots.add(slot);
                }
            }

            // Check if we should cancel drag (button released or not enough items)
            if ((!leftDown && !dragIsRight) || (!rightDown && dragIsRight)) {
                finishDrag(inventory);
            }
        }
    }

    /**
     * Finish the current drag operation, distributing items.
     */
    public void finishDrag(Inventory inventory) {
        if (!isDragging || dragSlots.isEmpty() || heldItem == null || heldItem.isEmpty()) {
            cancelDrag();
            return;
        }

        if (dragSlots.size() <= 1) {
            // Single slot drag — not really a drag, cancel
            cancelDrag();
            return;
        }

        if (dragIsRight) {
            // Right-drag: place 1 item in each hovered slot
            int placed = 0;
            for (int slot : dragSlots) {
                if (heldItem == null || heldItem.isEmpty()) break;
                if (slot < Inventory.TOTAL_SIZE) {
                    Inventory.ItemStack slotItem = inventory.getSlot(slot);
                    if (slotItem == null || slotItem.isEmpty()) {
                        Inventory.ItemStack one = heldItem.copy();
                        one.setCount(1);
                        inventory.setSlot(slot, one);
                        heldItem.remove(1);
                        placed++;
                    } else if (slotItem.getBlockId() == heldItem.getBlockId()
                               && !slotItem.isFull() && !slotItem.hasDurability()) {
                        slotItem.add(1);
                        heldItem.remove(1);
                        placed++;
                    }
                } else if (slot >= GRID_SLOT_BASE && slot <= GRID_SLOT_BASE + 3) {
                    int gi = slot - GRID_SLOT_BASE;
                    Inventory.ItemStack gridItem = craftingGrid[gi];
                    if (gridItem == null || gridItem.isEmpty()) {
                        Inventory.ItemStack one = heldItem.copy();
                        one.setCount(1);
                        craftingGrid[gi] = one;
                        heldItem.remove(1);
                        placed++;
                    } else if (gridItem.getBlockId() == heldItem.getBlockId()
                               && !gridItem.isFull() && !gridItem.hasDurability()) {
                        gridItem.add(1);
                        heldItem.remove(1);
                        placed++;
                    }
                }
            }
            if (heldItem != null && heldItem.isEmpty()) heldItem = null;
        } else {
            // Left-drag: split held stack evenly across hovered slots
            int total = heldItem.getCount();
            int slots = dragSlots.size();
            int perSlot = total / slots;
            int remainder = total % slots;

            if (perSlot < 1) {
                cancelDrag();
                return;
            }

            for (int slot : dragSlots) {
                int amount = perSlot;
                if (slot < Inventory.TOTAL_SIZE) {
                    Inventory.ItemStack slotItem = inventory.getSlot(slot);
                    if (slotItem == null || slotItem.isEmpty()) {
                        Inventory.ItemStack split = heldItem.copy();
                        split.setCount(amount);
                        inventory.setSlot(slot, split);
                    } else if (slotItem.getBlockId() == heldItem.getBlockId()
                               && !slotItem.hasDurability()) {
                        slotItem.add(amount);
                    }
                } else if (slot >= GRID_SLOT_BASE && slot <= GRID_SLOT_BASE + 3) {
                    int gi = slot - GRID_SLOT_BASE;
                    Inventory.ItemStack gridItem = craftingGrid[gi];
                    if (gridItem == null || gridItem.isEmpty()) {
                        Inventory.ItemStack split = heldItem.copy();
                        split.setCount(amount);
                        craftingGrid[gi] = split;
                    } else if (gridItem.getBlockId() == heldItem.getBlockId()
                               && !gridItem.hasDurability()) {
                        gridItem.add(amount);
                    }
                }
            }
            heldItem.setCount(remainder);
            if (heldItem.isEmpty()) heldItem = null;
        }

        updateCraftingResult();
        cancelDrag();
    }

    private void cancelDrag() {
        isDragging = false;
        dragSlots.clear();
        dragOriginal = null;
    }

    // ================================================================
    // Left-click slot handlers (original)
    // ================================================================

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

        // Update hovered slot for tooltip
        hoveredSlot = getSlotAt(mouseX, mouseY);

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

        // Render drag highlights
        if (isDragging && !dragSlots.isEmpty()) {
            for (int slot : dragSlots) {
                float[] pos = getSlotPosition(slot);
                if (pos != null) {
                    fillRect(pos[0], pos[1], SLOT_SIZE, SLOT_SIZE, 1.0f, 1.0f, 1.0f, 0.15f);
                }
            }
        }

        // Render held item at cursor
        if (heldItem != null && !heldItem.isEmpty()) {
            uiShader.bind();
            glBindVertexArray(quadVao);
            renderItemPreview(mouseX - SLOT_SIZE / 2, mouseY - SLOT_SIZE / 2, heldItem);
        }

        uiShader.unbind();

        // ---- Text rendering ----
        if (font != null) {
            float titleY = invY0 + totalH + BG_PADDING + 4;
            font.drawText("Inventory", invX0, titleY, 2.0f, sw, sh, 0.9f, 0.9f, 0.9f, 1.0f);
            font.drawText("Crafting", craftX0, craftY0 + craftH + 4, 1.8f, sw, sh, 0.9f, 0.9f, 0.7f, 1.0f);
            renderInventoryText(inventory);
            renderCraftingGridText();
            renderResultText();

            // Held item count
            if (heldItem != null && !heldItem.isEmpty() && heldItem.getCount() > 1 && !heldItem.hasDurability()) {
                String cs = String.valueOf(heldItem.getCount());
                float textScale = 1.5f;
                float charW = 8 * textScale;
                float charH = 8 * textScale;
                float tx = mouseX - SLOT_SIZE / 2 + SLOT_SIZE - charW * cs.length() - 2;
                float ty = sh - (mouseY - SLOT_SIZE / 2) - charH - 2;
                font.drawText(cs, tx + 1, ty + 1, textScale, sw, sh, 0, 0, 0, 0.8f);
                font.drawText(cs, tx, ty, textScale, sw, sh, 1, 1, 1, 1);
            }
            if (heldItem != null && heldItem.hasDurability()) {
                String name = ToolItem.getDisplayName(heldItem.getBlockId());
                float nameY = sh - mouseY - 6;
                font.drawText(name, mouseX + SLOT_SIZE / 2 + 4, nameY, 1.5f, sw, sh, 1, 1, 0.7f, 1);
            }

            // ---- Tooltip ----
            renderTooltip(inventory);
        }
    }

    // ================================================================
    // Tooltip rendering
    // ================================================================

    private void renderTooltip(Inventory inventory) {
        if (hoveredSlot < 0) return;
        if (heldItem != null && !heldItem.isEmpty()) return; // Don't show tooltip when holding

        String tooltipText = null;

        if (hoveredSlot >= 0 && hoveredSlot < Inventory.TOTAL_SIZE) {
            Inventory.ItemStack stack = inventory.getSlot(hoveredSlot);
            if (stack != null && !stack.isEmpty()) {
                tooltipText = getItemDisplayName(stack);
            }
        } else if (hoveredSlot >= GRID_SLOT_BASE && hoveredSlot <= GRID_SLOT_BASE + 3) {
            int gi = hoveredSlot - GRID_SLOT_BASE;
            if (craftingGrid[gi] != null && !craftingGrid[gi].isEmpty()) {
                tooltipText = getItemDisplayName(craftingGrid[gi]);
            }
        } else if (hoveredSlot == RESULT_SLOT && currentRecipe != null) {
            int outputId = currentRecipe.getOutputId();
            tooltipText = getItemDisplayName(outputId);
        }

        if (tooltipText == null) return;

        // Render tooltip background + text near cursor
        float textScale = 1.5f;
        float charW = 8 * textScale;
        float charH = 8 * textScale;
        float textW = tooltipText.length() * charW;
        float tooltipPadding = 4.0f;
        float ttW = textW + tooltipPadding * 2;
        float ttH = charH + tooltipPadding * 2;

        // Position tooltip above cursor
        float ttX = mouseX + 12;
        float ttY = mouseY + 16;

        // Clamp to screen
        if (ttX + ttW > sw) ttX = sw - ttW - 4;
        if (ttY + ttH > sh) ttY = mouseY - ttH - 4;

        // Draw background
        uiShader.bind();
        glBindVertexArray(quadVao);
        fillRect(ttX - 1, ttY - 1, ttW + 2, ttH + 2, 0.1f, 0.0f, 0.2f, 0.9f);
        fillRect(ttX, ttY, ttW, ttH, 0.12f, 0.06f, 0.18f, 0.95f);
        uiShader.unbind();

        // Draw text (convert OpenGL Y-up to screen Y-down)
        float screenTextY = sh - (ttY + tooltipPadding) - charH;
        font.drawText(tooltipText, ttX + tooltipPadding, screenTextY, textScale,
                      sw, sh, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private String getItemDisplayName(Inventory.ItemStack stack) {
        return getItemDisplayName(stack.getBlockId());
    }

    private String getItemDisplayName(int blockId) {
        if (ToolItem.isTool(blockId)) {
            return ToolItem.getDisplayName(blockId);
        }
        Block block = Blocks.get(blockId);
        String name = block.name();
        // Capitalize and prettify
        return prettifyName(name);
    }

    private String prettifyName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "Unknown";
        String[] parts = rawName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    // ================================================================
    // Slot position helper
    // ================================================================

    private float[] getSlotPosition(int slot) {
        computeLayout();
        if (slot >= 0 && slot < Inventory.TOTAL_SIZE) {
            int row, col;
            if (slot < 9) {
                row = 0;
                col = slot;
            } else {
                row = 1 + (slot - 9) / 9;
                col = (slot - 9) % 9;
            }
            float sx = invX0 + col * (SLOT_SIZE + SLOT_GAP);
            float sy;
            if (row == 0) sy = invY0;
            else sy = invY0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
            return new float[]{sx, sy};
        } else if (slot >= GRID_SLOT_BASE && slot <= GRID_SLOT_BASE + 3) {
            int gi = slot - GRID_SLOT_BASE;
            int row = gi / 2;
            int col = gi % 2;
            float sx = craftX0 + col * (SLOT_SIZE + SLOT_GAP);
            float sy = craftY0 + (1 - row) * (SLOT_SIZE + SLOT_GAP);
            return new float[]{sx, sy};
        } else if (slot == RESULT_SLOT) {
            return new float[]{resultX, resultY};
        }
        return null;
    }

    // ================================================================
    // Rendering helpers
    // ================================================================

    private void renderInventorySlots(Inventory inventory) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = invX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;
                if (row == 0) sy = invY0;
                else sy = invY0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
                int slot = (row == 0) ? col : (9 + (row - 1) * 9 + col);

                uiShader.bind();
                glBindVertexArray(quadVao);
                fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.2f, 0.2f, 0.2f, 0.8f);

                Inventory.ItemStack stack = inventory.getSlot(slot);
                if (stack != null && !stack.isEmpty()) renderItemPreview(sx, sy, stack);

                uiShader.bind();
                glBindVertexArray(quadVao);
                strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, BORDER, 0.4f, 0.4f, 0.4f, 0.7f);
            }
        }
    }

    private void renderCraftingGrid() {
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                float sx = craftX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy = craftY0 + (1 - row) * (SLOT_SIZE + SLOT_GAP);

                uiShader.bind();
                glBindVertexArray(quadVao);
                fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.22f, 0.20f, 0.18f, 0.8f);

                int gridIdx = row * 2 + col;
                if (craftingGrid[gridIdx] != null && !craftingGrid[gridIdx].isEmpty())
                    renderItemPreview(sx, sy, craftingGrid[gridIdx]);

                uiShader.bind();
                glBindVertexArray(quadVao);
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

        uiShader.bind();
        glBindVertexArray(quadVao);
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

        uiShader.bind();
        glBindVertexArray(quadVao);
        float bR = 0.7f, bG = 0.6f, bB = 0.2f;
        if (currentRecipe != null) { bR = 1.0f; bG = 0.85f; bB = 0.3f; }
        strokeRect(resultX, resultY, SLOT_SIZE, SLOT_SIZE, SELECTED_BORDER, bR, bG, bB, 0.9f);
    }

    /**
     * Render an item preview — uses atlas texture when available, falls back to colored square.
     */
    private void renderItemPreview(float sx, float sy, Inventory.ItemStack stack) {
        int bid = stack.getBlockId();
        float off = (SLOT_SIZE - PREVIEW_SIZE) / 2f;

        Block block = Blocks.get(bid);
        int tileIndex = block.getTextureIndex(0);
        
        // ALWAYS render with texture if atlas exists and tileIndex >= 0
        if (atlas != null && tileIndex >= 0) {
            float[] uv = atlas.getUV(tileIndex);
            
            float px = sx + off;
            float py = sy + off;
            
            texShader.bind();
            glBindVertexArray(quadVao);
            glActiveTexture(GL_TEXTURE0);
            atlas.bind(0);
            texShader.setInt("uTexture", 0);
            texShader.setVec4("uUVRect", uv[0], uv[3], uv[2], uv[1]);
            setProjectionTex(new Matrix4f().ortho(
                -px / PREVIEW_SIZE, (sw - px) / PREVIEW_SIZE,
                -py / PREVIEW_SIZE, (sh - py) / PREVIEW_SIZE,
                -1, 1));
            glDrawArrays(GL_TRIANGLES, 0, 6);
            texShader.unbind();
            
            // Draw durability bar on top if applicable
            if (stack.hasDurability()) {
                uiShader.bind();
                glBindVertexArray(quadVao);
                float durFrac = stack.getDurabilityFraction();
                if (durFrac >= 0 && durFrac < 1.0f) {
                    fillRect(sx + 2, sy + 2, SLOT_SIZE - 4, 3, 0.1f, 0.1f, 0.1f, 0.7f);
                    float r = durFrac < 0.5f ? 1.0f : durFrac * 2 - 1;
                    float g = durFrac > 0.5f ? 1.0f : durFrac * 2;
                    fillRect(sx + 2, sy + 2, (SLOT_SIZE - 4) * durFrac, 3, r, g, 0.2f, 0.9f);
                }
            }
        } else {
            // Fallback colored square (should rarely happen)
            uiShader.bind();
            glBindVertexArray(quadVao);
            if (bid > 0 && bid < BLOCK_COLORS.length) {
                float[] c = BLOCK_COLORS[bid];
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
        float textScale = 1.5f;
        float charW = 8 * textScale;
        float charH = 8 * textScale;
        float tx = sx + SLOT_SIZE - charW * cs.length() - 2;
        float ty = sh - sy - charH - 2;
        font.drawText(cs, tx + 1, ty + 1, textScale, sw, sh, 0, 0, 0, 0.8f);
        font.drawText(cs, tx, ty, textScale, sw, sh, 1, 1, 1, 1);
    }

    // ================================================================
    // GL drawing helpers
    // ================================================================

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

    private void setProjectionTex(Matrix4f proj) {
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(16);
            proj.get(fb);
            glUniformMatrix4fv(
                glGetUniformLocation(texShader.getProgramId(), "uProjection"),
                false, fb);
        }
    }

    public void cleanup() {
        if (quadVbo != 0) glDeleteBuffers(quadVbo);
        if (quadVao != 0) glDeleteVertexArrays(quadVao);
        if (uiShader != null) uiShader.cleanup();
        if (texShader != null) texShader.cleanup();
    }
}
