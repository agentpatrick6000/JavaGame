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

    // ---- Redstone & Rail Extension Blocks ----

    // Gold ingot (from smelting gold ore)
    public static final Block GOLD_INGOT     = new Block(47, "gold_ingot",     false, true,  new int[]{39},                 0.0f, -1);

    // Powered rail / booster track (non-solid, placeable)
    public static final Block POWERED_RAIL   = new Block(48, "powered_rail",   false, true,  new int[]{40},                 0.7f, -1);

    // Redstone dust item (drops from redstone ore or crafting)
    public static final Block REDSTONE       = new Block(49, "redstone",       false, true,  new int[]{41},                 0.0f, -1);

    // Redstone dust wire (placed on ground, non-solid, transparent)
    public static final Block REDSTONE_WIRE  = new Block(50, "redstone_wire",  false, true,  new int[]{42},                 0.0f, 49);  // drops redstone item

    // Redstone torch (non-solid, light source, power source)
    public static final Block REDSTONE_TORCH = new Block(51, "redstone_torch", false, true,  new int[]{43},                 0.0f, -1);

    // Redstone repeater (non-solid, placeable)
    public static final Block REDSTONE_REPEATER = new Block(52, "redstone_repeater", false, true, new int[]{44},            0.0f, -1);

    // Redstone ore (found underground, drops redstone dust)
    public static final Block REDSTONE_ORE   = new Block(53, "redstone_ore",   true,  false, new int[]{45},                 3.0f, 49);  // drops redstone

    // ---- Fluid blocks (Infdev 611 style) ----

    // Flowing water levels 1-7 (IDs 54-60). Level = id - 53.
    // Water source is WATER (id 9, level 0). Texture 11 = water texture.
    public static final Block FLOWING_WATER_1 = new Block(54, "flowing_water", false, true, new int[]{11}, -1.0f, 0);
    public static final Block FLOWING_WATER_2 = new Block(55, "flowing_water", false, true, new int[]{11}, -1.0f, 0);
    public static final Block FLOWING_WATER_3 = new Block(56, "flowing_water", false, true, new int[]{11}, -1.0f, 0);
    public static final Block FLOWING_WATER_4 = new Block(57, "flowing_water", false, true, new int[]{11}, -1.0f, 0);
    public static final Block FLOWING_WATER_5 = new Block(58, "flowing_water", false, true, new int[]{11}, -1.0f, 0);
    public static final Block FLOWING_WATER_6 = new Block(59, "flowing_water", false, true, new int[]{11}, -1.0f, 0);
    public static final Block FLOWING_WATER_7 = new Block(60, "flowing_water", false, true, new int[]{11}, -1.0f, 0);

    // Lava source (ID 61). Light emission 15. Texture 46.
    public static final Block LAVA            = new Block(61, "lava",          false, true, new int[]{46}, -1.0f, 0);

    // Flowing lava levels 1-7 (IDs 62-68). Level = id - 61.
    // Surface: max spread 3. Underground (y < SEA_LEVEL): max spread 7.
    public static final Block FLOWING_LAVA_1  = new Block(62, "flowing_lava",  false, true, new int[]{46}, -1.0f, 0);
    public static final Block FLOWING_LAVA_2  = new Block(63, "flowing_lava",  false, true, new int[]{46}, -1.0f, 0);
    public static final Block FLOWING_LAVA_3  = new Block(64, "flowing_lava",  false, true, new int[]{46}, -1.0f, 0);
    public static final Block FLOWING_LAVA_4  = new Block(65, "flowing_lava",  false, true, new int[]{46}, -1.0f, 0);
    public static final Block FLOWING_LAVA_5  = new Block(66, "flowing_lava",  false, true, new int[]{46}, -1.0f, 0);
    public static final Block FLOWING_LAVA_6  = new Block(67, "flowing_lava",  false, true, new int[]{46}, -1.0f, 0);
    public static final Block FLOWING_LAVA_7  = new Block(68, "flowing_lava",  false, true, new int[]{46}, -1.0f, 0);

    // Obsidian (from water + lava source interaction). Texture 47. Very hard.
    public static final Block OBSIDIAN        = new Block(69, "obsidian",      true,  false, new int[]{47}, 50.0f, -1);
    // ---- Alpha Building Blocks (IDs 70-97) ----
    // Texture indices 48-73

    // Doors (solid+transparent for special mesh rendering)
    public static final Block OAK_DOOR          = new Block(70, "oak_door",          true,  true,  new int[]{48},              3.0f, -1);
    public static final Block OAK_DOOR_TOP      = new Block(71, "oak_door_top",      true,  true,  new int[]{49},              3.0f,  0);
    public static final Block IRON_DOOR         = new Block(72, "iron_door",         true,  true,  new int[]{50},              5.0f, -1);
    public static final Block IRON_DOOR_TOP     = new Block(73, "iron_door_top",     true,  true,  new int[]{51},              5.0f,  0);

    // Stairs (solid, opaque)
    public static final Block COBBLESTONE_STAIRS = new Block(74, "cobblestone_stairs", true, false, new int[]{2},              2.0f, -1);
    public static final Block OAK_STAIRS        = new Block(75, "oak_stairs",        true,  false, new int[]{19},              2.0f, -1);

    // Slabs (solid+transparent, half-height)
    public static final Block STONE_SLAB        = new Block(76, "stone_slab",        true,  true,  new int[]{52, 52, 1, 1, 1, 1}, 2.0f, -1);
    public static final Block OAK_SLAB          = new Block(77, "oak_slab",          true,  true,  new int[]{19},              2.0f, -1);

    // Fence (solid+transparent post with connections)
    public static final Block OAK_FENCE         = new Block(78, "oak_fence",         true,  true,  new int[]{19},              2.0f, -1);

    // Ladder (non-solid, transparent, climbable)
    public static final Block LADDER            = new Block(79, "ladder",            false, true,  new int[]{53},              0.4f, -1);

    // Sign (non-solid, transparent)
    public static final Block SIGN              = new Block(80, "sign",              false, true,  new int[]{54},              1.0f, -1);

    // Bed (solid+transparent, 9/16 height)
    public static final Block BED_FOOT          = new Block(81, "bed",               true,  true,  new int[]{55, 56, 57, 57, 57, 57}, 0.2f, -1);
    public static final Block BED_HEAD          = new Block(82, "bed_head",          true,  true,  new int[]{58, 56, 59, 59, 59, 59}, 0.2f,  0);

    // Pressure plates (non-solid, transparent, 1/16 height)
    public static final Block WOOD_PRESSURE_PLATE  = new Block(83, "wood_pressure_plate",  false, true, new int[]{19}, 0.5f, -1);
    public static final Block STONE_PRESSURE_PLATE = new Block(84, "stone_pressure_plate", false, true, new int[]{1},  0.5f, -1);

    // Buttons (non-solid, transparent)
    public static final Block WOOD_BUTTON       = new Block(85, "wood_button",       false, true,  new int[]{19},              0.5f, -1);
    public static final Block STONE_BUTTON      = new Block(86, "stone_button",      false, true,  new int[]{1},               0.5f, -1);

    // Lever (non-solid, transparent)
    public static final Block LEVER             = new Block(87, "lever",             false, true,  new int[]{60},              0.5f, -1);

    // Snow layer (non-solid, transparent, 2/16 height)
    public static final Block SNOW_LAYER        = new Block(88, "snow_layer",        false, true,  new int[]{61},              0.1f, -1);

    // Ice (solid, transparent, slippery)
    public static final Block ICE               = new Block(89, "ice",               true,  true,  new int[]{62},              0.5f,  0);

    // Clay (solid, opaque)
    public static final Block CLAY              = new Block(90, "clay",              true,  false, new int[]{63},              0.6f, -1);

    // Brick (solid, opaque)
    public static final Block BRICK             = new Block(91, "brick",             true,  false, new int[]{64},              2.0f, -1);

    // Sponge (solid, opaque)
    public static final Block SPONGE            = new Block(92, "sponge",            true,  false, new int[]{65},              0.6f, -1);

    // Bookshelf (solid, opaque — planks top/bottom, books on sides)
    public static final Block BOOKSHELF         = new Block(93, "bookshelf",         true,  false, new int[]{19, 19, 66, 66, 66, 66}, 1.5f, -1);

    // Note block (solid, opaque)
    public static final Block NOTE_BLOCK        = new Block(94, "note_block",        true,  false, new int[]{67},              0.8f, -1);

    // Jukebox (solid, opaque)
    public static final Block JUKEBOX           = new Block(95, "jukebox",           true,  false, new int[]{68, 67, 69, 69, 69, 69}, 2.0f, -1);

    // Pumpkin (solid, opaque)
    public static final Block PUMPKIN           = new Block(96, "pumpkin",           true,  false, new int[]{70, 70, 71, 72, 71, 71}, 1.0f, -1);

    // Jack o'lantern (solid, opaque, light emission 15)
    public static final Block JACK_O_LANTERN   = new Block(97, "jack_o_lantern",    true,  false, new int[]{70, 70, 71, 73, 71, 71}, 1.0f, -1);



    // ---- Stub blocks (from other agent refs) ----
    public static final Block LEATHER_HELMET       = new Block(98, "leather_helmet", false, true, new int[]{0}, 0.0f, -1);
    public static final Block LEATHER_CHESTPLATE   = new Block(99, "leather_chestplate", false, true, new int[]{0}, 0.0f, -1);
    public static final Block LEATHER_LEGGINGS     = new Block(100, "leather_leggings", false, true, new int[]{0}, 0.0f, -1);
    public static final Block LEATHER_BOOTS        = new Block(101, "leather_boots", false, true, new int[]{0}, 0.0f, -1);
    public static final Block IRON_HELMET          = new Block(102, "iron_helmet", false, true, new int[]{0}, 0.0f, -1);
    public static final Block IRON_CHESTPLATE      = new Block(103, "iron_chestplate", false, true, new int[]{0}, 0.0f, -1);
    public static final Block IRON_LEGGINGS        = new Block(104, "iron_leggings", false, true, new int[]{0}, 0.0f, -1);
    public static final Block IRON_BOOTS           = new Block(105, "iron_boots", false, true, new int[]{0}, 0.0f, -1);
    public static final Block DIAMOND_HELMET       = new Block(106, "diamond_helmet", false, true, new int[]{0}, 0.0f, -1);
    public static final Block DIAMOND_CHESTPLATE   = new Block(107, "diamond_chestplate", false, true, new int[]{0}, 0.0f, -1);
    public static final Block DIAMOND_LEGGINGS     = new Block(108, "diamond_leggings", false, true, new int[]{0}, 0.0f, -1);
    public static final Block DIAMOND_BOOTS        = new Block(109, "diamond_boots", false, true, new int[]{0}, 0.0f, -1);
    public static final Block GOLD_HELMET          = new Block(110, "gold_helmet", false, true, new int[]{0}, 0.0f, -1);
    public static final Block GOLD_CHESTPLATE      = new Block(111, "gold_chestplate", false, true, new int[]{0}, 0.0f, -1);
    public static final Block GOLD_LEGGINGS        = new Block(112, "gold_leggings", false, true, new int[]{0}, 0.0f, -1);
    public static final Block GOLD_BOOTS           = new Block(113, "gold_boots", false, true, new int[]{0}, 0.0f, -1);
    public static final Block FARMLAND             = new Block(114, "farmland", true, false, new int[]{0}, 0.0f, -1);
    public static final Block SUGAR_CANE           = new Block(115, "sugar_cane", false, true, new int[]{0}, 0.0f, -1);
    public static final Block WHEAT_CROP_0         = new Block(116, "wheat_crop_0", false, true, new int[]{0}, 0.0f, -1);
    public static final Block WHEAT_CROP_1         = new Block(117, "wheat_crop_1", false, true, new int[]{0}, 0.0f, -1);
    public static final Block WHEAT_CROP_2         = new Block(118, "wheat_crop_2", false, true, new int[]{0}, 0.0f, -1);
    public static final Block WHEAT_CROP_3         = new Block(119, "wheat_crop_3", false, true, new int[]{0}, 0.0f, -1);
    public static final Block WHEAT_CROP_4         = new Block(120, "wheat_crop_4", false, true, new int[]{0}, 0.0f, -1);
    public static final Block WHEAT_CROP_5         = new Block(121, "wheat_crop_5", false, true, new int[]{0}, 0.0f, -1);
    public static final Block WHEAT_CROP_6         = new Block(122, "wheat_crop_6", false, true, new int[]{0}, 0.0f, -1);
    public static final Block WHEAT_CROP_7         = new Block(123, "wheat_crop_7", false, true, new int[]{0}, 0.0f, -1);
    public static final Block LEATHER              = new Block(124, "leather", false, true, new int[]{0}, 0.0f, -1);
    public static final Block RAW_BEEF             = new Block(125, "raw_beef", false, true, new int[]{0}, 0.0f, -1);
    public static final Block COOKED_BEEF          = new Block(126, "cooked_beef", false, true, new int[]{0}, 0.0f, -1);
    public static final Block WOOL                 = new Block(127, "wool", true, false, new int[]{0}, 0.8f, -1);

    /** All blocks indexed by ID for fast lookup. */
    private static final Block[] REGISTRY;

    static {
        REGISTRY = new Block[128]; // IDs 0-127
        REGISTRY[0]  = AIR;          REGISTRY[1]  = STONE;         REGISTRY[2]  = COBBLESTONE;
        REGISTRY[3]  = DIRT;         REGISTRY[4]  = GRASS;         REGISTRY[5]  = SAND;
        REGISTRY[6]  = GRAVEL;       REGISTRY[7]  = LOG;           REGISTRY[8]  = LEAVES;
        REGISTRY[9]  = WATER;        REGISTRY[10] = COAL_ORE;      REGISTRY[11] = IRON_ORE;
        REGISTRY[12] = GOLD_ORE;     REGISTRY[13] = DIAMOND_ORE;   REGISTRY[14] = BEDROCK;
        REGISTRY[15] = RAW_PORKCHOP; REGISTRY[16] = ROTTEN_FLESH;  REGISTRY[17] = PLANKS;
        REGISTRY[18] = CRAFTING_TABLE; REGISTRY[19] = STICK;       REGISTRY[20] = WOODEN_PICKAXE;
        REGISTRY[21] = WOODEN_AXE;   REGISTRY[22] = WOODEN_SHOVEL; REGISTRY[23] = STONE_PICKAXE;
        REGISTRY[24] = STONE_AXE;    REGISTRY[25] = STONE_SHOVEL;  REGISTRY[26] = CHEST;
        REGISTRY[27] = RAIL;         REGISTRY[28] = TNT;           REGISTRY[29] = BOAT_ITEM;
        REGISTRY[30] = MINECART_ITEM; REGISTRY[31] = FURNACE;      REGISTRY[32] = TORCH;
        REGISTRY[33] = COAL;         REGISTRY[34] = IRON_INGOT;    REGISTRY[35] = GLASS;
        REGISTRY[36] = COOKED_PORKCHOP; REGISTRY[37] = RED_FLOWER; REGISTRY[38] = YELLOW_FLOWER;
        REGISTRY[39] = DIAMOND;      REGISTRY[40] = IRON_PICKAXE;  REGISTRY[41] = IRON_AXE;
        REGISTRY[42] = IRON_SHOVEL;  REGISTRY[43] = IRON_SWORD;    REGISTRY[44] = WOODEN_SWORD;
        REGISTRY[45] = STONE_SWORD;  REGISTRY[46] = CHARCOAL;      REGISTRY[47] = GOLD_INGOT;
        REGISTRY[48] = POWERED_RAIL; REGISTRY[49] = REDSTONE;      REGISTRY[50] = REDSTONE_WIRE;
        REGISTRY[51] = REDSTONE_TORCH; REGISTRY[52] = REDSTONE_REPEATER; REGISTRY[53] = REDSTONE_ORE;
        // Fluid blocks
        REGISTRY[54] = FLOWING_WATER_1; REGISTRY[55] = FLOWING_WATER_2; REGISTRY[56] = FLOWING_WATER_3;
        REGISTRY[57] = FLOWING_WATER_4; REGISTRY[58] = FLOWING_WATER_5; REGISTRY[59] = FLOWING_WATER_6;
        REGISTRY[60] = FLOWING_WATER_7;
        REGISTRY[61] = LAVA;
        REGISTRY[62] = FLOWING_LAVA_1; REGISTRY[63] = FLOWING_LAVA_2; REGISTRY[64] = FLOWING_LAVA_3;
        REGISTRY[65] = FLOWING_LAVA_4; REGISTRY[66] = FLOWING_LAVA_5; REGISTRY[67] = FLOWING_LAVA_6;
        REGISTRY[68] = FLOWING_LAVA_7;
        REGISTRY[69] = OBSIDIAN;
        // Alpha building blocks
        REGISTRY[70] = OAK_DOOR;        REGISTRY[71] = OAK_DOOR_TOP;    REGISTRY[72] = IRON_DOOR;
        REGISTRY[73] = IRON_DOOR_TOP;   REGISTRY[74] = COBBLESTONE_STAIRS; REGISTRY[75] = OAK_STAIRS;
        REGISTRY[76] = STONE_SLAB;      REGISTRY[77] = OAK_SLAB;        REGISTRY[78] = OAK_FENCE;
        REGISTRY[79] = LADDER;          REGISTRY[80] = SIGN;            REGISTRY[81] = BED_FOOT;
        REGISTRY[82] = BED_HEAD;        REGISTRY[83] = WOOD_PRESSURE_PLATE; REGISTRY[84] = STONE_PRESSURE_PLATE;
        REGISTRY[85] = WOOD_BUTTON;     REGISTRY[86] = STONE_BUTTON;    REGISTRY[87] = LEVER;
        REGISTRY[88] = SNOW_LAYER;      REGISTRY[89] = ICE;             REGISTRY[90] = CLAY;
        REGISTRY[91] = BRICK;           REGISTRY[92] = SPONGE;          REGISTRY[93] = BOOKSHELF;
        REGISTRY[94] = NOTE_BLOCK;      REGISTRY[95] = JUKEBOX;         REGISTRY[96] = PUMPKIN;
        REGISTRY[97] = JACK_O_LANTERN;
        REGISTRY[98] = LEATHER_HELMET;
        REGISTRY[99] = LEATHER_CHESTPLATE;
        REGISTRY[100] = LEATHER_LEGGINGS;
        REGISTRY[101] = LEATHER_BOOTS;
        REGISTRY[102] = IRON_HELMET;
        REGISTRY[103] = IRON_CHESTPLATE;
        REGISTRY[104] = IRON_LEGGINGS;
        REGISTRY[105] = IRON_BOOTS;
        REGISTRY[106] = DIAMOND_HELMET;
        REGISTRY[107] = DIAMOND_CHESTPLATE;
        REGISTRY[108] = DIAMOND_LEGGINGS;
        REGISTRY[109] = DIAMOND_BOOTS;
        REGISTRY[110] = GOLD_HELMET;
        REGISTRY[111] = GOLD_CHESTPLATE;
        REGISTRY[112] = GOLD_LEGGINGS;
        REGISTRY[113] = GOLD_BOOTS;
        REGISTRY[114] = FARMLAND;
        REGISTRY[115] = SUGAR_CANE;
        REGISTRY[116] = WHEAT_CROP_0;
        REGISTRY[117] = WHEAT_CROP_1;
        REGISTRY[118] = WHEAT_CROP_2;
        REGISTRY[119] = WHEAT_CROP_3;
        REGISTRY[120] = WHEAT_CROP_4;
        REGISTRY[121] = WHEAT_CROP_5;
        REGISTRY[122] = WHEAT_CROP_6;
        REGISTRY[123] = WHEAT_CROP_7;
        REGISTRY[124] = LEATHER;
        REGISTRY[125] = RAW_BEEF;
        REGISTRY[126] = COOKED_BEEF;
        REGISTRY[127] = WOOL;
    }

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
     * Returns 0 for most blocks. Torches emit 14, lava/flowing lava emit 15.
     */
    public static int getLightEmission(int blockId) {
        if (blockId == TORCH.id()) return 14;
        if (blockId == REDSTONE_TORCH.id()) return 7;
        if (blockId == JACK_O_LANTERN.id()) return 15;
        if (isLava(blockId)) return 15;
        return 0;
    }

    // ======== Fluid helper methods (Infdev 611) ========

    /**
     * Check if a block is any form of water (source or flowing).
     */
    public static boolean isWater(int blockId) {
        return blockId == WATER.id() || (blockId >= FLOWING_WATER_1.id() && blockId <= FLOWING_WATER_7.id());
    }

    /**
     * Check if a block is any form of lava (source or flowing).
     */
    public static boolean isLava(int blockId) {
        return blockId == LAVA.id() || (blockId >= FLOWING_LAVA_1.id() && blockId <= FLOWING_LAVA_7.id());
    }

    /**
     * Check if a block is any fluid (water or lava).
     */
    public static boolean isFluid(int blockId) {
        return isWater(blockId) || isLava(blockId);
    }

    /**
     * Check if a block is a fluid source (still water or still lava).
     */
    public static boolean isFluidSource(int blockId) {
        return blockId == WATER.id() || blockId == LAVA.id();
    }

    /**
     * Get the fluid level for a block. Source = 0, flowing = 1-7.
     * Returns -1 if the block is not a fluid.
     */
    public static int getFluidLevel(int blockId) {
        if (blockId == WATER.id()) return 0;
        if (blockId >= FLOWING_WATER_1.id() && blockId <= FLOWING_WATER_7.id()) return blockId - 53; // 1-7
        if (blockId == LAVA.id()) return 0;
        if (blockId >= FLOWING_LAVA_1.id() && blockId <= FLOWING_LAVA_7.id()) return blockId - 61; // 1-7
        return -1;
    }

    /**
     * Get the water level. Returns -1 if not water.
     */
    public static int getWaterLevel(int blockId) {
        if (blockId == WATER.id()) return 0;
        if (blockId >= FLOWING_WATER_1.id() && blockId <= FLOWING_WATER_7.id()) return blockId - 53;
        return -1;
    }

    /**
     * Get the lava level. Returns -1 if not lava.
     */
    public static int getLavaLevel(int blockId) {
        if (blockId == LAVA.id()) return 0;
        if (blockId >= FLOWING_LAVA_1.id() && blockId <= FLOWING_LAVA_7.id()) return blockId - 61;
        return -1;
    }

    /**
     * Get the block ID for flowing water at a given level (1-7).
     */
    public static int flowingWaterId(int level) {
        if (level <= 0) return WATER.id();
        if (level > 7) return 0; // air - too far
        return 53 + level; // IDs 54-60
    }

    /**
     * Get the block ID for flowing lava at a given level (1-7).
     */
    public static int flowingLavaId(int level) {
        if (level <= 0) return LAVA.id();
        if (level > 7) return 0;
        return 61 + level; // IDs 62-68
    }

    /**
     * Get the rendered height of a fluid block (0.0 to 1.0).
     * Source blocks are 14/16 high. Flowing blocks decrease with level.
     */
    public static float getFluidHeight(int blockId) {
        int level = getFluidLevel(blockId);
        if (level < 0) return 1.0f; // not a fluid
        // Source = 14/16, level 1 = 12/16, ... level 7 = 2/16 (minimum)
        return Math.max(2.0f, 14.0f - level * 2.0f) / 16.0f;
    }

    /**
     * Check if a fluid can flow into the given block.
     * Fluids can replace air and non-solid placeables (flowers, torches, etc.).
     */
    public static boolean canFluidReplace(int blockId) {
        if (blockId == AIR.id()) return true;
        Block block = get(blockId);
        return !block.solid() && !isFluid(blockId);
    }

    /**
     * Check if a block is a non-solid "placeable" (like torches, flowers, rails).
     * These can be placed on top of/against solid blocks but don't block movement.
     */
    public static boolean isNonSolidPlaceable(int blockId) {
        return blockId == TORCH.id()
            || blockId == RED_FLOWER.id()
            || blockId == YELLOW_FLOWER.id()
            || blockId == RAIL.id()
            || blockId == POWERED_RAIL.id()
            || blockId == REDSTONE_WIRE.id()
            || blockId == REDSTONE_TORCH.id()
            || blockId == REDSTONE_REPEATER.id()
            || blockId == LADDER.id()
            || blockId == SIGN.id()
            || blockId == WOOD_PRESSURE_PLATE.id()
            || blockId == STONE_PRESSURE_PLATE.id()
            || blockId == WOOD_BUTTON.id()
            || blockId == STONE_BUTTON.id()
            || blockId == LEVER.id()
            || blockId == SNOW_LAYER.id();
    }

    /**
     * Check if a block is a rail type (regular or powered).
     */
    public static boolean isRail(int blockId) {
        return blockId == RAIL.id() || blockId == POWERED_RAIL.id();
    }

    /**
     * Check if a block is a redstone component.
     */
    public static boolean isRedstoneComponent(int blockId) {
        return blockId == REDSTONE_WIRE.id()
            || blockId == REDSTONE_TORCH.id()
            || blockId == REDSTONE_REPEATER.id();
    }

    /**
     * Check if a block is a flower.
     */
    public static boolean isFlower(int blockId) {
        return blockId == RED_FLOWER.id() || blockId == YELLOW_FLOWER.id();
    }

    // ======== Alpha building block helpers ========

    /** Check if a block is a door (any part). */
    public static boolean isDoor(int blockId) {
        return blockId == OAK_DOOR.id() || blockId == OAK_DOOR_TOP.id()
            || blockId == IRON_DOOR.id() || blockId == IRON_DOOR_TOP.id();
    }

    /** Check if a block is a door bottom half. */
    public static boolean isDoorBottom(int blockId) {
        return blockId == OAK_DOOR.id() || blockId == IRON_DOOR.id();
    }

    /** Check if a block is stairs. */
    public static boolean isStairs(int blockId) {
        return blockId == COBBLESTONE_STAIRS.id() || blockId == OAK_STAIRS.id();
    }

    /** Check if a block is a slab (half-height). */
    public static boolean isSlab(int blockId) {
        return blockId == STONE_SLAB.id() || blockId == OAK_SLAB.id();
    }

    /** Check if a block is a fence. */
    public static boolean isFence(int blockId) {
        return blockId == OAK_FENCE.id();
    }

    /** Check if a block is a pressure plate. */
    public static boolean isPressurePlate(int blockId) {
        return blockId == WOOD_PRESSURE_PLATE.id() || blockId == STONE_PRESSURE_PLATE.id();
    }

    /** Check if a block is a button. */
    public static boolean isButton(int blockId) {
        return blockId == WOOD_BUTTON.id() || blockId == STONE_BUTTON.id();
    }

    /** Check if a block is a bed (any part). */
    public static boolean isBed(int blockId) {
        return blockId == BED_FOOT.id() || blockId == BED_HEAD.id();
    }

    /** Check if a block is climbable (ladder). */
    public static boolean isClimbable(int blockId) {
        return blockId == LADDER.id();
    }

    /** Check if a block is ice (slippery). */
    public static boolean isIce(int blockId) {
        return blockId == ICE.id();
    }

    public static boolean isWheatCrop(int blockId) {
        return blockId >= WHEAT_CROP_0.id() && blockId <= WHEAT_CROP_7.id();
    }
    public static int getWheatStage(int blockId) {
        if (!isWheatCrop(blockId)) return -1;
        return blockId - WHEAT_CROP_0.id();
    }
    public static int wheatCropId(int stage) {
        if (stage < 0) stage = 0;
        if (stage > 7) stage = 7;
        return WHEAT_CROP_0.id() + stage;
    }
}
