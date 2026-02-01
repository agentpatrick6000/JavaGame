package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;

/**
 * A dropped item entity that floats in the world.
 * Has physics (gravity + ground collision), rotation animation,
 * and despawns after 5 minutes.
 */
public class ItemEntity {

    /** Hitbox half-size (item entities are small cubes). */
    public static final float HALF_SIZE = 0.15f;
    public static final float SIZE = HALF_SIZE * 2;

    /** Despawn time in seconds (5 minutes). */
    public static final float DESPAWN_TIME = 300.0f;

    /** Pickup delay (can't pick up immediately after spawning). */
    public static final float PICKUP_DELAY = 0.5f;

    /** Collection distance from player center. */
    public static final float PICKUP_RANGE = 1.5f;

    /** Gravity for item entities (lighter than player). */
    private static final float GRAVITY = 20.0f;

    /** Bounce factor when hitting ground. */
    private static final float BOUNCE = 0.3f;

    /** Friction applied each frame. */
    private static final float FRICTION = 0.95f;

    // Position
    private float x, y, z;
    // Velocity
    private float vx, vy, vz;
    // What item this represents
    private int blockId;
    private int count;
    // Animation
    private float rotation = 0;
    private float bobPhase;
    // Lifetime
    private float age = 0;
    private boolean dead = false;

    public ItemEntity(int blockId, int count, float x, float y, float z) {
        this.blockId = blockId;
        this.count = count;
        this.x = x;
        this.y = y;
        this.z = z;
        this.bobPhase = (float) (Math.random() * Math.PI * 2);

        // Random initial velocity (pop out of broken block)
        this.vx = (float) (Math.random() - 0.5) * 4.0f;
        this.vy = 3.0f + (float) Math.random() * 2.0f;
        this.vz = (float) (Math.random() - 0.5) * 4.0f;
    }

    /**
     * Update physics and animation.
     */
    public void update(float dt, World world) {
        if (dead) return;

        age += dt;
        if (age >= DESPAWN_TIME) {
            dead = true;
            return;
        }

        // Rotation animation
        rotation += 90.0f * dt; // 90 degrees per second
        if (rotation > 360.0f) rotation -= 360.0f;

        // Gravity
        vy -= GRAVITY * dt;

        // Apply velocity
        float nx = x + vx * dt;
        float ny = y + vy * dt;
        float nz = z + vz * dt;

        // Simple ground collision
        if (world != null) {
            // Check block below feet
            int bx = (int) Math.floor(nx);
            int by = (int) Math.floor(ny - HALF_SIZE);
            int bz = (int) Math.floor(nz);

            if (by >= 0 && Blocks.get(world.getBlock(bx, by, bz)).solid()) {
                // Hit ground
                ny = by + 1.0f + HALF_SIZE;
                if (vy < -0.5f) {
                    vy = -vy * BOUNCE;
                } else {
                    vy = 0;
                }
            }

            // Check if entity fell into void
            if (ny < -100) {
                dead = true;
                return;
            }
        }

        // Apply friction to horizontal velocity
        vx *= FRICTION;
        vz *= FRICTION;

        // Clamp very small velocities
        if (Math.abs(vx) < 0.01f) vx = 0;
        if (Math.abs(vz) < 0.01f) vz = 0;

        x = nx;
        y = ny;
        z = nz;
    }

    /**
     * Check if this item can be picked up (past delay period).
     */
    public boolean canPickUp() {
        return !dead && age >= PICKUP_DELAY;
    }

    /**
     * Check if the player is close enough to pick up this item.
     */
    public boolean isInPickupRange(float px, float py, float pz, float eyeHeight) {
        float feetY = py - eyeHeight;
        float centerY = feetY + eyeHeight * 0.5f; // approximate player center

        float dx = x - px;
        float dy = y - centerY;
        float dz = z - pz;
        float distSq = dx * dx + dy * dy + dz * dz;

        return distSq <= PICKUP_RANGE * PICKUP_RANGE;
    }

    /**
     * Get the Y offset for bobbing animation.
     */
    public float getBobOffset() {
        return (float) Math.sin(age * 2.0 + bobPhase) * 0.05f;
    }

    // --- Getters ---
    public float getX() { return x; }
    public float getY() { return y + getBobOffset(); } // includes bob
    public float getRawY() { return y; }
    public float getZ() { return z; }
    public float getRotation() { return rotation; }
    public int getBlockId() { return blockId; }
    public int getCount() { return count; }
    public boolean isDead() { return dead; }
    public float getAge() { return age; }

    public void kill() { dead = true; }

    /**
     * Merge another item entity's count into this one (if same type and not full).
     * Returns true if merged successfully.
     */
    public boolean tryMerge(ItemEntity other) {
        if (other.blockId != this.blockId) return false;
        if (this.count >= Inventory.MAX_STACK) return false;

        int canFit = Inventory.MAX_STACK - this.count;
        int toMerge = Math.min(canFit, other.count);
        this.count += toMerge;
        other.count -= toMerge;
        if (other.count <= 0) other.dead = true;
        return true;
    }
}
