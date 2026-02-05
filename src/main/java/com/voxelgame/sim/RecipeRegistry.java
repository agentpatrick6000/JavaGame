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

        // Swords: 2x2 shaped with material + stick pattern (different from shovel/pickaxe)
        // Sword = material on top-left, stick on bottom-right diagonal
        recipes.add(new Recipe(2, 2, new int[]{ PLANKS, 0, 0, STICK }, Blocks.WOODEN_SWORD.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ COBBLE, 0, 0, STICK }, Blocks.STONE_SWORD.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ IRON_INGOT, 0, 0, STICK }, Blocks.IRON_SWORD.id(), 1));

        // ---- Redstone & Extended Rail recipes ----

        int GOLD_INGOT = Blocks.GOLD_INGOT.id();
        int REDSTONE = Blocks.REDSTONE.id();
        int REDSTONE_TORCH = Blocks.REDSTONE_TORCH.id();

        // Powered rail: gold ingot + stick → 6 powered rails (simplified 2x2)
        recipes.add(new Recipe(2, 2, new int[]{ GOLD_INGOT, 0, STICK, 0 }, Blocks.POWERED_RAIL.id(), 6));

        // Redstone torch: redstone + stick → 1 (like regular torch but with redstone)
        recipes.add(new Recipe(1, 2, new int[]{ REDSTONE, STICK }, Blocks.REDSTONE_TORCH.id(), 1));

        // Redstone repeater: stone + redstone torch + redstone (simplified 2x2)
        recipes.add(new Recipe(2, 2, new int[]{ REDSTONE_TORCH, REDSTONE, COBBLE, COBBLE }, Blocks.REDSTONE_REPEATER.id(), 1));

        // Redstone wire placement: redstone item placed directly becomes wire
        // (handled in GameLoop, not a crafting recipe — redstone item IS the wire material)


        // ---- Armor recipes ----
        int LEATHER = Blocks.LEATHER.id();
        int DIAMOND_ITEM = Blocks.DIAMOND.id();

        // Leather armor
        recipes.add(new Recipe(2, 2, new int[]{ LEATHER, LEATHER, LEATHER, 0 }, Blocks.LEATHER_HELMET.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ LEATHER, 0, LEATHER, LEATHER }, Blocks.LEATHER_CHESTPLATE.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ LEATHER, LEATHER, LEATHER, LEATHER }, Blocks.LEATHER_LEGGINGS.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ 0, 0, LEATHER, LEATHER }, Blocks.LEATHER_BOOTS.id(), 1));

        // String crafting (from wool, temporary until spiders added)
        int WOOL = Blocks.WOOL.id();
        recipes.add(new Recipe(1, 1, new int[]{ WOOL }, Blocks.STRING.id(), 4));

        // Bow crafting (stick + string)
        int STRING = Blocks.STRING.id();
        recipes.add(new Recipe(2, 2, new int[]{ 0, STICK, STRING, 0 }, Blocks.BOW.id(), 1));

        // Arrow crafting (flint + stick + feather)
        int FLINT = Blocks.FLINT.id();
        int FEATHER = Blocks.FEATHER.id();
        recipes.add(new Recipe(1, 3, new int[]{ FLINT, STICK, FEATHER }, Blocks.ARROW_ITEM.id(), 4));

        // Flint and steel (shapeless: iron ingot + flint)
        recipes.add(Recipe.shapeless(new int[]{ IRON_INGOT, FLINT }, Blocks.FLINT_AND_STEEL.id(), 1));

        // Iron armor
        recipes.add(new Recipe(2, 2, new int[]{ IRON_INGOT, IRON_INGOT, IRON_INGOT, 0 }, Blocks.IRON_HELMET.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ IRON_INGOT, 0, IRON_INGOT, IRON_INGOT }, Blocks.IRON_CHESTPLATE.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ IRON_INGOT, IRON_INGOT, IRON_INGOT, IRON_INGOT }, Blocks.IRON_LEGGINGS.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ 0, 0, IRON_INGOT, IRON_INGOT }, Blocks.IRON_BOOTS.id(), 1));

        // Diamond armor
        recipes.add(new Recipe(2, 2, new int[]{ DIAMOND_ITEM, DIAMOND_ITEM, DIAMOND_ITEM, 0 }, Blocks.DIAMOND_HELMET.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ DIAMOND_ITEM, 0, DIAMOND_ITEM, DIAMOND_ITEM }, Blocks.DIAMOND_CHESTPLATE.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ DIAMOND_ITEM, DIAMOND_ITEM, DIAMOND_ITEM, DIAMOND_ITEM }, Blocks.DIAMOND_LEGGINGS.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ 0, 0, DIAMOND_ITEM, DIAMOND_ITEM }, Blocks.DIAMOND_BOOTS.id(), 1));

        // Gold armor
        recipes.add(new Recipe(2, 2, new int[]{ GOLD_INGOT, GOLD_INGOT, GOLD_INGOT, 0 }, Blocks.GOLD_HELMET.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ GOLD_INGOT, 0, GOLD_INGOT, GOLD_INGOT }, Blocks.GOLD_CHESTPLATE.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ GOLD_INGOT, GOLD_INGOT, GOLD_INGOT, GOLD_INGOT }, Blocks.GOLD_LEGGINGS.id(), 1));
        recipes.add(new Recipe(2, 2, new int[]{ 0, 0, GOLD_INGOT, GOLD_INGOT }, Blocks.GOLD_BOOTS.id(), 1));

        System.out.println("[RecipeRegistry] Registered " + recipes.size() + " recipes");
    }
}
