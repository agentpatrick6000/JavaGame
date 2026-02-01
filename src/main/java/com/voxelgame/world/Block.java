package com.voxelgame.world;

/**
 * Block type definition — id, name, properties, per-face texture indices,
 * mining hardness, and drop information.
 *
 * Hardness values (seconds to mine by hand):
 *   -1 = unbreakable (bedrock)
 *    0 = instant break (leaves, etc.)
 *   >0 = time in seconds
 *
 * Drop ID:
 *   -1 = drops itself (same block ID)
 *   0  = drops nothing (air / leaves)
 *   >0 = drops that specific block ID
 */
public record Block(int id, String name, boolean solid, boolean transparent,
                    int[] faceTextures, float hardness, int dropId) {

    /**
     * Backward-compatible constructor (defaults: hardness=1.0, dropId=-1 = drops self).
     */
    public Block(int id, String name, boolean solid, boolean transparent, int[] faceTextures) {
        this(id, name, solid, transparent, faceTextures, 1.0f, -1);
    }

    /**
     * Face texture array order: [top, bottom, north, south, east, west].
     * A single-element array means all faces use the same texture index.
     */
    public int getTextureIndex(int face) {
        if (faceTextures.length == 1) return faceTextures[0];
        return faceTextures[face];
    }

    /**
     * Get the block ID that drops when this block is broken.
     * Returns 0 (AIR) if nothing drops, or the specific drop block ID.
     */
    public int getDrop() {
        if (dropId == -1) return id;  // drops itself
        return dropId;                // drops specified block (0 = nothing)
    }

    /**
     * Whether this block is breakable (hardness >= 0).
     */
    public boolean isBreakable() {
        return hardness >= 0;
    }

    /**
     * Whether this block is unbreakable (bedrock, water, etc.).
     */
    public boolean isUnbreakable() {
        return hardness < 0;
    }

    /**
     * Get the time in seconds to break this block by hand.
     * Returns Float.MAX_VALUE for unbreakable blocks.
     */
    public float getBreakTime() {
        if (hardness < 0) return Float.MAX_VALUE;
        return hardness;
    }
}
