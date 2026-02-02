package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * Zombie — a hostile mob.  (Infdev 611 parity)
 *
 * AI: Targets and chases the player within 16 blocks. Attacks on contact.
 *     When player is out of range, wanders randomly.
 *     Tries to jump over obstacles when stuck chasing.
 * Health: 20 HP.
 * Attack: 2 HP per hit (every 1 second).
 * Drops: 0-2 feathers on death (Infdev 611 drop — changed to rotten flesh in Beta 1.8).
 * Appearance: Green skin, tattered clothes, red eyes, arms extended forward.
 * Spawns: At night in dark areas (light < 7).
 *
 * Model proportions (humanoid, Infdev 611 style):
 *   Head: 0.5 × 0.5 × 0.5
 *   Body: 0.5 × 0.75 × 0.25
 *   Arms: 0.25 × 0.75 × 0.25 (extended forward, zombie pose)
 *   Legs: 0.25 × 0.75 × 0.25
 */
public class Zombie extends Entity {

    // ---- Dimensions (humanoid hitbox) ----
    private static final float HALF_WIDTH  = 0.3f;   // 0.6 wide
    private static final float HEIGHT_VAL  = 1.95f;  // tall humanoid
    private static final float MAX_HP      = 20.0f;

    // ---- Movement ----
    private static final float WALK_SPEED  = 1.2f;   // blocks/sec (wandering)
    private static final float CHASE_SPEED = 2.5f;   // blocks/sec (chasing player)

    // ---- Targeting ----
    private static final float TARGET_RANGE    = 16.0f;
    private static final float TARGET_RANGE_SQ = TARGET_RANGE * TARGET_RANGE;

    // ---- Attack ----
    private static final float ATTACK_DAMAGE   = 2.0f;   // per hit (Infdev 611)
    private static final float ATTACK_INTERVAL = 1.0f;   // seconds between attacks
    private float attackTimer = 0;

    // ---- Wander state (used when player is out of range) ----
    private float wanderTimer = 0;
    private boolean isWandering = false;
    private float wanderDirX = 0, wanderDirZ = 0;

    // ---- Stuck detection (improved) ----
    private float stuckTimer = 0;
    private float stuckCheckTimer = 0;
    private float lastCheckX, lastCheckZ;

    public Zombie(float x, float y, float z) {
        super(EntityType.ZOMBIE, x, y, z, HALF_WIDTH, HEIGHT_VAL, MAX_HP);
        lastCheckX = x;
        lastCheckZ = z;
    }

    @Override
    public void update(float dt, World world, Player player) {
        if (dead) return;
        age += dt;

        // ---- Check distance to player ----
        Vector3f pPos = player.getPosition();
        float dx = pPos.x - x;
        float dz = pPos.z - z;
        float distSq = dx * dx + dz * dz;

        boolean playerInRange = distSq < TARGET_RANGE_SQ && !player.isDead();

        if (playerInRange) {
            // ---- Chase player ----
            float dist = (float) Math.sqrt(distSq);
            if (dist > 0.5f) {
                float ndx = dx / dist;
                float ndz = dz / dist;

                vx = ndx * CHASE_SPEED;
                vz = ndz * CHASE_SPEED;

                // Face the player
                yaw = (float) Math.toDegrees(Math.atan2(ndx, ndz));
            }

            // ---- Attack on contact ----
            if (isCollidingWithPlayer(player)) {
                attackTimer += dt;
                if (attackTimer >= ATTACK_INTERVAL) {
                    player.damage(ATTACK_DAMAGE, DamageSource.MOB);
                    attackTimer = 0;
                }
            } else {
                attackTimer = 0;
            }

            // ---- Improved stuck detection ----
            stuckCheckTimer += dt;
            if (stuckCheckTimer >= 1.0f) {
                float movedX = x - lastCheckX;
                float movedZ = z - lastCheckZ;
                float movedDistSq = movedX * movedX + movedZ * movedZ;

                if (movedDistSq < 0.25f) {
                    // Barely moved in 1 second while chasing — try jumping
                    stuckTimer += 1.0f;
                    if (stuckTimer >= 1.0f) {
                        jump();
                        stuckTimer = 0;
                    }
                } else {
                    stuckTimer = 0;
                }

                lastCheckX = x;
                lastCheckZ = z;
                stuckCheckTimer = 0;
            }
        } else {
            // ---- Wander when player is out of range ----
            attackTimer = 0;
            stuckTimer = 0;
            stuckCheckTimer = 0;
            wanderTimer -= dt;

            if (wanderTimer <= 0) {
                if (isWandering) {
                    isWandering = false;
                    wanderTimer = 3.0f + random.nextFloat() * 4.0f;
                } else {
                    isWandering = true;
                    wanderTimer = 1.0f + random.nextFloat() * 2.0f;
                    float angle = random.nextFloat() * (float) (Math.PI * 2);
                    wanderDirX = (float) Math.sin(angle);
                    wanderDirZ = (float) Math.cos(angle);
                }
            }

            if (isWandering) {
                vx = wanderDirX * WALK_SPEED;
                vz = wanderDirZ * WALK_SPEED;
                yaw = (float) Math.toDegrees(Math.atan2(wanderDirX, wanderDirZ));
            } else {
                vx = 0;
                vz = 0;
            }
        }

        moveWithCollision(dt, world);
    }

    @Override
    public void onDeath(ItemEntityManager itemManager) {
        // Infdev 611: Zombies drop 0-2 feathers
        int count = random.nextInt(3); // 0-2
        if (count > 0) {
            itemManager.spawnDrop(Blocks.FEATHER.id(), count, x, y, z);
        }
        System.out.printf("[Mob] Zombie died at (%.1f, %.1f, %.1f), dropped %d feather(s)%n",
                x, y, z, count);
    }
}
