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

        recipes.add(Recipe.shapeless(new int[]{ LOG }, PLANKS, 4));
        recipes.add(new Recipe(1, 2, new int[]{ PLANKS, PLANKS }, STICK, 4));
        recipes.add(new Recipe(2, 2, new int[]{ PLANKS, PLANKS, PLANKS, PLANKS }, CRAFT_TABLE, 1));

        recipes.add(new Recipe(2, 2, new int[]{ PLANKS, PLANKS, STICK, 0 }, Blocks.WOODEN_PICKAXE.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ PLANKS, PLANKS, PLANKS, STICK }, Blocks.WOODEN_AXE.id(), 1));
        recipes.add(new Recipe(1, 2, new int[]{ PLANKS, STICK }, Blocks.WOODEN_SHOVEL.id(), 1));

        recipes.add(new Recipe(2, 2, new int[]{ COBBLE, COBBLE, STICK, 0 }, Blocks.STONE_PICKAXE.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ COBBLE, COBBLE, COBBLE, STICK }, Blocks.STONE_AXE.id(), 1));
        recipes.add(new Recipe(1, 2, new int[]{ COBBLE, STICK }, Blocks.STONE_SHOVEL.id(), 1));

        System.out.println("[RecipeRegistry] Registered " + recipes.size() + " recipes");
    }
}
