package com.voxelgame.sim;

import com.voxelgame.world.Blocks;

public final class ArmorItem {
    private ArmorItem() {}

    public enum Slot {
        HELMET(0), CHESTPLATE(1), LEGGINGS(2), BOOTS(3);
        public final int index;
        Slot(int index) { this.index = index; }
    }

    public enum Material {
        LEATHER("Leather", 1.5f, 56, 81, 76, 66, new float[]{0.55f, 0.35f, 0.15f}),
        IRON("Iron", 2.0f, 166, 241, 226, 196, new float[]{0.78f, 0.78f, 0.80f}),
        DIAMOND("Diamond", 2.5f, 364, 529, 496, 430, new float[]{0.39f, 0.86f, 1.00f}),
        GOLD("Gold", 2.0f, 78, 113, 106, 92, new float[]{0.95f, 0.80f, 0.20f});

        public final String displayName;
        public final float defensePerPiece;
        public final int helmetDur, chestDur, leggingsDur, bootsDur;
        public final float[] color;

        Material(String dn, float def, int hd, int cd, int ld, int bd, float[] c) {
            this.displayName = dn; this.defensePerPiece = def;
            this.helmetDur = hd; this.chestDur = cd; this.leggingsDur = ld; this.bootsDur = bd;
            this.color = c;
        }
        public int getDurability(Slot slot) {
            return switch (slot) {
                case HELMET -> helmetDur; case CHESTPLATE -> chestDur;
                case LEGGINGS -> leggingsDur; case BOOTS -> bootsDur;
            };
        }
    }

    private record Piece(int blockId, Slot slot, Material material, String displayName) {}

    private static final Piece[] PIECES = {
        new Piece(Blocks.LEATHER_HELMET.id(), Slot.HELMET, Material.LEATHER, "Leather Helmet"),
        new Piece(Blocks.LEATHER_CHESTPLATE.id(), Slot.CHESTPLATE, Material.LEATHER, "Leather Chestplate"),
        new Piece(Blocks.LEATHER_LEGGINGS.id(), Slot.LEGGINGS, Material.LEATHER, "Leather Leggings"),
        new Piece(Blocks.LEATHER_BOOTS.id(), Slot.BOOTS, Material.LEATHER, "Leather Boots"),
        new Piece(Blocks.IRON_HELMET.id(), Slot.HELMET, Material.IRON, "Iron Helmet"),
        new Piece(Blocks.IRON_CHESTPLATE.id(), Slot.CHESTPLATE, Material.IRON, "Iron Chestplate"),
        new Piece(Blocks.IRON_LEGGINGS.id(), Slot.LEGGINGS, Material.IRON, "Iron Leggings"),
        new Piece(Blocks.IRON_BOOTS.id(), Slot.BOOTS, Material.IRON, "Iron Boots"),
        new Piece(Blocks.DIAMOND_HELMET.id(), Slot.HELMET, Material.DIAMOND, "Diamond Helmet"),
        new Piece(Blocks.DIAMOND_CHESTPLATE.id(), Slot.CHESTPLATE, Material.DIAMOND, "Diamond Chestplate"),
        new Piece(Blocks.DIAMOND_LEGGINGS.id(), Slot.LEGGINGS, Material.DIAMOND, "Diamond Leggings"),
        new Piece(Blocks.DIAMOND_BOOTS.id(), Slot.BOOTS, Material.DIAMOND, "Diamond Boots"),
        new Piece(Blocks.GOLD_HELMET.id(), Slot.HELMET, Material.GOLD, "Gold Helmet"),
        new Piece(Blocks.GOLD_CHESTPLATE.id(), Slot.CHESTPLATE, Material.GOLD, "Gold Chestplate"),
        new Piece(Blocks.GOLD_LEGGINGS.id(), Slot.LEGGINGS, Material.GOLD, "Gold Leggings"),
        new Piece(Blocks.GOLD_BOOTS.id(), Slot.BOOTS, Material.GOLD, "Gold Boots"),
    };

    public static boolean isArmor(int blockId) { return get(blockId) != null; }
    private static Piece get(int blockId) {
        for (Piece p : PIECES) if (p.blockId == blockId) return p;
        return null;
    }
    public static Slot getSlot(int blockId) { Piece p = get(blockId); return p != null ? p.slot : null; }
    public static Material getMaterial(int blockId) { Piece p = get(blockId); return p != null ? p.material : null; }
    public static float getDefense(int blockId) { Piece p = get(blockId); return p != null ? p.material.defensePerPiece : 0; }
    public static int getMaxDurability(int blockId) { Piece p = get(blockId); return p != null ? p.material.getDurability(p.slot) : -1; }
    public static String getDisplayName(int blockId) { Piece p = get(blockId); return p != null ? p.displayName : Blocks.get(blockId).name(); }
    public static float[] getColor(int blockId) { Piece p = get(blockId); return p != null ? p.material.color : new float[]{0.5f, 0.5f, 0.5f}; }
    public static float calculateDamageReduction(float totalDefense) { return Math.min(0.80f, totalDefense / 25.0f); }
}
