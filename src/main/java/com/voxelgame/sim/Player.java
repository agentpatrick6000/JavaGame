package com.voxelgame.sim;

import com.voxelgame.render.Camera;
import org.joml.Vector3f;

/**
 * Player entity. Holds position (at eye level via Camera), velocity,
 * on-ground state, fly mode, and selected block.
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

    private final Camera camera;
    private final Vector3f velocity = new Vector3f();
    private boolean flyMode = true;   // start in fly mode
    private boolean onGround = false;
    private int selectedBlock = 1;    // stone by default

    public Player() {
        this.camera = new Camera();
        camera.updateVectors();
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

    // --- Block selection ---

    public int getSelectedBlock() { return selectedBlock; }
    public void setSelectedBlock(int block) { this.selectedBlock = block; }
}
