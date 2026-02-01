package com.voxelgame.render;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldTime;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Main rendering coordinator. Binds shader, sets uniforms,
 * renders visible chunk meshes with frustum culling.
 *
 * Uses two passes:
 *   1. Opaque pass  — depth write ON, blend OFF
 *   2. Transparent pass — depth write OFF, blend ON (water, etc.)
 */
public class Renderer {

    private static final float WATER_ALPHA = 0.55f;

    private Shader blockShader;
    private TextureAtlas atlas;
    private Frustum frustum;
    private final World world;

    /** Current sun brightness for time-of-day lighting. Updated each frame. */
    private float sunBrightness = 1.0f;

    /** Current fog/sky color. Updated each frame from WorldTime. */
    private float[] fogColor = {0.53f, 0.68f, 0.90f};

    public Renderer(World world) {
        this.world = world;
    }

    public void init() {
        blockShader = new Shader("shaders/block.vert", "shaders/block.frag");
        atlas = new TextureAtlas();
        atlas.init();
        frustum = new Frustum();
    }

    /** Update sun brightness and fog color from world time. Call once per frame. */
    public void updateLighting(WorldTime worldTime) {
        if (worldTime != null) {
            this.sunBrightness = worldTime.getSunBrightness();
            this.fogColor = worldTime.getSkyColor();
        }
    }

    public void render(Camera camera, int windowWidth, int windowHeight) {
        Matrix4f projection = camera.getProjectionMatrix(windowWidth, windowHeight);
        Matrix4f view = camera.getViewMatrix();

        // Update frustum
        Matrix4f projView = new Matrix4f(projection).mul(view);
        frustum.update(projView);

        // Bind shader and set shared uniforms
        blockShader.bind();
        blockShader.setMat4("uProjection", projection);
        blockShader.setMat4("uView", view);
        blockShader.setInt("uAtlas", 0);
        blockShader.setFloat("uSunBrightness", sunBrightness);
        blockShader.setVec3("uCameraPos", camera.getPosition());
        blockShader.setVec3("uFogColor", fogColor[0], fogColor[1], fogColor[2]);

        atlas.bind(0);

        // ---- Pass 1: Opaque geometry ----
        blockShader.setFloat("uAlpha", 1.0f);

        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();

            if (!frustum.isChunkVisible(pos.x(), pos.z())) continue;

            var mesh = chunk.getMesh();
            if (mesh != null && !mesh.isEmpty()) {
                mesh.draw();
            }
        }

        // ---- Pass 2: Transparent geometry (water) ----
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);       // don't write to depth buffer
        glDisable(GL_CULL_FACE);  // render both sides of water

        blockShader.setFloat("uAlpha", WATER_ALPHA);

        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();

            if (!frustum.isChunkVisible(pos.x(), pos.z())) continue;

            var mesh = chunk.getTransparentMesh();
            if (mesh != null && !mesh.isEmpty()) {
                mesh.draw();
            }
        }

        // Restore state
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        blockShader.unbind();
    }

    public TextureAtlas getAtlas() { return atlas; }

    public void cleanup() {
        if (blockShader != null) blockShader.cleanup();
        if (atlas != null) atlas.cleanup();
    }
}
