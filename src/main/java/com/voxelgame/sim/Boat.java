package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;

/**
 * Boat entity — rideable, floats on water, fast water travel.
 * Right-click to mount/dismount. WASD controls while mounted.
 * Floats on water blocks; moves at 2× walk speed on water.
 */
public class Boat extends Entity {

    private static final float BOAT_HALF_WIDTH = 0.5f;
    private static final float BOAT_HEIGHT = 0.5f;
    private static final float BOAT_MAX_HEALTH = 20.0f;

    /** Movement speed on water (2× walk speed). */
    private static final float WATER_SPEED = 8.6f;
    /** Movement speed on land (slower). */
    private static final float LAND_SPEED = 2.0f;
    /** Buoyancy force when in water. */
    private static final float BUOYANCY = 20.0f;
    /** Water friction (slows boat when not actively moving). */
    private static final float WATER_FRICTION = 0.92f;

    /** Whether a player is riding this boat. */
    private boolean mounted = false;

    /** Forward/strafe input from rider. */
    private float inputForward = 0;
    private float inputStrafe = 0;

    /** Whether the boat is currently on water. */
    private boolean onWater = false;

    public Boat(float x, float y, float z) {
        super(EntityType.BOAT, x, y, z, BOAT_HALF_WIDTH, BOAT_HEIGHT, BOAT_MAX_HEALTH);
    }

    @Override
    public void update(float dt, World world, Player player) {
        age += dt;
        if (hurtTimer > 0) hurtTimer -= dt;

        // Check if boat is on water
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        int blockBelow = (by >= 0 && by < WorldConstants.WORLD_HEIGHT) ? world.getBlock(bx, by, bz) : 0;
        int blockAt = (by >= 0 && by < WorldConstants.WORLD_HEIGHT) ? world.getBlock(bx, by, bz) : 0;
        onWater = (Blocks.isWater(blockBelow) || Blocks.isWater(blockAt));

        // Check water one block below too
        if (!onWater && by - 1 >= 0) {
            onWater = Blocks.isWater(world.getBlock(bx, by - 1, bz));
        }

        if (mounted) {
            updateMounted(dt, world);
        } else {
            updateUnmounted(dt, world);
        }
    }

    private void updateMounted(float dt, World world) {
        float speed = onWater ? WATER_SPEED : LAND_SPEED;

        // Convert yaw to direction
        float yawRad = (float) Math.toRadians(yaw);
        float frontX = -(float) Math.sin(yawRad);
        float frontZ = (float) Math.cos(yawRad);
        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) Math.sin(yawRad);

        // Apply movement input
        float moveX = frontX * inputForward + rightX * inputStrafe;
        float moveZ = frontZ * inputForward + rightZ * inputStrafe;

        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (len > 0.001f) {
            moveX /= len;
            moveZ /= len;
            vx += moveX * speed * dt * 5.0f;
            vz += moveZ * speed * dt * 5.0f;
        }

        // Clamp horizontal speed
        float hSpeed = (float) Math.sqrt(vx * vx + vz * vz);
        if (hSpeed > speed) {
            float scale = speed / hSpeed;
            vx *= scale;
            vz *= scale;
        }

        // Buoyancy / gravity
        if (onWater) {
            // Float at water surface
            int waterSurfaceY = findWaterSurface(world);
            float targetY = waterSurfaceY + 0.6f; // slightly above water
            if (y < targetY) {
                vy += BUOYANCY * dt;
            } else {
                vy *= 0.8f; // dampen
            }
            // Water friction
            vx *= WATER_FRICTION;
            vz *= WATER_FRICTION;
        } else {
            // Regular gravity on land
            vy -= GRAVITY * dt;
        }

        // Apply velocity with collision
        moveWithCollision(dt, world);

        // Reset input
        inputForward = 0;
        inputStrafe = 0;
    }

    private void updateUnmounted(float dt, World world) {
        // Buoyancy when on water (stay floating)
        if (onWater) {
            int waterSurfaceY = findWaterSurface(world);
            float targetY = waterSurfaceY + 0.6f;
            if (y < targetY) {
                vy += BUOYANCY * dt;
            } else {
                vy *= 0.8f;
            }
            vx *= 0.95f;
            vz *= 0.95f;
        } else {
            vy -= GRAVITY * dt;
        }

        moveWithCollision(dt, world);
    }

    private int findWaterSurface(World world) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int surfaceY = (int) Math.floor(y);

        // Search upward for the top of water
        for (int sy = surfaceY; sy < WorldConstants.WORLD_HEIGHT; sy++) {
            if (!Blocks.isWater(world.getBlock(bx, sy, bz))) {
                return sy - 1;
            }
        }
        return surfaceY;
    }

    @Override
    public void onDeath(ItemEntityManager itemManager) {
        // Drop boat item
        itemManager.spawnDrop(Blocks.BOAT_ITEM.id(), 1, x, y, z);
    }

    // ---- Riding ----

    public boolean isMounted() { return mounted; }

    public void mount() {
        mounted = true;
        System.out.println("[Boat] Player mounted boat");
    }

    public void dismount() {
        mounted = false;
        inputForward = 0;
        inputStrafe = 0;
        System.out.println("[Boat] Player dismounted boat");
    }

    public void setInput(float forward, float strafe) {
        this.inputForward = forward;
        this.inputStrafe = strafe;
    }

    public void setRiderYaw(float yaw) {
        this.yaw = yaw;
    }

    public boolean isOnWater() { return onWater; }

    @Override
    public boolean shouldDespawn(Player player) {
        return false; // Boats don't despawn
    }
}
