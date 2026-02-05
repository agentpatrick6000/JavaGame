package com.voxelgame.sim;

import com.voxelgame.world.Blocks;

public final class ToolItem {

    public enum BlockMaterial { STONE, WOOD, DIRT, NONE }
    public enum ToolType { PICKAXE, AXE, SHOVEL, HOE, NONE }

    public enum ToolTier {
        WOOD(60, 2.0f),
        STONE(132, 4.0f),
        IRON(251, 6.0f),
        NONE(0, 1.0f);

        public final int durability;
        public final float speedMultiplier;

        ToolTier(int durability, float speedMultiplier) {
            this.durability = durability;
            this.speedMultiplier = speedMultiplier;
        }
    }

    private final int blockId;
    private final ToolType type;
    private final ToolTier tier;
    private final String displayName;

    private ToolItem(int blockId, ToolType type, ToolTier tier, String displayName) {
        this.blockId = blockId;
        this.type = type;
        this.tier = tier;
        this.displayName = displayName;
    }

    public int getBlockId() { return blockId; }
    public ToolType getType() { return type; }
    public ToolTier getTier() { return tier; }
    public String getDisplayName() { return displayName; }
    public int getMaxDurability() { return tier.durability; }
    public float getSpeedMultiplier() { return tier.speedMultiplier; }

    private static final ToolItem[] TOOLS = {
        new ToolItem(Blocks.WOODEN_PICKAXE.id(), ToolType.PICKAXE, ToolTier.WOOD, "Wooden Pickaxe"),
        new ToolItem(Blocks.WOODEN_AXE.id(),     ToolType.AXE,     ToolTier.WOOD, "Wooden Axe"),
        new ToolItem(Blocks.WOODEN_SHOVEL.id(),   ToolType.SHOVEL,  ToolTier.WOOD, "Wooden Shovel"),
        new ToolItem(Blocks.STONE_PICKAXE.id(),   ToolType.PICKAXE, ToolTier.STONE, "Stone Pickaxe"),
        new ToolItem(Blocks.STONE_AXE.id(),       ToolType.AXE,     ToolTier.STONE, "Stone Axe"),
        new ToolItem(Blocks.STONE_SHOVEL.id(),    ToolType.SHOVEL,  ToolTier.STONE, "Stone Shovel"),
        new ToolItem(Blocks.IRON_PICKAXE.id(),    ToolType.PICKAXE, ToolTier.IRON, "Iron Pickaxe"),
        new ToolItem(Blocks.IRON_AXE.id(),        ToolType.AXE,     ToolTier.IRON, "Iron Axe"),
        new ToolItem(Blocks.IRON_SHOVEL.id(),     ToolType.SHOVEL,  ToolTier.IRON, "Iron Shovel"),
        new ToolItem(Blocks.IRON_SWORD.id(),      ToolType.NONE,    ToolTier.IRON, "Iron Sword"),
        new ToolItem(Blocks.WOODEN_SWORD.id(),    ToolType.NONE,    ToolTier.WOOD, "Wooden Sword"),
        new ToolItem(Blocks.STONE_SWORD.id(),     ToolType.NONE,    ToolTier.STONE, "Stone Sword"),
        new ToolItem(Blocks.WOODEN_HOE.id(),      ToolType.HOE,     ToolTier.WOOD, "Wooden Hoe"),
        new ToolItem(Blocks.FLINT_AND_STEEL.id(), ToolType.NONE,    ToolTier.WOOD, "Flint and Steel"),  // 64 uses like wood tier
    };

    public static boolean isTool(int blockId) { return get(blockId) != null; }

    public static ToolItem get(int blockId) {
        for (ToolItem tool : TOOLS) {
            if (tool.blockId == blockId) return tool;
        }
        return null;
    }

    public static int getMaxDurability(int blockId) {
        ToolItem tool = get(blockId);
        return tool != null ? tool.getMaxDurability() : -1;
    }

    public static float getEffectiveness(int toolBlockId, int targetBlockId) {
        ToolItem tool = get(toolBlockId);
        if (tool == null) return 1.0f;
        BlockMaterial targetMat = getBlockMaterial(targetBlockId);
        if (targetMat == BlockMaterial.NONE) return 1.0f;
        boolean effective;
        switch (tool.type) {
            case PICKAXE: effective = targetMat == BlockMaterial.STONE; break;
            case AXE:     effective = targetMat == BlockMaterial.WOOD; break;
            case SHOVEL:  effective = targetMat == BlockMaterial.DIRT; break;
            default:      effective = false; break;
        }
        return effective ? tool.getSpeedMultiplier() : 1.0f;
    }

    public static BlockMaterial getBlockMaterial(int blockId) {
        if (blockId == Blocks.STONE.id() || blockId == Blocks.COBBLESTONE.id() ||
            blockId == Blocks.COAL_ORE.id() || blockId == Blocks.IRON_ORE.id() ||
            blockId == Blocks.GOLD_ORE.id() || blockId == Blocks.DIAMOND_ORE.id() ||
            blockId == Blocks.FURNACE.id()) {
            return BlockMaterial.STONE;
        }
        if (blockId == Blocks.LOG.id() || blockId == Blocks.PLANKS.id() ||
            blockId == Blocks.CRAFTING_TABLE.id() || blockId == Blocks.CHEST.id()) {
            return BlockMaterial.WOOD;
        }
        if (blockId == Blocks.DIRT.id() || blockId == Blocks.GRASS.id() ||
            blockId == Blocks.SAND.id() || blockId == Blocks.GRAVEL.id()) {
            return BlockMaterial.DIRT;
        }
        return BlockMaterial.NONE;
    }

    public static String getDisplayName(int blockId) {
        ToolItem tool = get(blockId);
        if (tool != null) return tool.displayName;
        if (ArmorItem.isArmor(blockId)) return ArmorItem.getDisplayName(blockId);
        return Blocks.get(blockId).name();
    }
}
