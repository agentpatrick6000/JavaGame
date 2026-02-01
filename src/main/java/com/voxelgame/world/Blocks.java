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
    public static final Block COAL_ORE     = new Block(10, "coal_ore",     true,  false, new int[]{12},                 3.0f, -1);  // drops self (coal item later)
    public static final Block IRON_ORE     = new Block(11, "iron_ore",     true,  false, new int[]{13},                 3.0f, -1);  // drops self
    public static final Block GOLD_ORE     = new Block(12, "gold_ore",     true,  false, new int[]{14},                 3.0f, -1);  // drops self
    public static final Block DIAMOND_ORE  = new Block(13, "diamond_ore",  true,  false, new int[]{15},                 3.0f, -1);  // drops self
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

    /** All blocks indexed by ID for fast lookup. */
    private static final Block[] REGISTRY = {
        AIR, STONE, COBBLESTONE, DIRT, GRASS, SAND, GRAVEL,
        LOG, LEAVES, WATER, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, BEDROCK,
        RAW_PORKCHOP, ROTTEN_FLESH,
        PLANKS, CRAFTING_TABLE, STICK,
        WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL,
        STONE_PICKAXE, STONE_AXE, STONE_SHOVEL
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
}
