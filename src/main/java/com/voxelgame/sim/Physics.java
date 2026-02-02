package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import org.joml.Vector3f;

/**
 * Physics simulation. Applies gravity, terminal velocity clamping,
 * and delegates to Collision for world-aware movement resolution.
 * In fly mode, physics is bypassed (controller moves player directly).
 *
 * Also tracks fall distance and applies fall damage on landing.
 * Includes swimming mechanics: buoyancy, reduced speed, oxygen/drowning.
 */
public class Physics {

    // Authentic Minecraft Infdev 611 physics constants
    public static final float GRAVITY          = 31.36f;  // blocks/s² (matches MC: 0.08 blocks/tick * 20²)
    public static final float TERMINAL_VELOCITY = 78.4f;  // blocks/s (MC: 3.92 blocks/tick * 20)
    public static final float JUMP_VELOCITY     = 8.4f;   // blocks/s upward (~1.25 block jump height)

    /** Minimum fall distance (blocks) before damage is applied. */
    private static final float FALL_DAMAGE_THRESHOLD = 3.0f;
    /** Damage per block fallen beyond threshold. */
    private static final float FALL_DAMAGE_PER_BLOCK = 2.0f;
    /** Void death Y level (feet below this = instant death). */
    private static final float VOID_Y = -64.0f;

    // ---- Swimming constants ----
    /** Speed multiplier when in water. */
    public static final float WATER_SPEED_MULTIPLIER = 0.5f;
    /** Gravity when in water (reduced, acts as buoyancy-like). */
    private static final float WATER_GRAVITY = 6.0f;
    /** Upward buoyancy speed when pressing space in water. */
    private static final float WATER_SWIM_UP_SPEED = 4.0f;
    /** Terminal velocity in water (much lower). */
    private static final float WATER_TERMINAL_VELOCITY = 8.0f;
    /** Maximum oxygen in seconds. */
    public static final float MAX_OXYGEN = 10.0f;
    /** Drowning damage per second when out of oxygen. */
    private static final float DROWNING_DAMAGE_PER_SECOND = 2.0f;

    // ---- Lava constants ----
    /** Speed multiplier when in lava. */
    public static final float LAVA_SPEED_MULTIPLIER = 0.3f;
    /** Gravity when in lava (very slow sinking). */
    private static final float LAVA_GRAVITY = 4.0f;
    /** Terminal velocity in lava. */
    private static final float LAVA_TERMINAL_VELOCITY = 4.0f;
    /** Lava damage per second. */
    private static final float LAVA_DAMAGE_PER_SECOND = 4.0f;

    private World world;

    public void setWorld(World world) {
        this.world = world;
    }

    /**
     * Check if a position is inside a water block (source or flowing).
     */
    public boolean isInWater(float x, float y, float z) {
        if (world == null) return false;
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        if (by < 0 || by >= WorldConstants.WORLD_HEIGHT) return false;
        return Blocks.isWater(world.getBlock(bx, by, bz));
    }

    /**
     * Check if a position is inside a lava block (source or flowing).
     */
    public boolean isInLava(float x, float y, float z) {
        if (world == null) return false;
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        if (by < 0 || by >= WorldConstants.WORLD_HEIGHT) return false;
        return Blocks.isLava(world.getBlock(bx, by, bz));
    }

    /**
     * Advance physics one step.
     * Applies gravity, then resolves collisions and integrates position.
     * Tracks fall distance and applies fall damage on landing.
     * Handles swimming, buoyancy, and drowning.
     */
    public void step(Player player, float dt) {
        if (player.isDead()) return; // No physics when dead

        // Void check — instant kill if below void level
        float feetY = player.getPosition().y - Player.EYE_HEIGHT;
        if (feetY < VOID_Y) {
            player.damage(999.0f, DamageSource.VOID);
            return;
        }

        if (player.isFlyMode()) {
            player.resetFallTracking();
            player.setInWater(false);
            player.setInLava(false);
            return;
        }

        Vector3f pos = player.getPosition();
        Vector3f vel = player.getVelocity();

        // ---- Water detection ----
        boolean feetInWater = isInWater(pos.x, pos.y - Player.EYE_HEIGHT + 0.1f, pos.z);
        boolean headInWater = isInWater(pos.x, pos.y, pos.z);
        boolean bodyInWater = feetInWater || isInWater(pos.x, pos.y - Player.EYE_HEIGHT + 0.9f, pos.z);
        player.setInWater(bodyInWater);
        player.setHeadUnderwater(headInWater);

        // ---- Lava detection ----
        boolean feetInLava = isInLava(pos.x, pos.y - Player.EYE_HEIGHT + 0.1f, pos.z);
        boolean bodyInLava = feetInLava || isInLava(pos.x, pos.y - Player.EYE_HEIGHT + 0.9f, pos.z);
        player.setInLava(bodyInLava);

        // ---- Oxygen / Drowning ----
        if (headInWater) {
            player.drainOxygen(dt);
            if (player.getOxygen() <= 0) {
                player.damage(DROWNING_DAMAGE_PER_SECOND * dt, DamageSource.DROWNING);
            }
        } else {
            player.refillOxygen();
        }

        // ---- Lava damage ----
        if (bodyInLava) {
            player.damage(LAVA_DAMAGE_PER_SECOND * dt, DamageSource.LAVA);
        }

        // Record pre-step ground state for fall detection
        boolean wasOnGround = player.isOnGround();

        if (bodyInLava) {
            // ---- Lava physics: very slow movement ----
            vel.y -= LAVA_GRAVITY * dt;
            if (vel.y < -LAVA_TERMINAL_VELOCITY) {
                vel.y = -LAVA_TERMINAL_VELOCITY;
            }
            vel.x *= (1.0f - 5.0f * dt);
            vel.z *= (1.0f - 5.0f * dt);

            // Lava breaks fall
            if (player.isTrackingFall()) {
                player.landAndGetFallDistance(feetY);
            }
        } else if (bodyInWater) {
            // ---- Water physics ----
            vel.y -= WATER_GRAVITY * dt;
            if (vel.y < -WATER_TERMINAL_VELOCITY) {
                vel.y = -WATER_TERMINAL_VELOCITY;
            }
            vel.x *= (1.0f - 3.0f * dt);
            vel.z *= (1.0f - 3.0f * dt);

            // Reset fall tracking when in water (water breaks fall)
            if (player.isTrackingFall()) {
                player.landAndGetFallDistance(feetY);
            }
        } else {
            // ---- Normal physics ----
            vel.y -= GRAVITY * dt;
            if (vel.y < -TERMINAL_VELOCITY) {
                vel.y = -TERMINAL_VELOCITY;
            }
        }

        // --- Collision resolution + position integration ---
        if (world != null) {
            Collision.resolveMovement(player.getPosition(), vel, dt, world, player);
        } else {
            // No world reference: raw integration (for testing)
            pos.x += vel.x * dt;
            pos.y += vel.y * dt;
            pos.z += vel.z * dt;
        }

        // --- Fall tracking & damage ---
        feetY = player.getPosition().y - Player.EYE_HEIGHT;

        if (!bodyInWater) {
            if (player.isOnGround()) {
                if (player.isTrackingFall()) {
                    float fallDistance = player.landAndGetFallDistance(feetY);
                    if (fallDistance > FALL_DAMAGE_THRESHOLD) {
                        float damage = (fallDistance - FALL_DAMAGE_THRESHOLD) * FALL_DAMAGE_PER_BLOCK;
                        player.damage(damage, DamageSource.FALL);
                    }
                }
            } else {
                player.updateFallTracking(feetY);
            }
        }
    }
}
