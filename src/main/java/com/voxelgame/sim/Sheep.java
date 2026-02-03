package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;

/**
 * Sheep - Passive mob, wanders randomly, flees when attacked.
 * Drops: wool (1) when killed, or can be sheared (not implemented yet).
 * Special: After eating grass, wool regrows (if sheared).
 */
public class Sheep extends Entity {
    private static final float HALF_WIDTH = 0.45f;
    private static final float HEIGHT_VAL = 1.3f;
    private static final float MAX_HP = 8.0f;
    private static final float WALK_SPEED = 1.2f;
    private static final float FLEE_SPEED = 4.0f;

    // Wander AI
    private float wanderTimer = 0;
    private boolean isWandering = false;
    private float wanderDirX = 0, wanderDirZ = 0;

    // Flee AI
    private boolean fleeing = false;
    private float fleeTimer = 0;
    private float fleeDirX = 0, fleeDirZ = 0;
    private float panicTimer = 0;

    // Wool state
    private boolean hasWool = true;
    private float eatTimer = 0; // regrow wool after 30s if eaten grass

    public Sheep(float x, float y, float z) {
        super(EntityType.SHEEP, x, y, z, HALF_WIDTH, HEIGHT_VAL, MAX_HP);
        wanderTimer = 2.0f + random.nextFloat() * 3.0f;
    }

    public boolean hasWool() {
        return hasWool;
    }

    @Override
    public void damage(float amount, float knockbackX, float knockbackZ) {
        super.damage(amount, knockbackX, knockbackZ);
        
        if (!dead) {
            // Start fleeing
            fleeing = true;
            fleeTimer = 3.0f + random.nextFloat() * 2.0f;
            panicTimer = 0;
            
            // Flee away from damage source
            float len = (float) Math.sqrt(knockbackX * knockbackX + knockbackZ * knockbackZ);
            if (len > 0.01f) {
                fleeDirX = knockbackX / len;
                fleeDirZ = knockbackZ / len;
            } else {
                // Random direction if no knockback vector
                float angle = random.nextFloat() * (float) (Math.PI * 2);
                fleeDirX = (float) Math.sin(angle);
                fleeDirZ = (float) Math.cos(angle);
            }
        }
    }

    @Override
    public void update(float dt, World world, Player player) {
        if (dead) return;
        
        age += dt;

        // Wool regrow timer (if sheared)
        if (!hasWool) {
            eatTimer += dt;
            if (eatTimer >= 30.0f) {
                // Wool regrew
                hasWool = true;
                eatTimer = 0;
            }
        }

        if (fleeing) {
            fleeTimer -= dt;
            if (fleeTimer <= 0) {
                // Stop fleeing
                fleeing = false;
                wanderTimer = 2.0f + random.nextFloat() * 3.0f;
                isWandering = false;
            } else {
                // Panic: jitter flee direction
                panicTimer -= dt;
                if (panicTimer <= 0) {
                    panicTimer = 0.3f + random.nextFloat() * 0.5f;
                    fleeDirX += (random.nextFloat() - 0.5f) * 0.8f;
                    fleeDirZ += (random.nextFloat() - 0.5f) * 0.8f;
                    float len = (float) Math.sqrt(fleeDirX * fleeDirX + fleeDirZ * fleeDirZ);
                    if (len > 0.01f) {
                        fleeDirX /= len;
                        fleeDirZ /= len;
                    }
                }
                
                vx = fleeDirX * FLEE_SPEED;
                vz = fleeDirZ * FLEE_SPEED;
                yaw = (float) Math.toDegrees(Math.atan2(fleeDirX, fleeDirZ));
                
                // Jump if stuck
                if (onGround && Math.abs(vx) < 0.05f && Math.abs(vz) < 0.05f) {
                    jump();
                    panicTimer = 0;
                }
            }
        } else {
            // Wander AI
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                if (isWandering) {
                    // Stop wandering
                    isWandering = false;
                    wanderTimer = 2.0f + random.nextFloat() * 3.0f;
                } else {
                    // Start wandering
                    isWandering = true;
                    wanderTimer = 1.0f + random.nextFloat() * 3.0f;
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
        // Drop wool if has wool
        if (hasWool) {
            itemManager.spawnDrop(Blocks.WOOL.id(), 1, x, y, z);
        }
    }
}
