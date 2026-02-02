package com.voxelgame.sim;

import com.voxelgame.world.Blocks;

import java.util.*;

/**
 * Registry of all items/blocks available in the creative inventory.
 * Items are organized by category for tabbed display.
 * 
 * Each entry has: block/item ID, display name, category.
 */
public final class ItemRegistry {

    private ItemRegistry() {}

    /** Item categories for the creative inventory tabs. */
    public enum Category {
        ALL("All"),
        BUILDING("Building"),
        NATURAL("Natural"),
        ORES("Ores"),
        FUNCTIONAL("Functional"),
        TOOLS("Tools"),
        MATERIALS("Materials"),
        FOOD("Food"),
        VEHICLES("Vehicles");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    /** A single entry in the creative inventory. */
    public record Entry(int blockId, String displayName, Category category) {}

    /** All registered creative inventory items, in display order. */
    private static final List<Entry> ALL_ENTRIES = new ArrayList<>();

    /** Items grouped by category. */
    private static final Map<Category, List<Entry>> BY_CATEGORY = new EnumMap<>(Category.class);

    static {
        // Initialize category lists
        for (Category cat : Category.values()) {
            BY_CATEGORY.put(cat, new ArrayList<>());
        }

        // ---- Building Blocks ----
        register(Blocks.STONE.id(),        "Stone",           Category.BUILDING);
        register(Blocks.COBBLESTONE.id(),   "Cobblestone",     Category.BUILDING);
        register(Blocks.DIRT.id(),          "Dirt",            Category.BUILDING);
        register(Blocks.GRASS.id(),         "Grass",           Category.BUILDING);
        register(Blocks.SAND.id(),          "Sand",            Category.BUILDING);
        register(Blocks.GRAVEL.id(),        "Gravel",          Category.BUILDING);
        register(Blocks.LOG.id(),           "Log",             Category.BUILDING);
        register(Blocks.PLANKS.id(),        "Planks",          Category.BUILDING);
        register(Blocks.GLASS.id(),         "Glass",           Category.BUILDING);
        register(Blocks.BEDROCK.id(),       "Bedrock",         Category.BUILDING);

        // ---- Natural ----
        register(Blocks.LEAVES.id(),        "Leaves",          Category.NATURAL);
        register(Blocks.RED_FLOWER.id(),    "Red Flower",      Category.NATURAL);
        register(Blocks.YELLOW_FLOWER.id(), "Yellow Flower",   Category.NATURAL);
        register(Blocks.WATER.id(),         "Water",           Category.NATURAL);

        // ---- Ores ----
        register(Blocks.COAL_ORE.id(),      "Coal Ore",        Category.ORES);
        register(Blocks.IRON_ORE.id(),      "Iron Ore",        Category.ORES);
        register(Blocks.GOLD_ORE.id(),      "Gold Ore",        Category.ORES);
        register(Blocks.DIAMOND_ORE.id(),   "Diamond Ore",     Category.ORES);

        // ---- Functional ----
        register(Blocks.TORCH.id(),           "Torch",           Category.FUNCTIONAL);
        register(Blocks.CRAFTING_TABLE.id(),  "Crafting Table",  Category.FUNCTIONAL);
        register(Blocks.FURNACE.id(),         "Furnace",         Category.FUNCTIONAL);
        register(Blocks.CHEST.id(),           "Chest",           Category.FUNCTIONAL);
        register(Blocks.TNT.id(),             "TNT",             Category.FUNCTIONAL);
        register(Blocks.RAIL.id(),            "Rail",            Category.FUNCTIONAL);

        // ---- Tools ----
        register(Blocks.WOODEN_PICKAXE.id(),  "Wooden Pickaxe",  Category.TOOLS);
        register(Blocks.WOODEN_AXE.id(),      "Wooden Axe",      Category.TOOLS);
        register(Blocks.WOODEN_SHOVEL.id(),   "Wooden Shovel",   Category.TOOLS);
        register(Blocks.WOODEN_SWORD.id(),    "Wooden Sword",    Category.TOOLS);
        register(Blocks.STONE_PICKAXE.id(),   "Stone Pickaxe",   Category.TOOLS);
        register(Blocks.STONE_AXE.id(),       "Stone Axe",       Category.TOOLS);
        register(Blocks.STONE_SHOVEL.id(),    "Stone Shovel",    Category.TOOLS);
        register(Blocks.STONE_SWORD.id(),     "Stone Sword",     Category.TOOLS);
        register(Blocks.IRON_PICKAXE.id(),    "Iron Pickaxe",    Category.TOOLS);
        register(Blocks.IRON_AXE.id(),        "Iron Axe",        Category.TOOLS);
        register(Blocks.IRON_SHOVEL.id(),     "Iron Shovel",     Category.TOOLS);
        register(Blocks.IRON_SWORD.id(),      "Iron Sword",      Category.TOOLS);

        // ---- Materials ----
        register(Blocks.STICK.id(),         "Stick",           Category.MATERIALS);
        register(Blocks.COAL.id(),          "Coal",            Category.MATERIALS);
        register(Blocks.CHARCOAL.id(),      "Charcoal",        Category.MATERIALS);
        register(Blocks.IRON_INGOT.id(),    "Iron Ingot",      Category.MATERIALS);
        register(Blocks.DIAMOND.id(),       "Diamond",         Category.MATERIALS);

        // ---- Food ----
        register(Blocks.RAW_PORKCHOP.id(),    "Raw Porkchop",    Category.FOOD);
        register(Blocks.COOKED_PORKCHOP.id(), "Cooked Porkchop", Category.FOOD);
        register(Blocks.ROTTEN_FLESH.id(),    "Rotten Flesh",    Category.FOOD);

        // ---- Vehicles ----
        register(Blocks.BOAT_ITEM.id(),     "Boat",            Category.VEHICLES);
        register(Blocks.MINECART_ITEM.id(), "Minecart",        Category.VEHICLES);

        // Build the ALL category (everything)
        BY_CATEGORY.get(Category.ALL).addAll(ALL_ENTRIES);
    }

    private static void register(int blockId, String displayName, Category category) {
        Entry entry = new Entry(blockId, displayName, category);
        ALL_ENTRIES.add(entry);
        BY_CATEGORY.get(category).add(entry);
    }

    /** Get all items in a category. */
    public static List<Entry> getItems(Category category) {
        return Collections.unmodifiableList(BY_CATEGORY.getOrDefault(category, List.of()));
    }

    /** Get all items (every category). */
    public static List<Entry> getAllItems() {
        return Collections.unmodifiableList(ALL_ENTRIES);
    }

    /** Get all categories that have at least one item. */
    public static List<Category> getCategories() {
        List<Category> result = new ArrayList<>();
        for (Category cat : Category.values()) {
            if (!BY_CATEGORY.get(cat).isEmpty()) {
                result.add(cat);
            }
        }
        return result;
    }

    /** Get the display name for a block/item ID. */
    public static String getDisplayName(int blockId) {
        for (Entry e : ALL_ENTRIES) {
            if (e.blockId() == blockId) return e.displayName();
        }
        // Fallback
        if (ToolItem.isTool(blockId)) return ToolItem.getDisplayName(blockId);
        return Blocks.get(blockId).name();
    }

    /** Total number of registered creative items. */
    public static int count() {
        return ALL_ENTRIES.size();
    }
}
