package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import com.voxelgame.render.TextureAtlas;
import com.voxelgame.sim.Inventory;
import com.voxelgame.sim.ItemRegistry;
import com.voxelgame.sim.ToolItem;
import com.voxelgame.world.Block;
import com.voxelgame.world.Blocks;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Creative mode inventory screen.
 * 
 * Layout (bottom to top in GL coordinates):
 *   - Player hotbar (9 slots) — bottom row
 *   - Player storage (27 slots, 3 rows) — above hotbar with gap
 *   - Category tabs — above player inventory
 *   - Creative item grid (scrollable, 9 columns) — fills remaining space
 * 
 * Click an item in the creative grid → adds a full stack (64) to inventory.
 * Click an inventory slot → pick up / put down (standard behavior).
 * Scroll wheel in the creative grid area → scroll items.
 */
public class CreativeInventoryScreen {

    // Layout constants
    private static final float SLOT_SIZE = 40.0f;
    private static final float SLOT_GAP = 4.0f;
    private static final float ROW_GAP = 8.0f;
    private static final float PREVIEW_SIZE = 28.0f;
    private static final float BORDER = 2.0f;
    private static final float BG_PADDING = 16.0f;
    private static final float TAB_HEIGHT = 28.0f;
    private static final float TAB_GAP = 2.0f;
    private static final float SECTION_GAP = 12.0f;

    // Grid config
    private static final int GRID_COLS = 9;
    private static final int VISIBLE_ROWS = 5; // visible rows in creative grid

    // Block colors (copied from InventoryScreen for consistency)
    private static final float[][] BLOCK_COLORS = {
        {0.0f, 0.0f, 0.0f, 0.0f},       // 0  AIR
        {0.47f, 0.47f, 0.47f, 1.0f},     // 1  STONE
        {0.39f, 0.39f, 0.39f, 1.0f},     // 2  COBBLESTONE
        {0.53f, 0.38f, 0.26f, 1.0f},     // 3  DIRT
        {0.30f, 0.60f, 0.00f, 1.0f},     // 4  GRASS
        {0.84f, 0.81f, 0.60f, 1.0f},     // 5  SAND
        {0.51f, 0.49f, 0.49f, 1.0f},     // 6  GRAVEL
        {0.39f, 0.27f, 0.16f, 1.0f},     // 7  LOG
        {0.20f, 0.51f, 0.04f, 0.9f},     // 8  LEAVES
        {0.12f, 0.31f, 0.78f, 0.6f},     // 9  WATER
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

    // State
    private boolean visible = false;
    private Inventory.ItemStack heldItem = null;
    private ItemRegistry.Category selectedCategory = ItemRegistry.Category.ALL;
    private int scrollOffset = 0; // row offset for creative grid
    private int hoveredSlot = -1;           // player inventory slot
    private int hoveredCreativeIndex = -1;  // creative grid index
    private int hoveredTabIndex = -1;       // tab index

    // GL resources
    private Shader uiShader;
    private Shader texShader;
    private int quadVao, quadVbo;
    private BitmapFont font;
    private TextureAtlas atlas;
    private int sw, sh;
    private float mouseX, mouseY;

    // Computed layout positions
    private float invX0, invY0;       // player inventory origin (bottom-left of hotbar)
    private float gridX0, gridY0;     // creative grid origin
    private float tabX0, tabY0;       // tab bar origin
    private float totalWidth;

    // ================================================================
    // Initialization
    // ================================================================

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

    // ================================================================
    // Visibility
    // ================================================================

    public boolean isVisible() { return visible; }
    public boolean isOpen() { return visible; }

    public void setVisible(boolean v) {
        visible = v;
        if (!v) {
            heldItem = null;
            scrollOffset = 0;
        }
    }

    public void toggle() {
        visible = !visible;
        if (!visible) {
            heldItem = null;
            scrollOffset = 0;
        }
    }

    public void close(Inventory inventory) {
        visible = false;
        // Return held item to inventory
        if (heldItem != null) {
            inventory.addItemStack(heldItem);
            heldItem = null;
        }
        scrollOffset = 0;
    }

    public void close() {
        visible = false;
        heldItem = null;
        scrollOffset = 0;
    }

    // ================================================================
    // Mouse input
    // ================================================================

    public void updateMouse(double mx, double my, int screenH) {
        this.mouseX = (float) mx;
        this.mouseY = screenH - (float) my; // flip to GL coords
    }

    /** Handle mouse scroll (for creative grid scrolling). */
    public void handleScroll(double scrollY) {
        if (!visible) return;
        List<ItemRegistry.Entry> items = ItemRegistry.getItems(selectedCategory);
        int totalRows = (items.size() + GRID_COLS - 1) / GRID_COLS;
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);

        scrollOffset -= (int) Math.signum(scrollY);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
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

        // Check tab clicks
        int tabIdx = getTabAt(clickX, clickY);
        if (tabIdx >= 0) {
            List<ItemRegistry.Category> cats = ItemRegistry.getCategories();
            if (tabIdx < cats.size()) {
                selectedCategory = cats.get(tabIdx);
                scrollOffset = 0;
            }
            return;
        }

        // Check creative grid clicks
        int creativeIdx = getCreativeSlotAt(clickX, clickY);
        if (creativeIdx >= 0) {
            List<ItemRegistry.Entry> items = ItemRegistry.getItems(selectedCategory);
            if (creativeIdx < items.size()) {
                ItemRegistry.Entry entry = items.get(creativeIdx);
                addCreativeItemToInventory(inventory, entry.blockId());
            }
            return;
        }

        // Check player inventory clicks
        int invSlot = getInventorySlotAt(clickX, clickY);
        if (invSlot >= 0 && invSlot < Inventory.TOTAL_SIZE) {
            if (shiftHeld) {
                // Shift-click: move between hotbar/storage
                handleShiftClick(inventory, invSlot);
            } else {
                handleInventorySlotClick(inventory, invSlot);
            }
        }
    }

    public void handleRightClick(Inventory inventory, double mx, double my,
                                  int screenW, int screenH) {
        if (!visible) return;
        this.sw = screenW;
        this.sh = screenH;
        float clickX = (float) mx;
        float clickY = screenH - (float) my;
        computeLayout();

        // Right-click creative grid → add single item
        int creativeIdx = getCreativeSlotAt(clickX, clickY);
        if (creativeIdx >= 0) {
            List<ItemRegistry.Entry> items = ItemRegistry.getItems(selectedCategory);
            if (creativeIdx < items.size()) {
                ItemRegistry.Entry entry = items.get(creativeIdx);
                // Add just 1 item
                if (heldItem == null) {
                    if (ToolItem.isTool(entry.blockId())) {
                        int maxDur = ToolItem.getMaxDurability(entry.blockId());
                        heldItem = new Inventory.ItemStack(entry.blockId(), maxDur, maxDur);
                    } else {
                        heldItem = new Inventory.ItemStack(entry.blockId(), 1);
                    }
                } else if (heldItem.getBlockId() == entry.blockId() && !heldItem.hasDurability()) {
                    heldItem.add(1);
                }
            }
            return;
        }

        // Right-click inventory slot
        int invSlot = getInventorySlotAt(clickX, clickY);
        if (invSlot >= 0 && invSlot < Inventory.TOTAL_SIZE) {
            handleInventorySlotRightClick(inventory, invSlot);
        }
    }

    // ================================================================
    // Inventory slot interaction (standard pick up / put down)
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
                // Swap
                inventory.setSlot(slot, heldItem);
                heldItem = slotItem;
            }
        }
    }

    private void handleInventorySlotRightClick(Inventory inventory, int slot) {
        Inventory.ItemStack slotItem = inventory.getSlot(slot);
        if (heldItem == null) {
            if (slotItem != null && !slotItem.isEmpty()) {
                int total = slotItem.getCount();
                int takeAmount = (total + 1) / 2;
                heldItem = slotItem.copy();
                heldItem.setCount(takeAmount);
                slotItem.setCount(total - takeAmount);
                if (slotItem.isEmpty()) inventory.setSlot(slot, null);
            }
        } else {
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

    private void handleShiftClick(Inventory inventory, int slot) {
        Inventory.ItemStack slotItem = inventory.getSlot(slot);
        if (slotItem == null || slotItem.isEmpty()) return;

        boolean isHotbar = slot < Inventory.HOTBAR_SIZE;
        if (isHotbar) {
            moveToRange(inventory, slot, Inventory.HOTBAR_SIZE, Inventory.TOTAL_SIZE);
        } else {
            moveToRange(inventory, slot, 0, Inventory.HOTBAR_SIZE);
        }
    }

    private void moveToRange(Inventory inventory, int sourceSlot, int destStart, int destEnd) {
        Inventory.ItemStack source = inventory.getSlot(sourceSlot);
        if (source == null || source.isEmpty()) return;

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
    // Creative item insertion
    // ================================================================

    private void addCreativeItemToInventory(Inventory inventory, int blockId) {
        if (heldItem != null) {
            // If holding something, replace with this item
            if (ToolItem.isTool(blockId)) {
                int maxDur = ToolItem.getMaxDurability(blockId);
                heldItem = new Inventory.ItemStack(blockId, maxDur, maxDur);
            } else {
                heldItem = new Inventory.ItemStack(blockId, 64);
            }
        } else {
            // Pick up a full stack
            if (ToolItem.isTool(blockId)) {
                int maxDur = ToolItem.getMaxDurability(blockId);
                heldItem = new Inventory.ItemStack(blockId, maxDur, maxDur);
            } else {
                heldItem = new Inventory.ItemStack(blockId, 64);
            }
        }
    }

    // ================================================================
    // Layout computation
    // ================================================================

    private void computeLayout() {
        totalWidth = GRID_COLS * SLOT_SIZE + (GRID_COLS - 1) * SLOT_GAP;

        // Player inventory: hotbar at bottom, then 3 rows of storage above
        float invH = 4 * SLOT_SIZE + 3 * SLOT_GAP + ROW_GAP;

        // Creative grid
        float creativeH = VISIBLE_ROWS * SLOT_SIZE + (VISIBLE_ROWS - 1) * SLOT_GAP;

        // Total height: inventory + gap + tabs + gap + creative grid
        float totalH = invH + SECTION_GAP + TAB_HEIGHT + SECTION_GAP + creativeH;

        // Center horizontally
        invX0 = (sw - totalWidth) / 2.0f;

        // Position from bottom
        invY0 = (sh - totalH) / 2.0f;

        // Tabs above inventory
        tabX0 = invX0;
        tabY0 = invY0 + invH + SECTION_GAP;

        // Creative grid above tabs
        gridX0 = invX0;
        gridY0 = tabY0 + TAB_HEIGHT + SECTION_GAP;
    }

    // ================================================================
    // Hit testing
    // ================================================================

    private int getInventorySlotAt(float mx, float my) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = invX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;
                if (row == 0) sy = invY0; // hotbar
                else sy = invY0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);

                if (mx >= sx && mx <= sx + SLOT_SIZE && my >= sy && my <= sy + SLOT_SIZE) {
                    return (row == 0) ? col : (9 + (row - 1) * 9 + col);
                }
            }
        }
        return -1;
    }

    private int getCreativeSlotAt(float mx, float my) {
        List<ItemRegistry.Entry> items = ItemRegistry.getItems(selectedCategory);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                float sx = gridX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy = gridY0 + (VISIBLE_ROWS - 1 - row) * (SLOT_SIZE + SLOT_GAP);

                if (mx >= sx && mx <= sx + SLOT_SIZE && my >= sy && my <= sy + SLOT_SIZE) {
                    int itemIndex = (scrollOffset + row) * GRID_COLS + col;
                    if (itemIndex >= 0 && itemIndex < items.size()) {
                        return itemIndex;
                    }
                    return -1;
                }
            }
        }
        return -1;
    }

    private int getTabAt(float mx, float my) {
        List<ItemRegistry.Category> cats = ItemRegistry.getCategories();
        float tx = tabX0;
        for (int i = 0; i < cats.size(); i++) {
            String label = cats.get(i).getDisplayName();
            float tabW = Math.max(40, label.length() * 8 * 1.3f + 12);
            if (mx >= tx && mx <= tx + tabW && my >= tabY0 && my <= tabY0 + TAB_HEIGHT) {
                return i;
            }
            tx += tabW + TAB_GAP;
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

        // Update hover state
        hoveredSlot = getInventorySlotAt(mouseX, mouseY);
        hoveredCreativeIndex = getCreativeSlotAt(mouseX, mouseY);
        hoveredTabIndex = getTabAt(mouseX, mouseY);

        uiShader.bind();
        glBindVertexArray(quadVao);

        // Full-screen dim
        fillRect(0, 0, sw, sh, 0.0f, 0.0f, 0.0f, 0.65f);

        // Background panel
        float invH = 4 * SLOT_SIZE + 3 * SLOT_GAP + ROW_GAP;
        float creativeH = VISIBLE_ROWS * SLOT_SIZE + (VISIBLE_ROWS - 1) * SLOT_GAP;
        float totalH = invH + SECTION_GAP + TAB_HEIGHT + SECTION_GAP + creativeH;

        fillRect(invX0 - BG_PADDING, invY0 - BG_PADDING,
                 totalWidth + BG_PADDING * 2, totalH + BG_PADDING * 2,
                 0.12f, 0.12f, 0.14f, 0.92f);

        // Render creative grid
        renderCreativeGrid();

        // Render tabs
        renderTabs();

        // Render scrollbar
        renderScrollbar();

        // Render player inventory
        renderInventorySlots(inventory);

        // Render held item at cursor
        if (heldItem != null && !heldItem.isEmpty()) {
            uiShader.bind();
            glBindVertexArray(quadVao);
            renderItemPreview(mouseX - SLOT_SIZE / 2, mouseY - SLOT_SIZE / 2, heldItem);
        }

        uiShader.unbind();

        // ---- Text rendering ----
        if (font != null) {
            float creativeTop = gridY0 + VISIBLE_ROWS * (SLOT_SIZE + SLOT_GAP);
            font.drawText("Creative Inventory", invX0,
                          sh - creativeTop - 16, 2.0f, sw, sh, 0.9f, 0.9f, 0.6f, 1.0f);

            // Inventory title
            float invTop = invY0 + 4 * SLOT_SIZE + 3 * SLOT_GAP + ROW_GAP;
            font.drawText("Inventory", invX0,
                          sh - invTop + 2, 1.6f, sw, sh, 0.7f, 0.7f, 0.7f, 1.0f);

            // Item counts in player inventory
            renderInventoryText(inventory);

            // Item counts in creative grid
            renderCreativeGridText();

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

            // Tooltip
            renderTooltip(inventory);
        }
    }

    // ================================================================
    // Section renderers
    // ================================================================

    private void renderCreativeGrid() {
        List<ItemRegistry.Entry> items = ItemRegistry.getItems(selectedCategory);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                float sx = gridX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy = gridY0 + (VISIBLE_ROWS - 1 - row) * (SLOT_SIZE + SLOT_GAP);

                int itemIndex = (scrollOffset + row) * GRID_COLS + col;

                // Slot background
                uiShader.bind();
                glBindVertexArray(quadVao);

                boolean hasItem = itemIndex >= 0 && itemIndex < items.size();
                boolean hovered = hasItem && (hoveredCreativeIndex == itemIndex);

                if (hovered) {
                    fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.30f, 0.30f, 0.35f, 0.9f);
                } else {
                    fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.18f, 0.18f, 0.20f, 0.8f);
                }

                if (hasItem) {
                    ItemRegistry.Entry entry = items.get(itemIndex);
                    Inventory.ItemStack previewStack;
                    if (ToolItem.isTool(entry.blockId())) {
                        int maxDur = ToolItem.getMaxDurability(entry.blockId());
                        previewStack = new Inventory.ItemStack(entry.blockId(), maxDur, maxDur);
                    } else {
                        previewStack = new Inventory.ItemStack(entry.blockId(), 1);
                    }
                    renderItemPreview(sx, sy, previewStack);
                }

                // Border
                uiShader.bind();
                glBindVertexArray(quadVao);
                if (hovered) {
                    strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, BORDER, 0.6f, 0.8f, 0.6f, 0.9f);
                } else {
                    strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 1, 0.3f, 0.3f, 0.3f, 0.5f);
                }
            }
        }
    }

    private void renderTabs() {
        List<ItemRegistry.Category> cats = ItemRegistry.getCategories();
        float tx = tabX0;

        uiShader.bind();
        glBindVertexArray(quadVao);

        for (int i = 0; i < cats.size(); i++) {
            ItemRegistry.Category cat = cats.get(i);
            String label = cat.getDisplayName();
            float tabW = Math.max(40, label.length() * 8 * 1.3f + 12);

            boolean selected = (cat == selectedCategory);
            boolean hovered = (hoveredTabIndex == i);

            // Tab background
            if (selected) {
                fillRect(tx, tabY0, tabW, TAB_HEIGHT, 0.25f, 0.35f, 0.25f, 0.95f);
            } else if (hovered) {
                fillRect(tx, tabY0, tabW, TAB_HEIGHT, 0.22f, 0.22f, 0.28f, 0.9f);
            } else {
                fillRect(tx, tabY0, tabW, TAB_HEIGHT, 0.16f, 0.16f, 0.18f, 0.85f);
            }

            // Tab border
            float bR = selected ? 0.5f : 0.3f;
            float bG = selected ? 0.7f : 0.3f;
            float bB = selected ? 0.5f : 0.3f;
            strokeRect(tx, tabY0, tabW, TAB_HEIGHT, 1, bR, bG, bB, 0.8f);

            // Tab text
            uiShader.unbind();
            float textScale = 1.3f;
            float charW = 8 * textScale;
            float textX = tx + (tabW - label.length() * charW) / 2;
            float textY = sh - tabY0 - (TAB_HEIGHT + 8 * textScale) / 2;
            float textR = selected ? 1.0f : 0.7f;
            float textG = selected ? 1.0f : 0.7f;
            float textB = selected ? 0.8f : 0.7f;
            font.drawText(label, textX, textY, textScale, sw, sh, textR, textG, textB, 1.0f);
            uiShader.bind();
            glBindVertexArray(quadVao);

            tx += tabW + TAB_GAP;
        }
    }

    private void renderScrollbar() {
        List<ItemRegistry.Entry> items = ItemRegistry.getItems(selectedCategory);
        int totalRows = (items.size() + GRID_COLS - 1) / GRID_COLS;
        if (totalRows <= VISIBLE_ROWS) return; // no scroll needed

        float sbX = gridX0 + totalWidth + 4;
        float creativeH = VISIBLE_ROWS * SLOT_SIZE + (VISIBLE_ROWS - 1) * SLOT_GAP;
        float sbW = 6;

        // Track
        uiShader.bind();
        glBindVertexArray(quadVao);
        fillRect(sbX, gridY0, sbW, creativeH, 0.15f, 0.15f, 0.15f, 0.6f);

        // Thumb
        float thumbFraction = (float) VISIBLE_ROWS / totalRows;
        float thumbH = Math.max(20, creativeH * thumbFraction);
        float maxScroll = totalRows - VISIBLE_ROWS;
        float thumbOffset = (maxScroll > 0) ? (float) scrollOffset / maxScroll * (creativeH - thumbH) : 0;
        // Invert Y: scrollOffset 0 = top, which should be thumb at top (high Y)
        float thumbY = gridY0 + creativeH - thumbH - thumbOffset;

        fillRect(sbX, thumbY, sbW, thumbH, 0.5f, 0.5f, 0.6f, 0.8f);
    }

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

                boolean hovered = (hoveredSlot == slot);
                if (hovered) {
                    fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.28f, 0.28f, 0.30f, 0.9f);
                } else {
                    fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.2f, 0.2f, 0.2f, 0.8f);
                }

                Inventory.ItemStack stack = inventory.getSlot(slot);
                if (stack != null && !stack.isEmpty()) renderItemPreview(sx, sy, stack);

                uiShader.bind();
                glBindVertexArray(quadVao);
                if (row == 0) {
                    // Hotbar slots get a slightly brighter border
                    strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, BORDER, 0.5f, 0.5f, 0.4f, 0.8f);
                } else {
                    strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, BORDER, 0.4f, 0.4f, 0.4f, 0.7f);
                }
            }
        }
    }

    // ================================================================
    // Text rendering
    // ================================================================

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

    private void renderCreativeGridText() {
        // Creative grid items show no count (they're infinite)
        // But we could show "64" if desired. Keeping it clean.
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
    // Tooltip
    // ================================================================

    private void renderTooltip(Inventory inventory) {
        if (heldItem != null && !heldItem.isEmpty()) return;

        String tooltipText = null;

        if (hoveredSlot >= 0 && hoveredSlot < Inventory.TOTAL_SIZE) {
            Inventory.ItemStack stack = inventory.getSlot(hoveredSlot);
            if (stack != null && !stack.isEmpty()) {
                tooltipText = ItemRegistry.getDisplayName(stack.getBlockId());
            }
        } else if (hoveredCreativeIndex >= 0) {
            List<ItemRegistry.Entry> items = ItemRegistry.getItems(selectedCategory);
            if (hoveredCreativeIndex < items.size()) {
                tooltipText = items.get(hoveredCreativeIndex).displayName();
            }
        }

        if (tooltipText == null) return;

        float textScale = 1.5f;
        float charW = 8 * textScale;
        float charH = 8 * textScale;
        float textW = tooltipText.length() * charW;
        float tooltipPadding = 4.0f;
        float ttW = textW + tooltipPadding * 2;
        float ttH = charH + tooltipPadding * 2;

        float ttX = mouseX + 12;
        float ttY = mouseY + 16;

        if (ttX + ttW > sw) ttX = sw - ttW - 4;
        if (ttY + ttH > sh) ttY = mouseY - ttH - 4;

        // Background
        uiShader.bind();
        glBindVertexArray(quadVao);
        fillRect(ttX - 1, ttY - 1, ttW + 2, ttH + 2, 0.08f, 0.0f, 0.15f, 0.9f);
        fillRect(ttX, ttY, ttW, ttH, 0.10f, 0.05f, 0.16f, 0.95f);
        uiShader.unbind();

        // Text
        float screenTextY = sh - (ttY + tooltipPadding) - charH;
        font.drawText(tooltipText, ttX + tooltipPadding, screenTextY, textScale,
                      sw, sh, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ================================================================
    // Item preview rendering
    // ================================================================

    private void renderItemPreview(float sx, float sy, Inventory.ItemStack stack) {
        int bid = stack.getBlockId();
        float off = (SLOT_SIZE - PREVIEW_SIZE) / 2f;

        if (stack.hasDurability()) {
            // Tool rendering (icon shape)
            uiShader.bind();
            glBindVertexArray(quadVao);
            if (bid > 0 && bid < BLOCK_COLORS.length) {
                float[] c = BLOCK_COLORS[bid];
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
            }
            return;
        }

        Block block = Blocks.get(bid);
        int tileIndex = block.getTextureIndex(0);
        
        boolean shouldRenderTexture = atlas != null && tileIndex > 0;

        if (shouldRenderTexture) {
            float[] uv = atlas.getUV(tileIndex);
            texShader.bind();
            glBindVertexArray(quadVao);
            atlas.bind(0);
            texShader.setInt("uTexture", 0);
            // Flip V to fix GL y-axis (flame on top for torches, etc.)
            texShader.setVec4("uUVRect", uv[0], uv[3], uv[2], uv[1]);
            setProjectionTex(new Matrix4f().ortho(
                -(sx + off) / PREVIEW_SIZE, (sw - sx - off) / PREVIEW_SIZE,
                -(sy + off) / PREVIEW_SIZE, (sh - sy - off) / PREVIEW_SIZE,
                -1, 1));
            glDrawArrays(GL_TRIANGLES, 0, 6);
            texShader.unbind();
            // Re-bind uiShader for subsequent rendering
            uiShader.bind();
            glBindVertexArray(quadVao);
        } else {
            uiShader.bind();
            glBindVertexArray(quadVao);
            if (bid > 0 && bid < BLOCK_COLORS.length) {
                float[] c = BLOCK_COLORS[bid];
                fillRect(sx + off, sy + off, PREVIEW_SIZE, PREVIEW_SIZE, c[0], c[1], c[2], c[3]);
            }
        }
    }

    // ================================================================
    // GL helpers
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
