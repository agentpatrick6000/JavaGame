package com.voxelgame.render;

import com.voxelgame.sim.ItemEntity;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders dropped item entities as small colored cubes floating in the world.
 */
public class ItemEntityRenderer {

    private static final float[][] BLOCK_COLORS = {
        {0.0f, 0.0f, 0.0f},       //  0 AIR
        {0.47f, 0.47f, 0.47f},     //  1 STONE
        {0.39f, 0.39f, 0.39f},     //  2 COBBLESTONE
        {0.53f, 0.38f, 0.26f},     //  3 DIRT
        {0.30f, 0.60f, 0.00f},     //  4 GRASS
        {0.84f, 0.81f, 0.60f},     //  5 SAND
        {0.51f, 0.49f, 0.49f},     //  6 GRAVEL
        {0.39f, 0.27f, 0.16f},     //  7 LOG
        {0.20f, 0.51f, 0.04f},     //  8 LEAVES
        {0.12f, 0.31f, 0.78f},     //  9 WATER
        {0.35f, 0.35f, 0.35f},     // 10 COAL_ORE
        {0.55f, 0.45f, 0.35f},     // 11 IRON_ORE
        {0.75f, 0.65f, 0.20f},     // 12 GOLD_ORE
        {0.39f, 0.86f, 1.00f},     // 13 DIAMOND_ORE
        {0.16f, 0.16f, 0.16f},     // 14 BEDROCK
        {0.95f, 0.55f, 0.50f},     // 15 RAW_PORKCHOP
        {0.55f, 0.40f, 0.25f},     // 16 ROTTEN_FLESH
        {0.70f, 0.55f, 0.30f},     // 17 PLANKS
        {0.60f, 0.45f, 0.25f},     // 18 CRAFTING_TABLE
        {0.65f, 0.50f, 0.25f},     // 19 STICK
        {0.58f, 0.42f, 0.20f},     // 20 WOODEN_PICKAXE
        {0.55f, 0.40f, 0.18f},     // 21 WOODEN_AXE
        {0.52f, 0.38f, 0.16f},     // 22 WOODEN_SHOVEL
        {0.50f, 0.50f, 0.52f},     // 23 STONE_PICKAXE
        {0.48f, 0.48f, 0.50f},     // 24 STONE_AXE
        {0.46f, 0.46f, 0.48f},     // 25 STONE_SHOVEL
        {0.60f, 0.40f, 0.20f},     // 26 CHEST
        {0.45f, 0.45f, 0.45f},     // 27 RAIL
        {0.85f, 0.20f, 0.15f},     // 28 TNT
        {0.55f, 0.35f, 0.15f},     // 29 BOAT
        {0.50f, 0.50f, 0.55f},     // 30 MINECART
        {0.45f, 0.45f, 0.45f},     // 31 FURNACE
        {1.00f, 0.85f, 0.30f},     // 32 TORCH
        {0.14f, 0.14f, 0.14f},     // 33 COAL (dark charcoal)
        {0.78f, 0.76f, 0.74f},     // 34 IRON_INGOT
        {0.82f, 0.90f, 0.94f},     // 35 GLASS
        {0.78f, 0.51f, 0.31f},     // 36 COOKED_PORKCHOP
        {0.86f, 0.15f, 0.15f},     // 37 RED_FLOWER
        {1.00f, 0.90f, 0.20f},     // 38 YELLOW_FLOWER
        {0.50f, 0.90f, 1.00f},     // 39 DIAMOND
        {0.78f, 0.78f, 0.80f},     // 40 IRON_PICKAXE
        {0.78f, 0.78f, 0.80f},     // 41 IRON_AXE
        {0.78f, 0.78f, 0.80f},     // 42 IRON_SHOVEL
        {0.78f, 0.78f, 0.80f},     // 43 IRON_SWORD
        {0.60f, 0.45f, 0.22f},     // 44 WOODEN_SWORD
        {0.50f, 0.50f, 0.52f},     // 45 STONE_SWORD
        {0.22f, 0.16f, 0.10f},     // 46 CHARCOAL
        {0.90f, 0.75f, 0.20f},     // 47 GOLD_INGOT
        {0.55f, 0.45f, 0.35f},     // 48 POWERED_RAIL
        {0.70f, 0.10f, 0.05f},     // 49 REDSTONE
        {0.70f, 0.10f, 0.05f},     // 50 REDSTONE_WIRE
        {0.70f, 0.15f, 0.10f},     // 51 REDSTONE_TORCH
        {0.60f, 0.15f, 0.10f},     // 52 REDSTONE_REPEATER
        {0.50f, 0.35f, 0.35f},     // 53 REDSTONE_ORE
        {0.12f, 0.31f, 0.78f},     // 54 FLOWING_WATER_1
        {0.12f, 0.31f, 0.78f},     // 55 FLOWING_WATER_2
        {0.12f, 0.31f, 0.78f},     // 56 FLOWING_WATER_3
        {0.12f, 0.31f, 0.78f},     // 57 FLOWING_WATER_4
        {0.12f, 0.31f, 0.78f},     // 58 FLOWING_WATER_5
        {0.12f, 0.31f, 0.78f},     // 59 FLOWING_WATER_6
        {0.12f, 0.31f, 0.78f},     // 60 FLOWING_WATER_7
        {0.90f, 0.45f, 0.10f},     // 61 LAVA
        {0.90f, 0.45f, 0.10f},     // 62 FLOWING_LAVA_1
        {0.90f, 0.45f, 0.10f},     // 63 FLOWING_LAVA_2
        {0.90f, 0.45f, 0.10f},     // 64 FLOWING_LAVA_3
        {0.90f, 0.45f, 0.10f},     // 65 FLOWING_LAVA_4
        {0.90f, 0.45f, 0.10f},     // 66 FLOWING_LAVA_5
        {0.90f, 0.45f, 0.10f},     // 67 FLOWING_LAVA_6
        {0.90f, 0.45f, 0.10f},     // 68 FLOWING_LAVA_7
        {0.10f, 0.05f, 0.15f},     // 69 OBSIDIAN
        {0.90f, 0.90f, 0.88f},     // 70 FEATHER
    };

    private Shader shader;
    private int vao, vbo;

    public void init() {
        shader = new Shader("shaders/line.vert", "shaders/line.frag");
        buildCube();
    }

    private void buildCube() {
        float[] verts = {
            // Front face (z=1)
            0,0,1,  1,0,1,  1,1,1,  0,0,1,  1,1,1,  0,1,1,
            // Back face (z=0)
            1,0,0,  0,0,0,  0,1,0,  1,0,0,  0,1,0,  1,1,0,
            // Top face (y=1)
            0,1,1,  1,1,1,  1,1,0,  0,1,1,  1,1,0,  0,1,0,
            // Bottom face (y=0)
            0,0,0,  1,0,0,  1,0,1,  0,0,0,  1,0,1,  0,0,1,
            // Right face (x=1)
            1,0,1,  1,0,0,  1,1,0,  1,0,1,  1,1,0,  1,1,1,
            // Left face (x=0)
            0,0,0,  0,0,1,  0,1,1,  0,0,0,  0,1,1,  0,1,0,
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    /**
     * Render all item entities.
     */
    public void render(Camera camera, int windowW, int windowH, List<ItemEntity> itemList) {
        if (itemList == null || itemList.isEmpty()) return;

        Matrix4f projection = camera.getProjectionMatrix(windowW, windowH);
        Matrix4f cameraView = camera.getViewMatrix();

        shader.bind();
        shader.setMat4("uProjection", projection);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindVertexArray(vao);

        float s = ItemEntity.SIZE;
        float halfS = s / 2.0f;

        for (ItemEntity item : itemList) {
            if (item.isDead()) continue;

            Matrix4f view = new Matrix4f(cameraView);
            view.translate(item.getX() - halfS, item.getY() - halfS, item.getZ() - halfS);

            // Rotate around center
            view.translate(halfS, halfS, halfS);
            view.rotateY((float) Math.toRadians(item.getRotation()));
            view.translate(-halfS, -halfS, -halfS);

            // Scale
            view.scale(s, s, s);

            shader.setMat4("uView", view);

            // Color based on block type
            int blockId = item.getBlockId();
            float r = 0.5f, g = 0.5f, b = 0.5f;
            if (blockId >= 0 && blockId < BLOCK_COLORS.length) {
                r = BLOCK_COLORS[blockId][0];
                g = BLOCK_COLORS[blockId][1];
                b = BLOCK_COLORS[blockId][2];
            }

            float pulse = 1.0f + 0.1f * (float) Math.sin(item.getAge() * 4.0f);
            shader.setVec4("uColor", r * pulse, g * pulse, b * pulse, 1.0f);

            glDrawArrays(GL_TRIANGLES, 0, 36);
        }

        glBindVertexArray(0);
        glDisable(GL_BLEND);
        shader.unbind();
    }

    public void cleanup() {
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (shader != null) shader.cleanup();
    }
}
