package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * AABB collision detection and resolution against the voxel world.
 *
 * Player hitbox: 0.6 wide × 1.8 tall × 0.6 deep.
 * Position convention: getPosition() = eye level.
 *   feet  = pos.y - EYE_HEIGHT
 *   head  = pos.y - EYE_HEIGHT + HEIGHT  (= pos.y + 0.18)
 *
 * Resolution: sweep axis by axis (Y first for ground detection, then X, then Z)
 * to prevent tunneling. On each axis, clip velocity to the first collision.
 */
public class Collision {

    private static final float EPSILON = 0.001f;

    /**
     * Resolve movement with collision against the world.
     * Modifies pos and vel in-place.
     *
     * @param pos   eye-level position (modified)
     * @param vel   velocity (modified — zeroed on collision axes)
     * @param dt    delta time
     * @param world the block world
     * @param player the player (for setting onGround)
     */
    public static void resolveMovement(Vector3f pos, Vector3f vel, float dt, World world, Player player) {
        float dx = vel.x * dt;
        float dy = vel.y * dt;
        float dz = vel.z * dt;

        // Current AABB (before movement)
        float halfW = Player.HALF_WIDTH;       // 0.3
        float eyeH  = Player.EYE_HEIGHT;       // 1.62
        float headH = Player.HEIGHT - eyeH;    // 0.18

        // --- Resolve Y axis first (most important for ground detection) ---
        if (dy != 0) {
            float clipped = sweepAxisY(pos, halfW, eyeH, headH, dy, world);
            if (Math.abs(clipped - dy) > EPSILON) {
                // Collision on Y
                if (vel.y < 0) {
                    player.setOnGround(true);
                }
                vel.y = 0;
                dy = clipped;
            } else {
                // No Y collision — airborne (unless we're on ground with 0 vel)
                if (dy < -EPSILON) {
                    player.setOnGround(false);
                }
            }
            pos.y += clipped;
        } else {
            // Check if we're still on ground (standing still, no Y velocity)
            // Probe a tiny distance below feet
            float probe = sweepAxisY(pos, halfW, eyeH, headH, -0.05f, world);
            player.setOnGround(Math.abs(probe - (-0.05f)) > EPSILON);
        }

        // --- Resolve X axis ---
        if (dx != 0) {
            float clipped = sweepAxisX(pos, halfW, eyeH, headH, dx, world);
            if (Math.abs(clipped - dx) > EPSILON) {
                vel.x = 0;
                dx = clipped;
            }
            pos.x += clipped;
        }

        // --- Resolve Z axis ---
        if (dz != 0) {
            float clipped = sweepAxisZ(pos, halfW, eyeH, headH, dz, world);
            if (Math.abs(clipped - dz) > EPSILON) {
                vel.z = 0;
                dz = clipped;
            }
            pos.z += clipped;
        }
    }

    // ---- Y-axis sweep ----

    private static float sweepAxisY(Vector3f pos, float halfW, float eyeH, float headH,
                                     float dy, World world) {
        if (dy == 0) return 0;

        float minX = pos.x - halfW;
        float maxX = pos.x + halfW;
        float minZ = pos.z - halfW;
        float maxZ = pos.z + halfW;

        // Current feet and head Y
        float feetY = pos.y - eyeH;
        float headY = pos.y + headH;

        // Block range to check (X and Z)
        int bx0 = floor(minX + EPSILON);
        int bx1 = floor(maxX - EPSILON);
        int bz0 = floor(minZ + EPSILON);
        int bz1 = floor(maxZ - EPSILON);

        float result = dy;

        if (dy < 0) {
            // Moving down — check blocks below feet
            float targetFeetY = feetY + dy;
            int by0 = floor(targetFeetY + EPSILON);
            int by1 = floor(feetY - EPSILON);

            for (int bx = bx0; bx <= bx1; bx++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int by = by1; by >= by0; by--) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockTop = by + 1.0f;
                            float maxDy = blockTop - feetY; // negative or zero
                            if (maxDy > result) {
                                result = maxDy;
                            }
                        }
                    }
                }
            }
        } else {
            // Moving up — check blocks above head
            float targetHeadY = headY + dy;
            int by0 = floor(headY + EPSILON);
            int by1 = floor(targetHeadY - EPSILON);

            for (int bx = bx0; bx <= bx1; bx++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockBottom = (float) by;
                            float maxDy = blockBottom - headY; // positive or zero
                            if (maxDy < result) {
                                result = maxDy;
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    // ---- X-axis sweep ----

    private static float sweepAxisX(Vector3f pos, float halfW, float eyeH, float headH,
                                     float dx, World world) {
        if (dx == 0) return 0;

        float minZ = pos.z - halfW;
        float maxZ = pos.z + halfW;
        float feetY = pos.y - eyeH;
        float headY = pos.y + headH;

        int bz0 = floor(minZ + EPSILON);
        int bz1 = floor(maxZ - EPSILON);
        int by0 = floor(feetY + EPSILON);
        int by1 = floor(headY - EPSILON);

        float result = dx;

        if (dx < 0) {
            // Moving in -X
            float edgeX = pos.x - halfW;
            float targetX = edgeX + dx;
            int bxFrom = floor(targetX + EPSILON);
            int bxTo   = floor(edgeX - EPSILON);

            for (int bx = bxTo; bx >= bxFrom; bx--) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockRight = bx + 1.0f;
                            float maxDx = blockRight - edgeX + EPSILON;
                            if (maxDx > result) {
                                result = maxDx;
                            }
                        }
                    }
                }
            }
        } else {
            // Moving in +X
            float edgeX = pos.x + halfW;
            float targetX = edgeX + dx;
            int bxFrom = floor(edgeX + EPSILON);
            int bxTo   = floor(targetX - EPSILON);

            for (int bx = bxFrom; bx <= bxTo; bx++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockLeft = (float) bx;
                            float maxDx = blockLeft - edgeX - EPSILON;
                            if (maxDx < result) {
                                result = maxDx;
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    // ---- Z-axis sweep ----

    private static float sweepAxisZ(Vector3f pos, float halfW, float eyeH, float headH,
                                     float dz, World world) {
        if (dz == 0) return 0;

        float minX = pos.x - halfW;
        float maxX = pos.x + halfW;
        float feetY = pos.y - eyeH;
        float headY = pos.y + headH;

        int bx0 = floor(minX + EPSILON);
        int bx1 = floor(maxX - EPSILON);
        int by0 = floor(feetY + EPSILON);
        int by1 = floor(headY - EPSILON);

        float result = dz;

        if (dz < 0) {
            // Moving in -Z
            float edgeZ = pos.z - halfW;
            float targetZ = edgeZ + dz;
            int bzFrom = floor(targetZ + EPSILON);
            int bzTo   = floor(edgeZ - EPSILON);

            for (int bz = bzTo; bz >= bzFrom; bz--) {
                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockFront = bz + 1.0f;
                            float maxDz = blockFront - edgeZ + EPSILON;
                            if (maxDz > result) {
                                result = maxDz;
                            }
                        }
                    }
                }
            }
        } else {
            // Moving in +Z
            float edgeZ = pos.z + halfW;
            float targetZ = edgeZ + dz;
            int bzFrom = floor(edgeZ + EPSILON);
            int bzTo   = floor(targetZ - EPSILON);

            for (int bz = bzFrom; bz <= bzTo; bz++) {
                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockBack = (float) bz;
                            float maxDz = blockBack - edgeZ - EPSILON;
                            if (maxDz < result) {
                                result = maxDz;
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    // ---- Helpers ----

    private static boolean isSolid(World world, int x, int y, int z) {
        int blockId = world.getBlock(x, y, z);
        return Blocks.get(blockId).solid();
    }

    /** Floor that handles negative correctly (Math.floorDiv equiv for float). */
    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
