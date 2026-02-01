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
        {0.0f, 0.0f, 0.0f},       // 0 AIR
        {0.47f, 0.47f, 0.47f},     // 1 STONE
        {0.39f, 0.39f, 0.39f},     // 2 COBBLESTONE
        {0.53f, 0.38f, 0.26f},     // 3 DIRT
        {0.30f, 0.60f, 0.00f},     // 4 GRASS
        {0.84f, 0.81f, 0.60f},     // 5 SAND
        {0.51f, 0.49f, 0.49f},     // 6 GRAVEL
        {0.39f, 0.27f, 0.16f},     // 7 LOG
        {0.20f, 0.51f, 0.04f},     // 8 LEAVES
        {0.12f, 0.31f, 0.78f},     // 9 WATER
        {0.35f, 0.35f, 0.35f},     // 10 COAL_ORE
        {0.55f, 0.45f, 0.35f},     // 11 IRON_ORE
        {0.75f, 0.65f, 0.20f},     // 12 GOLD_ORE
        {0.39f, 0.86f, 1.00f},     // 13 DIAMOND_ORE
        {0.16f, 0.16f, 0.16f},     // 14 BEDROCK
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
