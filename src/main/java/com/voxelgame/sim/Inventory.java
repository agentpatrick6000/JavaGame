package com.voxelgame.sim;

/**
 * Player inventory with 36 slots: 9 hotbar (indices 0-8) + 27 storage (9-35).
 * Each slot holds an ItemStack (block ID + count) or is empty (null).
 * Max stack size is 64 for all block types.
 */
public class Inventory {

    public static final int HOTBAR_SIZE = 9;
    public static final int STORAGE_SIZE = 27;
    public static final int TOTAL_SIZE = HOTBAR_SIZE + STORAGE_SIZE; // 36
    /** Alias for TOTAL_SIZE (used by DebugOverlay). */
    public static final int TOTAL_SLOTS = TOTAL_SIZE;
    public static final int MAX_STACK = 64;

    /**
     * An item stack: block ID + count + optional durability (for tools).
     * Tools are unstackable (max count = 1).
     */
    public static class ItemStack {
        private int blockId;
        private int count;
        private int durability = -1;    // -1 = not a tool
        private int maxDurability = -1;

        public ItemStack(int blockId, int count) {
            this.blockId = blockId;
            this.count = Math.min(count, MAX_STACK);
        }

        /** Create a tool item stack with durability. */
        public ItemStack(int blockId, int durability, int maxDurability) {
            this.blockId = blockId;
            this.count = 1;
            this.durability = durability;
            this.maxDurability = maxDurability;
        }

        public int getBlockId() { return blockId; }
        public int getCount() { return count; }
        public int getDurability() { return durability; }
        public int getMaxDurability() { return maxDurability; }

        public void setCount(int count) { this.count = Math.min(count, getMaxStack()); }
        public void setBlockId(int blockId) { this.blockId = blockId; }
        public void setDurability(int d) { this.durability = d; }
        public void setMaxDurability(int d) { this.maxDurability = d; }

        public boolean hasDurability() { return durability >= 0; }
        public int getMaxStack() { return hasDurability() ? 1 : MAX_STACK; }

        public float getDurabilityFraction() {
            if (maxDurability <= 0) return -1;
            return (float) durability / maxDurability;
        }

        public boolean damageTool(int amount) {
            if (durability < 0) return false;
            durability = Math.max(0, durability - amount);
            return durability <= 0;
        }

        /** Add to this stack. Returns leftover that didn't fit. */
        public int add(int amount) {
            int max = getMaxStack();
            int canFit = max - count;
            int added = Math.min(amount, canFit);
            count += added;
            return amount - added;
        }

        /** Remove from this stack. Returns amount actually removed. */
        public int remove(int amount) {
            int removed = Math.min(amount, count);
            count -= removed;
            return removed;
        }

        public boolean isFull() { return count >= getMaxStack(); }
        public boolean isEmpty() { return count <= 0; }

        public ItemStack copy() {
            ItemStack c = new ItemStack(blockId, count);
            c.durability = this.durability;
            c.maxDurability = this.maxDurability;
            return c;
        }

        @Override
        public String toString() {
            if (durability >= 0) return "ItemStack{" + blockId + " dur=" + durability + "/" + maxDurability + "}";
            return "ItemStack{" + blockId + " x" + count + "}";
        }
    }

    private final ItemStack[] slots = new ItemStack[TOTAL_SIZE];

    /**
     * Add an item to the inventory. Tries hotbar first, then storage.
     * Returns the count that couldn't be added (0 if all fit).
     */
    public int addItem(int blockId, int count) {
        if (blockId <= 0 || count <= 0) return 0;

        boolean isTool = ToolItem.isTool(blockId);
        int remaining = count;

        if (!isTool) {
            for (int i = 0; i < TOTAL_SIZE && remaining > 0; i++) {
                if (slots[i] != null && slots[i].getBlockId() == blockId
                        && !slots[i].isFull() && !slots[i].hasDurability()) {
                    remaining = slots[i].add(remaining);
                }
            }
        }

        for (int i = 0; i < TOTAL_SIZE && remaining > 0; i++) {
            if (slots[i] == null || slots[i].isEmpty()) {
                if (isTool) {
                    int maxDur = ToolItem.getMaxDurability(blockId);
                    slots[i] = new ItemStack(blockId, maxDur, maxDur);
                    remaining--;
                } else {
                    int toPlace = Math.min(remaining, MAX_STACK);
                    slots[i] = new ItemStack(blockId, toPlace);
                    remaining -= toPlace;
                }
            }
        }

        return remaining;
    }

    /** Add a pre-built ItemStack (preserves durability for tools). */
    public int addItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.hasDurability()) {
            for (int i = 0; i < TOTAL_SIZE; i++) {
                if (slots[i] == null || slots[i].isEmpty()) {
                    slots[i] = stack.copy();
                    return 0;
                }
            }
            return 1;
        } else {
            return addItem(stack.getBlockId(), stack.getCount());
        }
    }

    /**
     * Remove items from the inventory. Searches all slots.
     * Returns the count actually removed.
     */
    public int removeItem(int blockId, int count) {
        if (blockId <= 0 || count <= 0) return 0;

        int remaining = count;

        for (int i = TOTAL_SIZE - 1; i >= 0 && remaining > 0; i--) {
            if (slots[i] != null && slots[i].getBlockId() == blockId) {
                int removed = slots[i].remove(remaining);
                remaining -= removed;
                if (slots[i].isEmpty()) {
                    slots[i] = null;
                }
            }
        }

        return count - remaining;
    }

    /**
     * Get the stack at a slot index (may be null for empty).
     */
    public ItemStack getSlot(int index) {
        if (index < 0 || index >= TOTAL_SIZE) return null;
        return slots[index];
    }

    /**
     * Set a slot directly (used for drag-and-drop).
     */
    public void setSlot(int index, ItemStack stack) {
        if (index < 0 || index >= TOTAL_SIZE) return;
        slots[index] = stack;
    }

    /**
     * Swap two slots (for drag-and-drop).
     */
    public void swapSlots(int a, int b) {
        if (a < 0 || a >= TOTAL_SIZE || b < 0 || b >= TOTAL_SIZE) return;
        ItemStack temp = slots[a];
        slots[a] = slots[b];
        slots[b] = temp;
    }

    /**
     * Get the block ID in a hotbar slot (0-8).
     * Returns 0 (AIR) if empty.
     */
    public int getHotbarBlockId(int slot) {
        if (slot < 0 || slot >= HOTBAR_SIZE) return 0;
        return slots[slot] != null ? slots[slot].getBlockId() : 0;
    }

    /**
     * Get total count of a specific block type across all slots.
     */
    public int countItem(int blockId) {
        int total = 0;
        for (ItemStack slot : slots) {
            if (slot != null && slot.getBlockId() == blockId) {
                total += slot.getCount();
            }
        }
        return total;
    }

    /**
     * Check if inventory has at least one empty slot.
     */
    public boolean hasSpace() {
        for (ItemStack slot : slots) {
            if (slot == null || slot.isEmpty()) return true;
        }
        return false;
    }

    /**
     * Check if a specific item can be added (has space or matching partial stack).
     */
    public boolean canAdd(int blockId) {
        for (ItemStack slot : slots) {
            if (slot == null || slot.isEmpty()) return true;
            if (slot.getBlockId() == blockId && !slot.isFull()) return true;
        }
        return false;
    }

    /**
     * Clear all slots.
     */
    public void clear() {
        for (int i = 0; i < TOTAL_SIZE; i++) {
            slots[i] = null;
        }
    }

    /**
     * Get the raw slots array (for rendering).
     */
    public ItemStack[] getSlots() {
        return slots;
    }

    // ---- Convenience methods for UI rendering ----

    /**
     * Check if a slot is empty.
     */
    public boolean isEmpty(int index) {
        if (index < 0 || index >= TOTAL_SIZE) return true;
        return slots[index] == null || slots[index].isEmpty();
    }

    /**
     * Get the block ID at a specific slot index.
     * Returns 0 if empty.
     */
    public int getBlockId(int index) {
        if (index < 0 || index >= TOTAL_SIZE) return 0;
        return slots[index] != null ? slots[index].getBlockId() : 0;
    }

    /**
     * Get the item count at a specific slot index.
     * Returns 0 if empty.
     */
    public int getCount(int index) {
        if (index < 0 || index >= TOTAL_SIZE) return 0;
        return slots[index] != null ? slots[index].getCount() : 0;
    }

    /**
     * Get the number of used (non-empty) slots.
     */
    public int getUsedSlotCount() {
        int count = 0;
        for (ItemStack slot : slots) {
            if (slot != null && !slot.isEmpty()) count++;
        }
        return count;
    }
}
