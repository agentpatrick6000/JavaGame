package com.voxelgame.sim;

/**
 * Furnace tile entity — stores 3 item slots (fuel, input, output) at a specific
 * world position. Handles smelting logic: consumes fuel, processes input items
 * into output items based on registered smelting recipes.
 *
 * Tick-based: call tick() every game tick (20 ticks/second).
 * Smelting time: 10 ticks per item (0.5 seconds).
 */
public class Furnace {

    /** Number of ticks to smelt one item. */
    public static final int SMELT_TIME = 10;

    /** World position of the furnace block. */
    private final int x, y, z;

    /** Slots: 0 = input, 1 = fuel, 2 = output */
    private final Inventory.ItemStack[] slots = new Inventory.ItemStack[3];

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;

    /** Current fuel burn time remaining (in ticks). 0 = no fuel burning. */
    private int fuelBurnTime = 0;

    /** Maximum fuel burn time of the current fuel (for progress bar). */
    private int maxFuelBurnTime = 0;

    /** Current smelting progress (ticks). When >= SMELT_TIME, item is done. */
    private int smeltProgress = 0;

    /** Whether the furnace is currently active (burning fuel). */
    private boolean active = false;

    public Furnace(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // ---- Slot access ----

    public Inventory.ItemStack getSlot(int index) {
        if (index < 0 || index >= 3) return null;
        return slots[index];
    }

    public void setSlot(int index, Inventory.ItemStack stack) {
        if (index < 0 || index >= 3) return;
        slots[index] = stack;
    }

    // ---- Smelting tick ----

    /**
     * Tick the furnace. Call this every game tick.
     * Returns true if the furnace state changed (for mesh rebuild / UI update).
     */
    public boolean tick() {
        boolean changed = false;

        // Check if we can smelt
        SmeltingRecipe.Recipe recipe = getActiveRecipe();
        boolean canSmelt = (recipe != null) && canOutputFit(recipe);

        // If we need fuel and can smelt, try to consume fuel
        if (canSmelt && fuelBurnTime <= 0) {
            if (tryConsumeFuel()) {
                changed = true;
            }
        }

        // If burning fuel
        if (fuelBurnTime > 0) {
            fuelBurnTime--;

            if (!active) {
                active = true;
                changed = true;
            }

            // Process smelting
            if (canSmelt) {
                smeltProgress++;
                if (smeltProgress >= SMELT_TIME) {
                    // Smelting complete — produce output
                    finishSmelting(recipe);
                    smeltProgress = 0;
                    changed = true;
                }
            } else {
                // No valid input — reset progress
                if (smeltProgress > 0) {
                    smeltProgress = 0;
                    changed = true;
                }
            }
        } else {
            // No fuel — stop
            if (active) {
                active = false;
                changed = true;
            }
            if (smeltProgress > 0) {
                smeltProgress = 0;
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Get the smelting recipe for the current input item.
     */
    private SmeltingRecipe.Recipe getActiveRecipe() {
        Inventory.ItemStack input = slots[SLOT_INPUT];
        if (input == null || input.isEmpty()) return null;
        return SmeltingRecipe.findRecipe(input.getBlockId());
    }

    /**
     * Check if the output slot can accept the recipe result.
     */
    private boolean canOutputFit(SmeltingRecipe.Recipe recipe) {
        Inventory.ItemStack output = slots[SLOT_OUTPUT];
        if (output == null || output.isEmpty()) return true;
        if (output.getBlockId() != recipe.outputId()) return false;
        return output.getCount() + recipe.outputCount() <= Inventory.MAX_STACK;
    }

    /**
     * Try to consume one fuel item. Returns true if fuel was consumed.
     */
    private boolean tryConsumeFuel() {
        Inventory.ItemStack fuel = slots[SLOT_FUEL];
        if (fuel == null || fuel.isEmpty()) return false;

        int burnTime = SmeltingRecipe.getFuelValue(fuel.getBlockId());
        if (burnTime <= 0) return false;

        fuel.remove(1);
        if (fuel.isEmpty()) {
            slots[SLOT_FUEL] = null;
        }

        fuelBurnTime = burnTime;
        maxFuelBurnTime = burnTime;
        return true;
    }

    /**
     * Finish smelting: consume input, produce output.
     */
    private void finishSmelting(SmeltingRecipe.Recipe recipe) {
        // Consume input
        Inventory.ItemStack input = slots[SLOT_INPUT];
        input.remove(1);
        if (input.isEmpty()) {
            slots[SLOT_INPUT] = null;
        }

        // Produce output
        Inventory.ItemStack output = slots[SLOT_OUTPUT];
        if (output == null || output.isEmpty()) {
            slots[SLOT_OUTPUT] = new Inventory.ItemStack(recipe.outputId(), recipe.outputCount());
        } else {
            output.add(recipe.outputCount());
        }
    }

    // ---- State queries ----

    public boolean isActive() { return active; }
    public int getFuelBurnTime() { return fuelBurnTime; }
    public int getMaxFuelBurnTime() { return maxFuelBurnTime; }
    public int getSmeltProgress() { return smeltProgress; }

    /** Fuel burn progress 0.0 - 1.0 (for flame icon). */
    public float getFuelProgress() {
        if (maxFuelBurnTime <= 0) return 0;
        return (float) fuelBurnTime / maxFuelBurnTime;
    }

    /** Smelting progress 0.0 - 1.0 (for arrow icon). */
    public float getSmeltProgressFraction() {
        return (float) smeltProgress / SMELT_TIME;
    }

    // ---- Position ----

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public boolean isAt(int bx, int by, int bz) {
        return x == bx && y == by && z == bz;
    }

    // ---- Serialization ----

    /**
     * Serialize furnace state to int array.
     * Format: [input_id, input_count, fuel_id, fuel_count, output_id, output_count,
     *          fuelBurnTime, maxFuelBurnTime, smeltProgress]
     */
    public int[] serialize() {
        int[] data = new int[9];
        if (slots[0] != null && !slots[0].isEmpty()) {
            data[0] = slots[0].getBlockId();
            data[1] = slots[0].getCount();
        }
        if (slots[1] != null && !slots[1].isEmpty()) {
            data[2] = slots[1].getBlockId();
            data[3] = slots[1].getCount();
        }
        if (slots[2] != null && !slots[2].isEmpty()) {
            data[4] = slots[2].getBlockId();
            data[5] = slots[2].getCount();
        }
        data[6] = fuelBurnTime;
        data[7] = maxFuelBurnTime;
        data[8] = smeltProgress;
        return data;
    }

    /**
     * Deserialize furnace state from int array.
     */
    public void deserialize(int[] data) {
        if (data == null || data.length < 9) return;
        slots[0] = (data[0] > 0 && data[1] > 0) ? new Inventory.ItemStack(data[0], data[1]) : null;
        slots[1] = (data[2] > 0 && data[3] > 0) ? new Inventory.ItemStack(data[2], data[3]) : null;
        slots[2] = (data[4] > 0 && data[5] > 0) ? new Inventory.ItemStack(data[4], data[5]) : null;
        fuelBurnTime = data[6];
        maxFuelBurnTime = data[7];
        smeltProgress = data[8];
        active = fuelBurnTime > 0;
    }

    /**
     * Drop all items from the furnace (when broken).
     */
    public Inventory.ItemStack[] dropAll() {
        Inventory.ItemStack[] drops = new Inventory.ItemStack[3];
        for (int i = 0; i < 3; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) {
                drops[i] = slots[i].copy();
                slots[i] = null;
            }
        }
        return drops;
    }

    public boolean isEmpty() {
        for (Inventory.ItemStack slot : slots) {
            if (slot != null && !slot.isEmpty()) return false;
        }
        return true;
    }

    // ---- Convenience getters for FurnaceScreen ----

    public int getInputId() {
        return (slots[SLOT_INPUT] != null && !slots[SLOT_INPUT].isEmpty()) ? slots[SLOT_INPUT].getBlockId() : 0;
    }
    public int getInputCount() {
        return (slots[SLOT_INPUT] != null) ? slots[SLOT_INPUT].getCount() : 0;
    }
    public void setInput(int blockId, int count) {
        slots[SLOT_INPUT] = (blockId > 0 && count > 0) ? new Inventory.ItemStack(blockId, count) : null;
    }

    public int getFuelId() {
        return (slots[SLOT_FUEL] != null && !slots[SLOT_FUEL].isEmpty()) ? slots[SLOT_FUEL].getBlockId() : 0;
    }
    public int getFuelCount() {
        return (slots[SLOT_FUEL] != null) ? slots[SLOT_FUEL].getCount() : 0;
    }
    public void setFuel(int blockId, int count) {
        slots[SLOT_FUEL] = (blockId > 0 && count > 0) ? new Inventory.ItemStack(blockId, count) : null;
    }
    public int getFuelRemaining() { return fuelBurnTime; }
    public int getCurrentFuelTotal() { return maxFuelBurnTime; }

    public int getOutputId() {
        return (slots[SLOT_OUTPUT] != null && !slots[SLOT_OUTPUT].isEmpty()) ? slots[SLOT_OUTPUT].getBlockId() : 0;
    }
    public int getOutputCount() {
        return (slots[SLOT_OUTPUT] != null) ? slots[SLOT_OUTPUT].getCount() : 0;
    }
    public void setOutput(int blockId, int count) {
        slots[SLOT_OUTPUT] = (blockId > 0 && count > 0) ? new Inventory.ItemStack(blockId, count) : null;
    }

    /** Check if a given input can be smelted. */
    public static boolean canSmelt(int inputId) {
        return SmeltingRecipe.findRecipe(inputId) != null;
    }

    /** Check if a given item is valid fuel. */
    public static boolean isFuel(int blockId) {
        return SmeltingRecipe.isFuel(blockId);
    }
}
