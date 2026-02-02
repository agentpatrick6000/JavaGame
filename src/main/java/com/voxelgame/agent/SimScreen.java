package com.voxelgame.agent;

import com.voxelgame.render.Camera;
import com.voxelgame.world.*;
import org.joml.Vector3f;

/**
 * SimScreen: generates a 64×36 token grid representing the agent's
 * first-person POV.
 * <p>
 * For each cell in the grid, a ray is cast from the camera position
 * through the corresponding screen pixel. The result is classified into:
 * <ul>
 *   <li><b>cls</b> — cell class (SKY, AIR, SOLID, WATER, LAVA, FOLIAGE, etc.)</li>
 *   <li><b>depth</b> — distance bucket (0-5, representing 0-2m to 50+m)</li>
 *   <li><b>light</b> — light level bucket (0-3, representing 0-100%)</li>
 * </ul>
 * <p>
 * The grid is occlusion-correct: rays stop at the first solid hit.
 * Resolution is deliberately low (64×36) to be bandwidth-friendly
 * for AI agents while preserving spatial structure.
 */
public class SimScreen {

    public static final int WIDTH = 64;
    public static final int HEIGHT = 36;

    /** Maximum ray distance in blocks. */
    private static final float MAX_RAY_DIST = 80.0f;

    /** Ray step size for non-solid traversal (smaller = more accurate, slower). */
    private static final float RAY_STEP = 0.5f;

    // Pre-allocated ray direction vector (reused per ray to avoid GC)
    private final Vector3f rayDir = new Vector3f();

    /**
     * Generate the SimScreen grid and append it as a JSON array to the StringBuilder.
     * <p>
     * Output format: 2D array [row][col] where each cell is [cls_index, depth, light].
     * Using compact integer encoding: cls is index into CellClass enum (0-9),
     * depth is bucket (0-5), light is bucket (0-3).
     * <p>
     * Compact format: each cell is a 3-element array [cls, depth, light]
     * packed into rows. Total: 64*36 = 2304 cells.
     */
    public void generate(Camera camera, WorldAccess world, StringBuilder sb) {
        Vector3f pos = camera.getPosition();
        Vector3f front = camera.getFront();
        Vector3f right = camera.getRight();
        Vector3f up = camera.getUp();

        // Compute view plane dimensions from FOV
        float fovRad = (float) Math.toRadians(camera.getFov());
        float aspect = (float) WIDTH / HEIGHT;
        float halfH = (float) Math.tan(fovRad / 2.0f);
        float halfW = halfH * aspect;

        sb.append('[');

        for (int row = 0; row < HEIGHT; row++) {
            if (row > 0) sb.append(',');
            sb.append('[');

            // Map row to vertical angle: top of screen = +halfH, bottom = -halfH
            float v = 1.0f - 2.0f * (row + 0.5f) / HEIGHT; // +1 at top, -1 at bottom
            float vy = v * halfH;

            for (int col = 0; col < WIDTH; col++) {
                if (col > 0) sb.append(',');

                // Map col to horizontal angle: left = -halfW, right = +halfW
                float u = 2.0f * (col + 0.5f) / WIDTH - 1.0f; // -1 at left, +1 at right
                float vx = u * halfW;

                // Ray direction = front + vx*right + vy*up (normalized)
                rayDir.set(
                    front.x + vx * right.x + vy * up.x,
                    front.y + vx * right.y + vy * up.y,
                    front.z + vx * right.z + vy * up.z
                ).normalize();

                // Cast ray and classify
                castAndClassify(pos, rayDir, world, sb);
            }
            sb.append(']');
        }
        sb.append(']');
    }

    /**
     * Cast a single ray using DDA voxel traversal and write the result
     * as a compact JSON array [cls, depth, light].
     */
    private void castAndClassify(Vector3f origin, Vector3f dir, WorldAccess world, StringBuilder sb) {
        float dirX = dir.x, dirY = dir.y, dirZ = dir.z;

        // Current voxel
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        // Step direction
        int stepX = dirX > 0 ? 1 : (dirX < 0 ? -1 : 0);
        int stepY = dirY > 0 ? 1 : (dirY < 0 ? -1 : 0);
        int stepZ = dirZ > 0 ? 1 : (dirZ < 0 ? -1 : 0);

        // tMax
        float tMaxX = intBound(origin.x, dirX);
        float tMaxY = intBound(origin.y, dirY);
        float tMaxZ = intBound(origin.z, dirZ);

        // tDelta
        float tDeltaX = stepX != 0 ? Math.abs(1.0f / dirX) : Float.MAX_VALUE;
        float tDeltaY = stepY != 0 ? Math.abs(1.0f / dirY) : Float.MAX_VALUE;
        float tDeltaZ = stepZ != 0 ? Math.abs(1.0f / dirZ) : Float.MAX_VALUE;

        float dist = 0;

        while (dist < MAX_RAY_DIST) {
            // Check world bounds
            if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) {
                // Below or above world — classify based on direction
                if (y >= WorldConstants.WORLD_HEIGHT) {
                    // Above world = sky
                    writeCell(sb, Messages.CellClass.SKY, Messages.depthBucket(dist), 3);
                    return;
                } else {
                    // Below bedrock = solid darkness
                    writeCell(sb, Messages.CellClass.SOLID, Messages.depthBucket(dist), 0);
                    return;
                }
            }

            int blockId = world.getBlock(x, y, z);
            if (blockId != 0) {
                Block block = Blocks.get(blockId);

                if (block.solid()) {
                    // Hit a solid block — occlusion stops here
                    Messages.CellClass cls = classifyBlock(blockId, block);
                    int light = getCombinedLight(world, x, y, z);
                    writeCell(sb, cls, Messages.depthBucket(dist), Messages.lightBucket(light));
                    return;
                } else {
                    // Non-solid, non-air block (water, leaves transparent, etc.)
                    Messages.CellClass cls = classifyBlock(blockId, block);
                    if (cls == Messages.CellClass.WATER || cls == Messages.CellClass.LAVA) {
                        // Water/lava: report it (semi-transparent, but agent should know)
                        int light = getCombinedLight(world, x, y, z);
                        writeCell(sb, cls, Messages.depthBucket(dist), Messages.lightBucket(light));
                        return;
                    }
                    // Otherwise continue ray through transparent blocks
                }
            }

            // Step to next voxel (DDA)
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                dist = tMaxX;
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                dist = tMaxY;
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                dist = tMaxZ;
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }

        // Ray didn't hit anything within range — sky
        writeCell(sb, Messages.CellClass.SKY, 5, 3);
    }

    /**
     * Write a single cell as compact JSON array: [cls, depth, light].
     * cls is the enum ordinal (0-9), depth is bucket (0-5), light is bucket (0-3).
     */
    private static void writeCell(StringBuilder sb, Messages.CellClass cls, int depth, int light) {
        sb.append('[').append(cls.ordinal()).append(',').append(depth).append(',').append(light).append(']');
    }

    /**
     * Classify a block into a CellClass.
     */
    public static Messages.CellClass classifyBlock(int blockId, Block block) {
        if (blockId == 0) return Messages.CellClass.AIR;

        // Water
        if (Blocks.isWater(blockId)) return Messages.CellClass.WATER;
        if (Blocks.isLava(blockId)) return Messages.CellClass.LAVA;

        // Foliage (leaves, grass-like)
        if (blockId == Blocks.LEAVES.id()) return Messages.CellClass.FOLIAGE;

        // Lava — doesn't exist yet in block registry, but future-proof
        // if (blockId == Blocks.LAVA.id()) return Messages.CellClass.LAVA;

        // Solid blocks
        if (block.solid()) return Messages.CellClass.SOLID;

        // Non-solid transparent (air-like)
        return Messages.CellClass.AIR;
    }

    /**
     * Get combined light level (max of sky and block light) at a position.
     */
    private static int getCombinedLight(WorldAccess world, int x, int y, int z) {
        int sky = world.getSkyLight(x, y, z);
        int block = world.getBlockLight(x, y, z);
        return Math.max(sky, block);
    }

    /**
     * DDA helper: distance along ray to next integer boundary.
     */
    private static float intBound(float s, float ds) {
        if (ds > 0) {
            return ((float) Math.ceil(s) - s) / ds;
        } else if (ds < 0) {
            return (s - (float) Math.floor(s)) / (-ds);
        } else {
            return Float.MAX_VALUE;
        }
    }
}
