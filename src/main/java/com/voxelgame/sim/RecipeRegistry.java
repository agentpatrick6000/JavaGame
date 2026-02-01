package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import java.util.ArrayList;
import java.util.List;

public final class RecipeRegistry {

    private RecipeRegistry() {}

    private static final List<Recipe> recipes = new ArrayList<>();

    static { registerAll(); }

    public static Recipe findMatch(int[] grid) {
        if (grid == null || grid.length != 4) return null;
        boolean allEmpty = true;
        for (int id : grid) { if (id != 0) { allEmpty = false; break; } }
        if (allEmpty) return null;
        for (Recipe recipe : recipes) {
            if (recipe.matches(grid)) return recipe;
        }
        return null;
    }

    public static List<Recipe> getAllRecipes() {
        return List.copyOf(recipes);
    }

    private static void registerAll() {
        int LOG = Blocks.LOG.id();
        int PLANKS = Blocks.PLANKS.id();
        int COBBLE = Blocks.COBBLESTONE.id();
        int STICK = Blocks.STICK.id();
        int CRAFT_TABLE = Blocks.CRAFTING_TABLE.id();
        int SAND = Blocks.SAND.id();
        int IRON = Blocks.IRON_ORE.id();
        int COAL = Blocks.COAL.id();
        int CHARCOAL = Blocks.CHARCOAL.id();
        int IRON_INGOT = Blocks.IRON_INGOT.id();

        recipes.add(Recipe.shapeless(new int[]{ LOG }, PLANKS, 4));
        recipes.add(new Recipe(1, 2, new int[]{ PLANKS, PLANKS }, STICK, 4));
        recipes.add(new Recipe(2, 2, new int[]{ PLANKS, PLANKS, PLANKS, PLANKS }, CRAFT_TABLE, 1));

        recipes.add(new Recipe(2, 2, new int[]{ PLANKS, PLANKS, STICK, 0 }, Blocks.WOODEN_PICKAXE.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ PLANKS, PLANKS, PLANKS, STICK }, Blocks.WOODEN_AXE.id(), 1));
        recipes.add(new Recipe(1, 2, new int[]{ PLANKS, STICK }, Blocks.WOODEN_SHOVEL.id(), 1));

        recipes.add(new Recipe(2, 2, new int[]{ COBBLE, COBBLE, STICK, 0 }, Blocks.STONE_PICKAXE.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ COBBLE, COBBLE, COBBLE, STICK }, Blocks.STONE_AXE.id(), 1));
        recipes.add(new Recipe(1, 2, new int[]{ COBBLE, STICK }, Blocks.STONE_SHOVEL.id(), 1));

        // ---- Advanced feature recipes ----

        // Chest: 2x2 planks (same as crafting table, but with sticks in pattern)
        recipes.add(new Recipe(2, 2, new int[]{ PLANKS, PLANKS, PLANKS, STICK }, Blocks.CHEST.id(), 1));

        // Boat: planks in bottom row + stick
        recipes.add(new Recipe(2, 2, new int[]{ PLANKS, 0, PLANKS, PLANKS }, Blocks.BOAT_ITEM.id(), 1));

        // Minecart: iron ingots (now available via smelting)
        recipes.add(new Recipe(2, 2, new int[]{ IRON_INGOT, 0, IRON_INGOT, IRON_INGOT }, Blocks.MINECART_ITEM.id(), 1));

        // Rail: iron ingot + stick
        recipes.add(new Recipe(2, 2, new int[]{ IRON_INGOT, 0, STICK, 0 }, Blocks.RAIL.id(), 16));

        // TNT: sand + gravel (simplified recipe since no gunpowder)
        recipes.add(new Recipe(2, 2, new int[]{ SAND, SAND, SAND, SAND }, Blocks.TNT.id(), 1));

        // ---- InfDev 611 recipes ----

        // Furnace: 4 cobblestone in 2x2
        recipes.add(new Recipe(2, 2, new int[]{ COBBLE, COBBLE, COBBLE, COBBLE }, Blocks.FURNACE.id(), 1));

        // Torch: coal/charcoal + stick → 4 torches
        recipes.add(new Recipe(1, 2, new int[]{ COAL, STICK }, Blocks.TORCH.id(), 4));
        recipes.add(new Recipe(1, 2, new int[]{ CHARCOAL, STICK }, Blocks.TORCH.id(), 4));

        // Iron tools (require iron ingots)
        recipes.add(new Recipe(2, 2, new int[]{ IRON_INGOT, IRON_INGOT, STICK, 0 }, Blocks.IRON_PICKAXE.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ IRON_INGOT, IRON_INGOT, IRON_INGOT, STICK }, Blocks.IRON_AXE.id(), 1));
        recipes.add(new Recipe(1, 2, new int[]{ IRON_INGOT, STICK }, Blocks.IRON_SHOVEL.id(), 1));

        // Swords
        recipes.add(new Recipe(1, 2, new int[]{ PLANKS, STICK }, Blocks.WOODEN_SWORD.id(), 1));
        recipes.add(new Recipe(1, 2, new int[]{ COBBLE, STICK }, Blocks.STONE_SWORD.id(), 1));
        recipes.add(new Recipe(1, 2, new int[]{ IRON_INGOT, STICK }, Blocks.IRON_SWORD.id(), 1));

        System.out.println("[RecipeRegistry] Registered " + recipes.size() + " recipes");
    }
}
