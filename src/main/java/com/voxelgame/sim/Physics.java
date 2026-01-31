package com.voxelgame.sim;

import org.joml.Vector3f;

/**
 * Physics simulation. Applies gravity, terminal velocity clamping,
 * and velocity integration for walk-mode movement.
 * In fly mode, physics is bypassed (controller moves player directly).
 */
public class Physics {

    public static final float GRAVITY          = 32.0f;   // blocks/s²
    public static final float TERMINAL_VELOCITY = 78.0f;  // blocks/s (max fall speed)
    public static final float JUMP_VELOCITY     = 9.0f;   // blocks/s upward (~1.27 block jump)

    /**
     * Advance physics one step.
     * Applies gravity to Y velocity, clamps to terminal velocity,
     * and integrates position from velocity.
     *
     * @param player the player entity
     * @param dt     delta time in seconds
     */
    public void step(Player player, float dt) {
        if (player.isFlyMode()) return;

        Vector3f vel = player.getVelocity();
        Vector3f pos = player.getPosition();

        // --- Gravity ---
        vel.y -= GRAVITY * dt;
        if (vel.y < -TERMINAL_VELOCITY) {
            vel.y = -TERMINAL_VELOCITY;
        }

        // --- Integrate position ---
        pos.x += vel.x * dt;
        pos.y += vel.y * dt;
        pos.z += vel.z * dt;
    }
}
