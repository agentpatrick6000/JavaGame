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
 * Uses three passes:
 *   1. Shadow pass  — render depth from sun's perspective (CSM)
 *   2. Opaque pass  — depth write ON, blend OFF
 *   3. Transparent pass — depth write OFF, blend ON (water, etc.)
 *
 * Phase 2 Sky System:
 * - Zenith/horizon color split for atmospheric depth
 * - Time-of-day intensity curves (dark nights)
 * - Sun direction and color
 *
 * Phase 5 Shadow System:
 * - Cascaded shadow maps for sun/moon shadows
 * - PCF soft shadows
 */
public class Renderer {

    private static final float WATER_ALPHA = 0.55f;

    private Shader blockShader;
    private TextureAtlas atlas;
    private Frustum frustum;
    private final World world;
    
    /** Phase 5: Shadow renderer for cascaded shadow maps. */
    private ShadowRenderer shadowRenderer;

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

    /** Phase 4: Game time in seconds for torch flicker animation. */
    private float gameTime = 0.0f;

    /** Phase 6: Smooth lighting toggle (true = interpolated, false = flat per-face). */
    private boolean smoothLighting = true;

    /** Phase 6: Current normalized time of day for fog density calculation. */
    private float currentTimeOfDay = 0.5f;

    /** Debug view mode (0=normal, 1=albedo, 2=lighting, 3=depth, 4=fog, 5=fog_dist, 6=fog_height, 7=fog_combined) */
    private int debugView = 0;
    private static final String[] DEBUG_VIEW_NAMES = {
        "Normal", "Albedo", "Lighting", "Linear Depth", "Fog Factor", 
        "Fog Dist Only", "Fog Height Only", "Fog Combined"
    };

    /** Fog mode: 0=world fog ON, 1=fog disabled (post-only), 2=fog OFF */
    private int fogMode = 0;
    public static final int FOG_WORLD_ONLY = 0;
    public static final int FOG_POST_ONLY = 1;  // Disabled in world shader
    public static final int FOG_OFF = 2;
    private static final String[] FOG_MODE_NAMES = {"WORLD_ONLY", "POST_ONLY", "OFF"};

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
        
        // Phase 5: Initialize shadow renderer
        shadowRenderer = new ShadowRenderer();
        shadowRenderer.init();
    }

    /** Set the LOD config for dynamic fog distances. */
    public void setLodConfig(LODConfig lodConfig) {
        this.lodConfig = lodConfig;
    }

    /** Phase 4: Set the game time for torch flicker animation. */
    public void setGameTime(float time) {
        this.gameTime = time;
    }

    /** Phase 6: Toggle smooth lighting mode. */
    public void toggleSmoothLighting() {
        this.smoothLighting = !this.smoothLighting;
        System.out.println("[Renderer] Smooth lighting: " + (smoothLighting ? "ON" : "OFF"));
    }

    /** Phase 6: Get current smooth lighting state. */
    public boolean isSmoothLighting() {
        return smoothLighting;
    }

    /** Phase 6: Set smooth lighting state. */
    public void setSmoothLighting(boolean enabled) {
        this.smoothLighting = enabled;
    }

    /** Cycle debug view mode (F7). */
    public void cycleDebugView() {
        debugView = (debugView + 1) % DEBUG_VIEW_NAMES.length;
        System.out.println("[Renderer] Debug view: " + DEBUG_VIEW_NAMES[debugView]);
    }

    /** Get current debug view name. */
    public String getDebugViewName() {
        return DEBUG_VIEW_NAMES[debugView];
    }
    
    /** Get current debug view mode. */
    public int getDebugView() {
        return debugView;
    }
    
    /** Set debug view mode directly. */
    public void setDebugView(int mode) {
        this.debugView = mode % DEBUG_VIEW_NAMES.length;
    }
    
    /** Get fog start distance (for render state JSON). */
    public float getFogStart() {
        if (lodConfig != null) {
            return lodConfig.getFogStart() * skySystem.getFogDensity(currentTimeOfDay);
        }
        return 80.0f;
    }
    
    /** Get fog end distance (for render state JSON). */
    public float getFogEnd() {
        if (lodConfig != null) {
            return lodConfig.getFogEnd() * skySystem.getFogDensity(currentTimeOfDay);
        }
        return 128.0f;
    }

    /** Cycle fog mode (F10). */
    public void cycleFogMode() {
        fogMode = (fogMode + 1) % 3;
        System.out.println("[Renderer] Fog mode: " + FOG_MODE_NAMES[fogMode]);
    }

    /** Get current fog mode name. */
    public String getFogModeName() {
        return FOG_MODE_NAMES[fogMode];
    }

    /** Get current fog mode. */
    public int getFogMode() {
        return fogMode;
    }
    
    /** Set fog mode directly (for profile captures). */
    public void setFogModeValue(int mode) {
        this.fogMode = mode % 3;
    }

    /** Set fog mode directly. */
    public void setFogMode(int mode) {
        this.fogMode = mode % 3;
    }

    // Section D: Wireframe mode
    private boolean wireframeMode = false;

    /** Toggle wireframe rendering (F12). */
    public void toggleWireframe() {
        wireframeMode = !wireframeMode;
        if (wireframeMode) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            System.out.println("[Renderer] Wireframe: ON");
        } else {
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            System.out.println("[Renderer] Wireframe: OFF");
        }
    }

    public boolean isWireframeMode() { return wireframeMode; }

    /** 
     * Update lighting from world time using SkySystem. Call once per frame.
     * 
     * Uses Phase 2 SkySystem for:
     * - Zenith/horizon color split
     * - Smooth intensity curves (dark nights)
     * - Sun direction, color, and intensity
     * 
     * Phase 6 additions:
     * - Dynamic fog color matching horizon
     * - Time-of-day fog density
     */
    public void updateLighting(WorldTime worldTime) {
        if (worldTime == null) return;
        
        // Convert WorldTime ticks to normalized time (0-1)
        float normalizedTime = SkySystem.worldTimeToNormalized(worldTime.getWorldTick());
        
        // Phase 6: Track current time for fog density calculation
        this.currentTimeOfDay = normalizedTime;
        
        // Update sky colors from SkySystem
        this.zenithColor = skySystem.getZenithColor(normalizedTime);
        this.horizonColor = skySystem.getHorizonColor(normalizedTime);
        this.skyIntensity = skySystem.getSkyIntensity(normalizedTime);
        
        // Update sun from SkySystem
        this.sunDirection = skySystem.getSunDirection(normalizedTime);
        this.sunColor = skySystem.getSunColor(normalizedTime);
        this.sunIntensity = skySystem.getSunIntensity(normalizedTime);
        
        // Phase 6: Fog color matches horizon (seamless blend to sky at distance)
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
        // Phase 6: Apply dynamic fog density based on time of day
        float baseFogStart, baseFogEnd;
        if (lodConfig != null) {
            baseFogStart = lodConfig.getFogStart();
            baseFogEnd = lodConfig.getFogEnd();
        } else {
            baseFogStart = 80.0f;
            baseFogEnd = 128.0f;
        }
        
        // Phase 6: Dynamic fog density - multiply distances by density factor
        // Lower density = denser fog = shorter distances
        // Higher density = thinner fog = longer distances
        float fogDensity = skySystem.getFogDensity(currentTimeOfDay);
        float fogStart = baseFogStart * fogDensity;
        float fogEnd = baseFogEnd * fogDensity;

        // Pre-compute distance culling threshold (in chunk coordinates)
        // Chunks beyond maxRenderDistance + 1 are fully fogged and don't need rendering
        float maxChunkDist = lodConfig != null ? lodConfig.getMaxRenderDistance() + 1.0f : 20.0f;
        float maxChunkDistSq = maxChunkDist * maxChunkDist;
        float camCX = camera.getPosition().x / WorldConstants.CHUNK_SIZE;
        float camCZ = camera.getPosition().z / WorldConstants.CHUNK_SIZE;

        // ========================================================================
        // PHASE 5: SHADOW PASS - Render scene from sun's perspective
        // ========================================================================
        renderShadowPass(camera, windowWidth, windowHeight, camCX, camCZ, maxChunkDistSq);

        // Restore viewport after shadow pass
        glViewport(0, 0, windowWidth, windowHeight);

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
        
        // Phase 4: Pass game time for torch flicker animation
        blockShader.setFloat("uTime", gameTime);
        
        // Phase 6: Smooth lighting toggle (1 = smooth interpolated, 0 = flat per-face)
        blockShader.setInt("uSmoothLighting", smoothLighting ? 1 : 0);
        
        // Debug view mode + depth planes for visualization
        blockShader.setInt("uDebugView", debugView);
        blockShader.setFloat("uNearPlane", camera.getNearPlane());
        blockShader.setFloat("uFarPlane", camera.getFarPlane());
        
        // Fog mode toggle for visual audit
        blockShader.setInt("uFogMode", fogMode);
        
        // Phase 5: Shadow map uniforms
        if (shadowRenderer != null && shadowRenderer.isShadowsEnabled()) {
            blockShader.setInt("uShadowsEnabled", 1);
            
            // Bind shadow textures to texture units 4, 5, 6
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_2D, shadowRenderer.getShadowTexture(0));
            blockShader.setInt("uShadowMap0", 4);
            
            glActiveTexture(GL_TEXTURE5);
            glBindTexture(GL_TEXTURE_2D, shadowRenderer.getShadowTexture(1));
            blockShader.setInt("uShadowMap1", 5);
            
            glActiveTexture(GL_TEXTURE6);
            glBindTexture(GL_TEXTURE_2D, shadowRenderer.getShadowTexture(2));
            blockShader.setInt("uShadowMap2", 6);
            
            // Light-space matrices
            blockShader.setMat4("uLightViewProj0", shadowRenderer.getLightViewProj(0));
            blockShader.setMat4("uLightViewProj1", shadowRenderer.getLightViewProj(1));
            blockShader.setMat4("uLightViewProj2", shadowRenderer.getLightViewProj(2));
            
            // Restore texture unit 0 for atlas
            glActiveTexture(GL_TEXTURE0);
        } else {
            blockShader.setInt("uShadowsEnabled", 0);
        }

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
    
    /** Get shadow renderer for debug visualization. */
    public ShadowRenderer getShadowRenderer() { return shadowRenderer; }

    /**
     * Phase 5: Render shadow pass for all cascades.
     * Renders scene from sun's perspective into shadow map depth textures.
     */
    private void renderShadowPass(Camera camera, int windowWidth, int windowHeight,
                                  float camCX, float camCZ, float maxChunkDistSq) {
        if (shadowRenderer == null) return;
        
        // Update cascade matrices based on camera and sun direction
        float aspect = (float) windowWidth / Math.max(windowHeight, 1);
        shadowRenderer.updateCascades(camera, sunDirection, camera.getFov(), aspect);
        
        // Skip shadow rendering if disabled (night time)
        if (!shadowRenderer.isShadowsEnabled()) return;
        
        // Save current FBO to restore after shadow pass (important for PostFX!)
        int previousFBO = glGetInteger(GL_FRAMEBUFFER_BINDING);
        
        // Disable blending and ensure depth testing
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        
        // Render each cascade
        for (int cascade = 0; cascade < ShadowRenderer.NUM_CASCADES; cascade++) {
            shadowRenderer.beginShadowPass(cascade);
            
            // Render all visible chunks (simple culling - could be optimized with cascade frustum)
            for (var entry : world.getChunkMap().entrySet()) {
                ChunkPos pos = entry.getKey();
                Chunk chunk = entry.getValue();
                
                // Basic distance cull for shadow pass
                float cdx = pos.x() + 0.5f - camCX;
                float cdz = pos.z() + 0.5f - camCZ;
                
                // Use larger distance for shadow pass (shadows can come from further away)
                float shadowDistSq = maxChunkDistSq * 2.0f;
                if (cdx * cdx + cdz * cdz > shadowDistSq) continue;
                
                // Render opaque mesh for shadows
                ChunkMesh mesh = chunk.getRenderMesh();
                if (mesh != null && !mesh.isEmpty()) {
                    mesh.renderDepthOnly();
                }
            }
            
            shadowRenderer.endShadowPass(previousFBO);
        }
    }

    // ========================================================================
    // Section E: OpenGL State Validation (for visual audit debugging)
    // ========================================================================
    private long lastStateLogTime = 0;
    private boolean stateLoggingEnabled = false;
    
    /** Toggle state logging (for debugging). */
    public void toggleStateLogging() {
        stateLoggingEnabled = !stateLoggingEnabled;
        System.out.println("[Renderer] GL State logging: " + (stateLoggingEnabled ? "ON" : "OFF"));
    }
    
    /** Log current OpenGL state (called once per second if enabled). */
    public void logGLState(String checkpoint, int sceneFBO) {
        if (!stateLoggingEnabled) return;
        
        long now = System.currentTimeMillis();
        if (now - lastStateLogTime < 1000) return;  // Once per second
        lastStateLogTime = now;
        
        int currentFBO = glGetInteger(GL_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        boolean depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean blendEnabled = glIsEnabled(GL_BLEND);
        boolean srgbEnabled = glIsEnabled(GL_FRAMEBUFFER_SRGB);
        float[] clearColor = new float[4];
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
        int currentProgram = glGetInteger(GL_CURRENT_PROGRAM);
        
        System.out.println("=== GL STATE [" + checkpoint + "] ===");
        System.out.println("  FBO: " + currentFBO + " (scene=" + sceneFBO + ")");
        System.out.println("  Viewport: " + viewport[0] + "," + viewport[1] + " " + viewport[2] + "x" + viewport[3]);
        System.out.println("  Depth Test: " + depthTestEnabled);
        System.out.println("  Depth Mask: " + depthMask);
        System.out.println("  Blend: " + blendEnabled);
        System.out.println("  sRGB Framebuffer: " + srgbEnabled);
        System.out.printf("  Clear Color: (%.2f, %.2f, %.2f, %.2f)%n", 
            clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        System.out.println("  Shader Program: " + currentProgram);
        System.out.println("===================================");
    }
    
    public boolean isStateLoggingEnabled() { return stateLoggingEnabled; }

    public void cleanup() {
        if (blockShader != null) blockShader.cleanup();
        if (atlas != null) atlas.cleanup();
        if (shadowRenderer != null) shadowRenderer.cleanup();
    }
}
