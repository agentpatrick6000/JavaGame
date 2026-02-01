package com.voxelgame.world;

/**
 * Block registry. Defines all block types and provides lookup by ID.
 * MVP set: 15 blocks (AIR through BEDROCK).
 *
 * Texture indices refer to positions in the texture atlas (row-major).
 * Atlas layout TBD — indices are placeholders until atlas.png is created.
 *
 * Block constructor: (id, name, solid, transparent, textures, hardness, dropId)
 *   hardness: seconds to mine by hand (-1 = unbreakable)
 *   dropId: -1 = drops self, 0 = drops nothing, >0 = specific block ID
 */
public final class Blocks {

    private Blocks() {}

    // --- Block definitions (id, name, solid, transparent, textures, hardness, dropId) ---

    //                                                                                    hardness  dropId
    public static final Block AIR          = new Block( 0, "air",          false, true,  new int[]{0},                  0.0f,  0);  // instant, drops nothing
    public static final Block STONE        = new Block( 1, "stone",        true,  false, new int[]{1},                  1.5f,  2);  // drops cobblestone
    public static final Block COBBLESTONE  = new Block( 2, "cobblestone",  true,  false, new int[]{2},                  2.0f, -1);  // drops self
    public static final Block DIRT         = new Block( 3, "dirt",         true,  false, new int[]{3},                  0.5f, -1);  // drops self
    public static final Block GRASS        = new Block( 4, "grass",        true,  false, new int[]{4, 3, 5, 5, 5, 5},  0.6f,  3);  // drops dirt
    public static final Block SAND         = new Block( 5, "sand",         true,  false, new int[]{6},                  0.5f, -1);  // drops self
    public static final Block GRAVEL       = new Block( 6, "gravel",       true,  false, new int[]{7},                  0.6f, -1);  // drops self
    public static final Block LOG          = new Block( 7, "log",          true,  false, new int[]{8, 8, 9, 9, 9, 9},  2.0f, -1);  // drops self
    public static final Block LEAVES       = new Block( 8, "leaves",       true,  true,  new int[]{10},                 0.2f,  0);  // drops nothing (could add sapling later)
    public static final Block WATER        = new Block( 9, "water",        false, true,  new int[]{11},                -1.0f,  0);  // unbreakable, drops nothing
    public static final Block COAL_ORE     = new Block(10, "coal_ore",     true,  false, new int[]{12},                 3.0f, 33);  // drops coal item
    public static final Block IRON_ORE     = new Block(11, "iron_ore",     true,  false, new int[]{13},                 3.0f, -1);  // drops self (smelts to ingot)
    public static final Block GOLD_ORE     = new Block(12, "gold_ore",     true,  false, new int[]{14},                 3.0f, -1);  // drops self
    public static final Block DIAMOND_ORE  = new Block(13, "diamond_ore",  true,  false, new int[]{15},                 3.0f, 39);  // drops diamond item
    public static final Block BEDROCK      = new Block(14, "bedrock",      true,  false, new int[]{16},                -1.0f,  0);  // unbreakable

    // ---- Mob drop items (non-solid, non-placeable) ----
    public static final Block RAW_PORKCHOP = new Block(15, "raw_porkchop", false, true,  new int[]{17},                 0.0f, -1);  // pig drop
    public static final Block ROTTEN_FLESH = new Block(16, "rotten_flesh", false, true,  new int[]{18},                 0.0f, -1);  // zombie drop

    // ---- Crafted blocks (solid, placeable) ----
    public static final Block PLANKS         = new Block(17, "planks",         true,  false, new int[]{19},                 2.0f, -1);
    public static final Block CRAFTING_TABLE = new Block(18, "crafting_table", true,  false, new int[]{20, 20, 21, 21, 21, 21}, 2.5f, -1);

    // ---- Crafting material items (non-solid, non-placeable) ----
    public static final Block STICK          = new Block(19, "stick",          false, true,  new int[]{0},                  0.0f, -1);

    // ---- Tool items (non-solid, non-placeable) ----
    public static final Block WOODEN_PICKAXE = new Block(20, "wooden_pickaxe", false, true, new int[]{0},                  0.0f, -1);
    public static final Block WOODEN_AXE     = new Block(21, "wooden_axe",     false, true, new int[]{0},                  0.0f, -1);
    public static final Block WOODEN_SHOVEL  = new Block(22, "wooden_shovel",  false, true, new int[]{0},                  0.0f, -1);
    public static final Block STONE_PICKAXE  = new Block(23, "stone_pickaxe",  false, true, new int[]{0},                  0.0f, -1);
    public static final Block STONE_AXE      = new Block(24, "stone_axe",      false, true, new int[]{0},                  0.0f, -1);
    public static final Block STONE_SHOVEL   = new Block(25, "stone_shovel",   false, true, new int[]{0},                  0.0f, -1);

    // ---- Advanced feature blocks ----
    public static final Block CHEST          = new Block(26, "chest",          true,  false, new int[]{22, 22, 23, 23, 23, 23}, 2.5f, -1);
    public static final Block RAIL           = new Block(27, "rail",           false, true,  new int[]{24},                 0.7f, -1);
    public static final Block TNT            = new Block(28, "tnt",            true,  false, new int[]{25, 25, 26, 26, 26, 26}, 0.0f,  0);  // instant break, drops nothing (activates instead)

    // ---- Advanced feature items (non-solid, non-placeable) ----
    public static final Block BOAT_ITEM      = new Block(29, "boat",           false, true,  new int[]{0},                  0.0f, -1);
    public static final Block MINECART_ITEM  = new Block(30, "minecart",       false, true,  new int[]{0},                  0.0f, -1);

    // ---- InfDev 611 blocks/items ----

    // Furnace block (solid, placeable)
    public static final Block FURNACE        = new Block(31, "furnace",        true,  false, new int[]{27, 27, 28, 29, 28, 28}, 3.5f, -1);

    // Torch (non-solid, transparent — light source, level 14)
    public static final Block TORCH          = new Block(32, "torch",          false, true,  new int[]{30},                 0.0f, -1);

    // Coal item (drops from coal ore)
    public static final Block COAL           = new Block(33, "coal",           false, true,  new int[]{31},                 0.0f, -1);

    // Iron ingot (from smelting iron ore)
    public static final Block IRON_INGOT     = new Block(34, "iron_ingot",     false, true,  new int[]{32},                 0.0f, -1);

    // Glass (solid, transparent — from smelting sand)
    public static final Block GLASS          = new Block(35, "glass",          true,  true,  new int[]{33},                 0.0f, -1);

    // Cooked porkchop (from smelting raw pork)
    public static final Block COOKED_PORKCHOP = new Block(36, "cooked_porkchop", false, true, new int[]{34},               0.0f, -1);

    // Flowers (non-solid, transparent, decorative)
    public static final Block RED_FLOWER     = new Block(37, "red_flower",     false, true,  new int[]{35},                 0.0f, -1);
    public static final Block YELLOW_FLOWER  = new Block(38, "yellow_flower",  false, true,  new int[]{36},                 0.0f, -1);

    // Diamond item (drops from diamond ore)
    public static final Block DIAMOND        = new Block(39, "diamond",        false, true,  new int[]{37},                 0.0f, -1);

    // Iron tools
    public static final Block IRON_PICKAXE   = new Block(40, "iron_pickaxe",   false, true,  new int[]{0},                  0.0f, -1);
    public static final Block IRON_AXE       = new Block(41, "iron_axe",       false, true,  new int[]{0},                  0.0f, -1);
    public static final Block IRON_SHOVEL    = new Block(42, "iron_shovel",    false, true,  new int[]{0},                  0.0f, -1);
    public static final Block IRON_SWORD     = new Block(43, "iron_sword",     false, true,  new int[]{0},                  0.0f, -1);

    // Wooden/stone swords
    public static final Block WOODEN_SWORD   = new Block(44, "wooden_sword",   false, true,  new int[]{0},                  0.0f, -1);
    public static final Block STONE_SWORD    = new Block(45, "stone_sword",    false, true,  new int[]{0},                  0.0f, -1);

    // Charcoal (fuel, from smelting logs)
    public static final Block CHARCOAL       = new Block(46, "charcoal",       false, true,  new int[]{38},                 0.0f, -1);

    /** All blocks indexed by ID for fast lookup. */
    private static final Block[] REGISTRY = {
        AIR, STONE, COBBLESTONE, DIRT, GRASS, SAND, GRAVEL,
        LOG, LEAVES, WATER, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, BEDROCK,
        RAW_PORKCHOP, ROTTEN_FLESH,
        PLANKS, CRAFTING_TABLE, STICK,
        WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL,
        STONE_PICKAXE, STONE_AXE, STONE_SHOVEL,
        CHEST, RAIL, TNT,
        BOAT_ITEM, MINECART_ITEM,
        FURNACE, TORCH, COAL, IRON_INGOT, GLASS, COOKED_PORKCHOP,
        RED_FLOWER, YELLOW_FLOWER, DIAMOND,
        IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_SWORD,
        WOODEN_SWORD, STONE_SWORD,
        CHARCOAL
    };

    /**
     * Returns the block for the given ID.
     * @param id block ID (0–14)
     * @return the Block, or AIR if out of range
     */
    public static Block get(int id) {
        if (id < 0 || id >= REGISTRY.length) return AIR;
        return REGISTRY[id];
    }

    /** Total number of registered block types. */
    public static int count() {
        return REGISTRY.length;
    }

    /**
     * Get the block light emission level for a given block ID.
     * Returns 0 for most blocks. Torches emit 14, furnaces emit 13 when active.
     */
    public static int getLightEmission(int blockId) {
        if (blockId == TORCH.id()) return 14;
        // Future: active furnace could emit light
        return 0;
    }

    /**
     * Check if a block is a non-solid "placeable" (like torches, flowers, rails).
     * These can be placed on top of/against solid blocks but don't block movement.
     */
    public static boolean isNonSolidPlaceable(int blockId) {
        return blockId == TORCH.id()
            || blockId == RED_FLOWER.id()
            || blockId == YELLOW_FLOWER.id()
            || blockId == RAIL.id();
    }

    /**
     * Check if a block is a flower.
     */
    public static boolean isFlower(int blockId) {
        return blockId == RED_FLOWER.id() || blockId == YELLOW_FLOWER.id();
    }
}
