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
     * An item stack: block ID + count.
     */
    public static class ItemStack {
        private int blockId;
        private int count;

        public ItemStack(int blockId, int count) {
            this.blockId = blockId;
            this.count = Math.min(count, MAX_STACK);
        }

        public int getBlockId() { return blockId; }
        public int getCount() { return count; }

        public void setCount(int count) { this.count = Math.min(count, MAX_STACK); }
        public void setBlockId(int blockId) { this.blockId = blockId; }

        /** Add to this stack. Returns leftover that didn't fit. */
        public int add(int amount) {
            int canFit = MAX_STACK - count;
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

        public boolean isFull() { return count >= MAX_STACK; }
        public boolean isEmpty() { return count <= 0; }

        public ItemStack copy() { return new ItemStack(blockId, count); }

        @Override
        public String toString() {
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

        int remaining = count;

        // First pass: try to stack with existing items (hotbar then storage)
        for (int i = 0; i < TOTAL_SIZE && remaining > 0; i++) {
            if (slots[i] != null && slots[i].getBlockId() == blockId && !slots[i].isFull()) {
                remaining = slots[i].add(remaining);
            }
        }

        // Second pass: fill empty slots (hotbar first)
        for (int i = 0; i < TOTAL_SIZE && remaining > 0; i++) {
            if (slots[i] == null || slots[i].isEmpty()) {
                int toPlace = Math.min(remaining, MAX_STACK);
                slots[i] = new ItemStack(blockId, toPlace);
                remaining -= toPlace;
            }
        }

        return remaining;
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
