package com.voxelgame.sim;

import com.voxelgame.world.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smelting recipe registry. Maps input block ID → output block ID + count.
 * Also provides fuel burn times for valid fuel items.
 */
public final class SmeltingRecipe {

    /** A smelting recipe: input → output with count. */
    public record Recipe(int inputId, int outputId, int outputCount) {}

    private static final List<Recipe> RECIPES = new ArrayList<>();
    private static final Map<Integer, Integer> FUEL_VALUES = new HashMap<>();

    static {
        registerRecipes();
        registerFuels();
    }

    private SmeltingRecipe() {}

    private static void registerRecipes() {
        // Ore smelting
        RECIPES.add(new Recipe(Blocks.IRON_ORE.id(), Blocks.IRON_INGOT.id(), 1));
        RECIPES.add(new Recipe(Blocks.GOLD_ORE.id(), Blocks.IRON_INGOT.id(), 1)); // placeholder — gold ingot later

        // Material processing
        RECIPES.add(new Recipe(Blocks.SAND.id(), Blocks.GLASS.id(), 1));
        RECIPES.add(new Recipe(Blocks.LOG.id(), Blocks.CHARCOAL.id(), 1));
        RECIPES.add(new Recipe(Blocks.COBBLESTONE.id(), Blocks.STONE.id(), 1)); // cobblestone → stone

        // Food cooking
        RECIPES.add(new Recipe(Blocks.RAW_PORKCHOP.id(), Blocks.COOKED_PORKCHOP.id(), 1));

        System.out.println("[SmeltingRecipe] Registered " + RECIPES.size() + " smelting recipes");
    }

    private static void registerFuels() {
        FUEL_VALUES.put(Blocks.COAL.id(), 80);       // 80 ticks (smelts 8 items)
        FUEL_VALUES.put(Blocks.CHARCOAL.id(), 80);   // 80 ticks (smelts 8 items)
        FUEL_VALUES.put(Blocks.PLANKS.id(), 15);      // 15 ticks (smelts 1.5 items)
        FUEL_VALUES.put(Blocks.STICK.id(), 5);         // 5 ticks (smelts 0.5 items)
        FUEL_VALUES.put(Blocks.LOG.id(), 15);          // 15 ticks (same as planks)

        System.out.println("[SmeltingRecipe] Registered " + FUEL_VALUES.size() + " fuel types");
    }

    /**
     * Get the result block ID for a given input item.
     * @return the output block ID, or 0 if no recipe exists
     */
    public static int getResult(int inputId) {
        Recipe recipe = findRecipe(inputId);
        return recipe != null ? recipe.outputId() : 0;
    }

    /**
     * Find a smelting recipe for the given input item.
     * @return the recipe, or null if no recipe exists
     */
    public static Recipe findRecipe(int inputId) {
        if (inputId <= 0) return null;
        for (Recipe recipe : RECIPES) {
            if (recipe.inputId() == inputId) return recipe;
        }
        return null;
    }

    /**
     * Get the fuel burn time for a block/item ID.
     * @return burn time in ticks (10 ticks = 1 item smelted), or 0 if not a fuel
     */
    public static int getFuelValue(int blockId) {
        return FUEL_VALUES.getOrDefault(blockId, 0);
    }

    /**
     * Check if a block/item is valid fuel.
     */
    public static boolean isFuel(int blockId) {
        return FUEL_VALUES.containsKey(blockId);
    }

    /**
     * Check if an input item can be smelted.
     */
    public static boolean canSmelt(int inputId) {
        return findRecipe(inputId) != null;
    }

    /**
     * Get all registered recipes (for UI display).
     */
    public static List<Recipe> getAllRecipes() {
        return List.copyOf(RECIPES);
    }
}
