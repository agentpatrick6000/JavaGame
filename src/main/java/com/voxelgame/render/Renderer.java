package com.voxelgame.render;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import com.voxelgame.world.WorldTime;
import com.voxelgame.world.lod.LODConfig;
import com.voxelgame.world.lod.LODLevel;
import com.voxelgame.world.mesh.ChunkMesh;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Main rendering coordinator. Binds shader, sets uniforms,
 * renders visible chunk meshes with frustum culling.
 *
 * Supports multi-tier LOD rendering:
 * - LOD 0: Full detail opaque + transparent passes
 * - LOD 1-3: Opaque-only simplified meshes
 * - Dynamic fog distance based on LOD config
 *
 * Uses two passes:
 *   1. Opaque pass  — depth write ON, blend OFF
 *   2. Transparent pass — depth write OFF, blend ON (water, etc.)
 *
 * Phase 2 Sky System:
 * - Zenith/horizon color split for atmospheric depth
 * - Time-of-day intensity curves (dark nights)
 * - Sun direction and color
 */
public class Renderer {

    private static final float WATER_ALPHA = 0.55f;

    private Shader blockShader;
    private TextureAtlas atlas;
    private Frustum frustum;
    private final World world;

    /** Sky system for zenith/horizon colors and intensity curves. */
    private final SkySystem skySystem = new SkySystem();

    /** Current sun brightness for time-of-day lighting. Updated each frame. */
    private float sunBrightness = 1.0f;

    /** Current fog/sky color. Updated each frame from SkySystem. */
    private float[] fogColor = {0.53f, 0.68f, 0.90f};
    
    /** Current zenith (overhead) sky color. */
    private float[] zenithColor = {0.30f, 0.50f, 0.90f};
    
    /** Current horizon sky color. */
    private float[] horizonColor = {0.60f, 0.80f, 1.00f};
    
    /** Current sky intensity multiplier. */
    private float skyIntensity = 1.0f;
    
    /** Current sun direction vector. Updated each frame from SkySystem. */
    private float[] sunDirection = {0.0f, 1.0f, 0.0f};
    
    /** Current sun color. */
    private float[] sunColor = {1.0f, 0.98f, 0.90f};
    
    /** Current sun intensity for directional lighting. Updated each frame from SkySystem. */
    private float sunIntensity = 1.0f;

    /** LOD configuration — controls fog distances. May be null if LOD not initialized. */
    private LODConfig lodConfig;

    // ---- Render stats ----
    private int renderedChunks;
    private int culledChunks;

    public Renderer(World world) {
        this.world = world;
    }

    public void init() {
        blockShader = new Shader("shaders/block.vert", "shaders/block.frag");
        atlas = new TextureAtlas();
        atlas.init();
        frustum = new Frustum();
    }

    /** Set the LOD config for dynamic fog distances. */
    public void setLodConfig(LODConfig lodConfig) {
        this.lodConfig = lodConfig;
    }

    /** 
     * Update lighting from world time using SkySystem. Call once per frame.
     * 
     * Uses Phase 2 SkySystem for:
     * - Zenith/horizon color split
     * - Smooth intensity curves (dark nights)
     * - Sun direction, color, and intensity
     */
    public void updateLighting(WorldTime worldTime) {
        if (worldTime == null) return;
        
        // Convert WorldTime ticks to normalized time (0-1)
        float normalizedTime = SkySystem.worldTimeToNormalized(worldTime.getWorldTick());
        
        // Update sky colors from SkySystem
        this.zenithColor = skySystem.getZenithColor(normalizedTime);
        this.horizonColor = skySystem.getHorizonColor(normalizedTime);
        this.skyIntensity = skySystem.getSkyIntensity(normalizedTime);
        
        // Update sun from SkySystem
        this.sunDirection = skySystem.getSunDirection(normalizedTime);
        this.sunColor = skySystem.getSunColor(normalizedTime);
        this.sunIntensity = skySystem.getSunIntensity(normalizedTime);
        
        // Fog color is a blend of zenith and horizon
        this.fogColor = skySystem.getFogColor(normalizedTime);
        
        // Keep sunBrightness for legacy/backward compatibility
        this.sunBrightness = worldTime.getSunBrightness();
    }

    public void render(Camera camera, int windowWidth, int windowHeight) {
        // Sync camera far plane with LOD config (prevents frustum from extending way beyond render distance)
        if (lodConfig != null) {
            camera.setFarPlane(lodConfig.getFarPlane());
        }

        Matrix4f projection = camera.getProjectionMatrix(windowWidth, windowHeight);
        Matrix4f view = camera.getViewMatrix();

        // Update frustum
        Matrix4f projView = new Matrix4f(projection).mul(view);
        frustum.update(projView);

        // Compute fog distances from LOD config
        float fogStart, fogEnd;
        if (lodConfig != null) {
            fogStart = lodConfig.getFogStart();
            fogEnd = lodConfig.getFogEnd();
        } else {
            fogStart = 80.0f;
            fogEnd = 128.0f;
        }

        // Pre-compute distance culling threshold (in chunk coordinates)
        // Chunks beyond maxRenderDistance + 1 are fully fogged and don't need rendering
        float maxChunkDist = lodConfig != null ? lodConfig.getMaxRenderDistance() + 1.0f : 20.0f;
        float maxChunkDistSq = maxChunkDist * maxChunkDist;
        float camCX = camera.getPosition().x / WorldConstants.CHUNK_SIZE;
        float camCZ = camera.getPosition().z / WorldConstants.CHUNK_SIZE;

        // Bind shader and set shared uniforms
        blockShader.bind();
        blockShader.setMat4("uProjection", projection);
        blockShader.setMat4("uView", view);
        blockShader.setInt("uAtlas", 0);
        blockShader.setFloat("uSunBrightness", sunBrightness);
        blockShader.setVec3("uCameraPos", camera.getPosition());
        blockShader.setVec3("uFogColor", fogColor[0], fogColor[1], fogColor[2]);
        blockShader.setFloat("uFogStart", fogStart);
        blockShader.setFloat("uFogEnd", fogEnd);
        
        // Phase 2 Sky System uniforms - zenith/horizon color split
        blockShader.setVec3("uSkyZenithColor", zenithColor[0], zenithColor[1], zenithColor[2]);
        blockShader.setVec3("uSkyHorizonColor", horizonColor[0], horizonColor[1], horizonColor[2]);
        blockShader.setFloat("uSkyIntensity", skyIntensity);
        
        // Sun uniforms
        blockShader.setVec3("uSunDirection", sunDirection[0], sunDirection[1], sunDirection[2]);
        blockShader.setVec3("uSunColor", sunColor[0], sunColor[1], sunColor[2]);
        blockShader.setFloat("uSunIntensity", sunIntensity);
        
        // Legacy: keep uSkyColor for backward compatibility (use fog color)
        blockShader.setVec3("uSkyColor", fogColor[0], fogColor[1], fogColor[2]);

        atlas.bind(0);

        renderedChunks = 0;
        culledChunks = 0;

        // ---- Pass 1: Opaque geometry (all LOD levels) ----
        // Includes alpha-discard geometry (torches, flowers, rails) which need both-side rendering.
        // Disabling cull face here is safe since solid block faces are single-sided anyway.
        blockShader.setFloat("uAlpha", 1.0f);
        glDisable(GL_CULL_FACE);

        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();

            // Distance cull — skip chunks beyond fog end (fully fogged, no point rendering)
            float cdx = pos.x() + 0.5f - camCX;
            float cdz = pos.z() + 0.5f - camCZ;
            if (cdx * cdx + cdz * cdz > maxChunkDistSq) {
                culledChunks++;
                continue;
            }

            if (!frustum.isChunkVisible(pos.x(), pos.z())) {
                culledChunks++;
                continue;
            }

            // Use LOD-appropriate mesh
            ChunkMesh mesh = chunk.getRenderMesh();
            if (mesh != null && !mesh.isEmpty()) {
                mesh.draw();
                renderedChunks++;
            }
        }

        // Re-enable cull face after opaque pass (was disabled for cross-billboard geometry)
        glEnable(GL_CULL_FACE);

        // ---- Pass 2: Transparent geometry (water only now) — LOD 0 only ----
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);       // don't write to depth buffer
        glDisable(GL_CULL_FACE);  // render both sides of water

        blockShader.setFloat("uAlpha", WATER_ALPHA);

        for (var entry : world.getChunkMap().entrySet()) {
            Chunk chunk = entry.getValue();

            // Skip non-LOD0 chunks early — they never have transparent meshes
            if (chunk.getCurrentLOD() != com.voxelgame.world.lod.LODLevel.LOD_0) continue;

            ChunkPos pos = entry.getKey();
            if (!frustum.isChunkVisible(pos.x(), pos.z())) continue;

            ChunkMesh transMesh = chunk.getRenderTransparentMesh();
            if (transMesh != null && !transMesh.isEmpty()) {
                transMesh.draw();
            }
        }

        // Restore state
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        blockShader.unbind();
    }

    public TextureAtlas getAtlas() { return atlas; }
    public int getRenderedChunks() { return renderedChunks; }
    public int getCulledChunks() { return culledChunks; }

    public void cleanup() {
        if (blockShader != null) blockShader.cleanup();
        if (atlas != null) atlas.cleanup();
    }
}
