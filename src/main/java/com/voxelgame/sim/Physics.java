package com.voxelgame.sim;

import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * Physics simulation. Applies gravity, terminal velocity clamping,
 * and delegates to Collision for world-aware movement resolution.
 * In fly mode, physics is bypassed (controller moves player directly).
 */
public class Physics {

    public static final float GRAVITY          = 32.0f;   // blocks/s²
    public static final float TERMINAL_VELOCITY = 78.0f;  // blocks/s (max fall speed)
    public static final float JUMP_VELOCITY     = 9.0f;   // blocks/s upward (~1.27 block jump)

    private World world;

    public void setWorld(World world) {
        this.world = world;
    }

    /**
     * Advance physics one step.
     * Applies gravity, then resolves collisions and integrates position.
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

        // --- Collision resolution + position integration ---
        if (world != null) {
            Collision.resolveMovement(pos, vel, dt, world, player);
        } else {
            // No world reference: raw integration (for testing)
            pos.x += vel.x * dt;
            pos.y += vel.y * dt;
            pos.z += vel.z * dt;
        }
    }
}
