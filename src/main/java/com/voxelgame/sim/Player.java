package com.voxelgame.sim;

import com.voxelgame.render.Camera;
import com.voxelgame.world.Blocks;
import org.joml.Vector3f;

/**
 * Player entity. Holds position (at eye level via Camera), velocity,
 * on-ground state, fly mode, and hotbar with block selection.
 *
 * Position convention: getPosition() returns the eye-level position.
 * The player hitbox extends from (pos.y - EYE_HEIGHT) to (pos.y - EYE_HEIGHT + HEIGHT).
 */
public class Player {

    /** Player hitbox dimensions. */
    public static final float WIDTH      = 0.6f;
    public static final float HEIGHT     = 1.8f;
    public static final float EYE_HEIGHT = 1.62f;
    public static final float HALF_WIDTH = WIDTH / 2.0f;  // 0.3

    /** Number of hotbar slots. */
    public static final int HOTBAR_SIZE = 9;

    private final Camera camera;
    private final Vector3f velocity = new Vector3f();
    private boolean flyMode = false;  // start in walk mode with physics/collision
    private boolean onGround = false;

    /** Hotbar: block IDs for each slot. */
    private final int[] hotbar = new int[HOTBAR_SIZE];
    /** Currently selected hotbar slot (0-based). */
    private int selectedSlot = 0;

    public Player() {
        this.camera = new Camera();
        camera.updateVectors();
        initHotbar();
    }

    private void initHotbar() {
        hotbar[0] = Blocks.STONE.id();
        hotbar[1] = Blocks.COBBLESTONE.id();
        hotbar[2] = Blocks.DIRT.id();
        hotbar[3] = Blocks.GRASS.id();
        hotbar[4] = Blocks.SAND.id();
        hotbar[5] = Blocks.LOG.id();
        hotbar[6] = Blocks.LEAVES.id();
        hotbar[7] = Blocks.GRAVEL.id();
        hotbar[8] = Blocks.WATER.id();
    }

    // --- Camera / Position ---

    public Camera getCamera() { return camera; }

    /** Eye-level position. Feet are at y - EYE_HEIGHT. */
    public Vector3f getPosition() { return camera.getPosition(); }

    // --- Velocity ---

    public Vector3f getVelocity() { return velocity; }

    // --- Fly mode ---

    public boolean isFlyMode() { return flyMode; }
    public void setFlyMode(boolean fly) { this.flyMode = fly; }
    public void toggleFlyMode() {
        this.flyMode = !this.flyMode;
        // Zero velocity on mode switch to prevent jarring movement
        velocity.set(0);
    }

    // --- Ground state ---

    public boolean isOnGround() { return onGround; }
    public void setOnGround(boolean og) { this.onGround = og; }

    // --- Jump ---

    /**
     * Attempt a jump. Only succeeds if on ground and not in fly mode.
     */
    public void jump() {
        if (onGround && !flyMode) {
            velocity.y = Physics.JUMP_VELOCITY;
            onGround = false;
        }
    }

    // --- Hotbar / Block selection ---

    /** Get the block ID in the currently selected hotbar slot. */
    public int getSelectedBlock() {
        return hotbar[selectedSlot];
    }

    /** Get the currently selected hotbar slot index (0-based). */
    public int getSelectedSlot() { return selectedSlot; }

    /** Set the selected hotbar slot index (0-based, clamped to valid range). */
    public void setSelectedSlot(int slot) {
        this.selectedSlot = Math.max(0, Math.min(HOTBAR_SIZE - 1, slot));
    }

    /** Cycle the selected slot by delta (positive = right, negative = left). */
    public void cycleSelectedSlot(int delta) {
        selectedSlot = ((selectedSlot - delta) % HOTBAR_SIZE + HOTBAR_SIZE) % HOTBAR_SIZE;
    }

    /** Get the block ID at a specific hotbar slot. */
    public int getHotbarBlock(int slot) {
        if (slot < 0 || slot >= HOTBAR_SIZE) return 0;
        return hotbar[slot];
    }

    /** Set the block ID at a specific hotbar slot. */
    public void setHotbarBlock(int slot, int blockId) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            hotbar[slot] = blockId;
        }
    }

    /** For backward compatibility. */
    public void setSelectedBlock(int block) {
        hotbar[selectedSlot] = block;
    }
}
