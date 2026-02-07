package com.voxelgame.core;

import com.voxelgame.render.PostFX;
import com.voxelgame.render.Renderer;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.Player;
import com.voxelgame.ui.Screenshot;
import com.voxelgame.world.WorldTime;
import com.voxelgame.world.stream.ChunkManager;
import com.voxelgame.world.gen.GenPipeline;
import com.voxelgame.world.gen.SpawnPointFinder;

import java.io.*;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Handles profile-based debug/spawn captures.
 * Captures multiple profiles sequentially with different rendering settings.
 */
public class ProfileCapture {
    
    // Capture phases
    private static final int PHASE_INIT = 0;
    private static final int PHASE_WARMUP = 1;
    private static final int PHASE_CAPTURE = 2;
    private static final int PHASE_NEXT_PROFILE = 3;
    private static final int PHASE_COMPLETE = 4;
    
    // Capture types
    public enum CaptureType {
        DEBUG,  // Full debug capture with all views
        SPAWN   // Spawn point validation
    }
    
    private final CaptureType captureType;
    private final List<CaptureProfile> profiles;
    private int currentProfileIndex = 0;
    private int phase = PHASE_INIT;
    private float timer = 0;
    private int warmupFrames = 0;
    
    // Camera/world settings for debug capture
    private static final int WARMUP_FRAME_COUNT = 120;
    private static final long DEBUG_SEED = 42L;
    private static final float DEBUG_X = 0.5f;
    private static final float DEBUG_Y = 100.0f;
    private static final float DEBUG_Z = 0.5f;
    private static final float DEBUG_YAW = 0.0f;
    private static final float DEBUG_PITCH = 0.0f;
    private static final int DEBUG_TIME_OF_DAY = 6000; // Noon
    
    // Debug view captures per profile
    private static final int[] DEBUG_VIEW_INDICES = {0, 3, 5, 6, 7}; // final, depth, fog_dist, fog_height, fog_combined
    private static final String[] DEBUG_VIEW_FILENAMES = {"final", "depth", "fog_dist", "fog_height", "fog_combined"};
    private int currentViewIndex = 0;
    
    // Base output directory
    private final String baseOutputDir;
    
    // Git hash (captured once at init)
    private String gitHeadHash = "unknown";
    
    // Seed for reproducibility
    private final String seed;
    
    // References (set during capture)
    private Player player;
    private WorldTime worldTime;
    private Renderer renderer;
    private PostFX postFX;
    private ChunkManager chunkManager;
    private int fbWidth, fbHeight;
    
    public ProfileCapture(CaptureType type, List<CaptureProfile> profiles, String baseDir, String seed) {
        this.captureType = type;
        this.profiles = new ArrayList<>(profiles);
        this.baseOutputDir = baseDir;
        this.seed = seed;
        
        // Get git hash
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null && line.length() >= 7) {
                gitHeadHash = line.trim();
            }
            p.waitFor();
        } catch (Exception e) {
            System.err.println("[ProfileCapture] Failed to get git hash: " + e.getMessage());
        }
    }
    
    public void setReferences(Player player, WorldTime worldTime, Renderer renderer, 
                              PostFX postFX, ChunkManager chunkManager, int fbW, int fbH) {
        this.player = player;
        this.worldTime = worldTime;
        this.renderer = renderer;
        this.postFX = postFX;
        this.chunkManager = chunkManager;
        this.fbWidth = fbW;
        this.fbHeight = fbH;
    }
    
    public boolean isComplete() {
        return phase == PHASE_COMPLETE;
    }
    
    public void update(float dt) {
        timer += dt;
        
        switch (phase) {
            case PHASE_INIT -> handleInit();
            case PHASE_WARMUP -> handleWarmup();
            case PHASE_CAPTURE -> handleCapture();
            case PHASE_NEXT_PROFILE -> handleNextProfile();
        }
    }
    
    private void handleInit() {
        if (timer < 0.1f) {
            // Setup player state
            player.setGameMode(GameMode.CREATIVE);
            if (!player.isFlyMode()) {
                player.toggleFlyMode();
            }
            return;
        }
        
        // Apply first profile settings
        applyProfile(profiles.get(currentProfileIndex));
        
        phase = PHASE_WARMUP;
        timer = 0;
        warmupFrames = 0;
        currentViewIndex = 0;
        
        System.out.println("[ProfileCapture] Starting profile: " + profiles.get(currentProfileIndex).getName());
    }
    
    private void handleWarmup() {
        warmupFrames++;
        
        // Keep position/time fixed during warmup
        if (captureType == CaptureType.DEBUG) {
            player.getCamera().getPosition().set(DEBUG_X, DEBUG_Y, DEBUG_Z);
            player.getCamera().setYaw(DEBUG_YAW);
            player.getCamera().setPitch(DEBUG_PITCH);
            if (worldTime != null) {
                worldTime.setWorldTick(DEBUG_TIME_OF_DAY);
            }
        }
        
        if (warmupFrames >= WARMUP_FRAME_COUNT) {
            System.out.println("[ProfileCapture] Warmup complete (" + warmupFrames + " frames)");
            
            // Create output directory for this profile
            CaptureProfile profile = profiles.get(currentProfileIndex);
            String profileDir = baseOutputDir + "/" + profile.getFolderName();
            new File(profileDir).mkdirs();
            System.out.println("[ProfileCapture] Output: " + profileDir);
            
            phase = PHASE_CAPTURE;
            timer = 0;
            currentViewIndex = 0;
        }
    }
    
    private void handleCapture() {
        // Keep position/time fixed during capture
        if (captureType == CaptureType.DEBUG) {
            player.getCamera().getPosition().set(DEBUG_X, DEBUG_Y, DEBUG_Z);
            player.getCamera().setYaw(DEBUG_YAW);
            player.getCamera().setPitch(DEBUG_PITCH);
            if (worldTime != null) {
                worldTime.setWorldTick(DEBUG_TIME_OF_DAY);
            }
        }
        
        CaptureProfile profile = profiles.get(currentProfileIndex);
        String profileDir = baseOutputDir + "/" + profile.getFolderName();
        
        if (captureType == CaptureType.DEBUG) {
            captureDebugViews(profileDir, profile);
        } else {
            captureSpawn(profileDir, profile);
        }
    }
    
    private void captureDebugViews(String profileDir, CaptureProfile profile) {
        // Capture each debug view
        if (currentViewIndex < DEBUG_VIEW_INDICES.length) {
            renderer.setDebugView(DEBUG_VIEW_INDICES[currentViewIndex]);
            postFX.setCompositeDebugMode(PostFX.COMPOSITE_NORMAL);
            
            if (timer > 0.2f) {
                String filename = DEBUG_VIEW_FILENAMES[currentViewIndex] + ".png";
                String path = Screenshot.captureToFile(fbWidth, fbHeight, profileDir + "/" + filename);
                System.out.println("[ProfileCapture] Saved: " + path);
                currentViewIndex++;
                timer = 0;
            }
        } else if (currentViewIndex == DEBUG_VIEW_INDICES.length) {
            // Capture HDR pre-tonemap
            renderer.setDebugView(0);
            postFX.setCompositeDebugMode(PostFX.COMPOSITE_HDR_PRE_TONEMAP);
            
            if (timer > 0.2f) {
                String path = Screenshot.captureToFile(fbWidth, fbHeight, profileDir + "/hdr_pre_tonemap.png");
                System.out.println("[ProfileCapture] Saved: " + path);
                currentViewIndex++;
                timer = 0;
            }
        } else if (currentViewIndex == DEBUG_VIEW_INDICES.length + 1) {
            // Capture LDR post-tonemap
            renderer.setDebugView(0);
            postFX.setCompositeDebugMode(PostFX.COMPOSITE_LDR_POST_TONEMAP);
            
            if (timer > 0.2f) {
                String path = Screenshot.captureToFile(fbWidth, fbHeight, profileDir + "/ldr_post_tonemap.png");
                System.out.println("[ProfileCapture] Saved: " + path);
                currentViewIndex++;
                timer = 0;
            }
        } else {
            // Reset modes and save metadata
            renderer.setDebugView(0);
            postFX.setCompositeDebugMode(PostFX.COMPOSITE_NORMAL);
            
            // Save render_state.json
            saveRenderStateJson(profileDir, profile);
            
            // Save probe.json (center-pixel fog values)
            saveFogProbe(profileDir, profile);
            
            phase = PHASE_NEXT_PROFILE;
            timer = 0;
        }
    }
    
    private void captureSpawn(String profileDir, CaptureProfile profile) {
        if (timer < 0.2f) return;
        
        // Capture spawn screenshot
        String path = Screenshot.captureToFile(fbWidth, fbHeight, profileDir + "/spawn.png");
        System.out.println("[ProfileCapture] Saved: " + path);
        
        // Save spawn_report.json
        saveSpawnReport(profileDir, profile);
        
        // Save render_state.json
        saveRenderStateJson(profileDir, profile);
        
        phase = PHASE_NEXT_PROFILE;
        timer = 0;
    }
    
    private void handleNextProfile() {
        currentProfileIndex++;
        
        if (currentProfileIndex >= profiles.size()) {
            phase = PHASE_COMPLETE;
            System.out.println("[ProfileCapture] All profiles complete.");
        } else {
            // Apply next profile settings
            CaptureProfile profile = profiles.get(currentProfileIndex);
            applyProfile(profile);
            
            phase = PHASE_WARMUP;
            timer = 0;
            warmupFrames = 0;
            currentViewIndex = 0;
            
            System.out.println("[ProfileCapture] Starting profile: " + profile.getName());
        }
    }
    
    private void applyProfile(CaptureProfile profile) {
        // Apply fog settings to renderer
        if (profile.isFogEnabled()) {
            renderer.setFogModeValue(Renderer.FOG_WORLD_ONLY);
        } else {
            renderer.setFogModeValue(Renderer.FOG_OFF);
        }
        
        // Note: Fog start/end controlled by profile but renderer uses LOD config
        // For now we rely on the default fog distances
        
        // Apply SSAO
        if (postFX != null) {
            postFX.setSSAOEnabled(profile.isSsaoEnabled());
        }
        
        // Note: Exposure/saturation/tonemap are hardcoded in composite shader
        // Full profile support would require shader uniforms for these
        // For now we capture with current settings and document in render_state.json
    }
    
    private void saveRenderStateJson(String profileDir, CaptureProfile profile) {
        try {
            File file = new File(profileDir, "render_state.json");
            
            long worldSeed = chunkManager != null ? chunkManager.getSeed() : 0;
            boolean srgbEnabled = glIsEnabled(GL_FRAMEBUFFER_SRGB);
            
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            
            // Git hash
            json.append("  \"git_head_hash\": \"").append(gitHeadHash).append("\",\n");
            
            // Profile info
            json.append("  \"profile_name\": \"").append(profile.getName()).append("\",\n");
            json.append("  \"profile_overrides\": ").append(profile.toOverridesJson()).append(",\n");
            
            // Seed and camera
            json.append("  \"seed\": ").append(worldSeed).append(",\n");
            json.append("  \"camera\": [")
                .append(player.getCamera().getPosition().x).append(", ")
                .append(player.getCamera().getPosition().y).append(", ")
                .append(player.getCamera().getPosition().z).append("],\n");
            json.append("  \"warmup\": ").append(warmupFrames).append(",\n");
            json.append("  \"near\": ").append(player.getCamera().getNearPlane()).append(",\n");
            json.append("  \"far\": ").append(player.getCamera().getFarPlane()).append(",\n");
            json.append("  \"fov\": ").append(player.getCamera().getFov()).append(",\n");
            json.append("  \"timeOfDay\": ").append(worldTime != null ? worldTime.getWorldTick() : 0).append(",\n");
            
            // Runtime values
            json.append("  \"exposure_runtime\": ").append(postFX != null ? postFX.getExposureMultiplier() : 1.0f).append(",\n");
            json.append("  \"saturation_runtime\": ").append(postFX != null ? postFX.getSaturationMultiplier() : 1.0f).append(",\n");
            json.append("  \"tonemap\": \"ACES\",\n");
            json.append("  \"gamma_runtime\": 2.2,\n");
            json.append("  \"srgb_runtime\": ").append(srgbEnabled).append(",\n");
            
            // Fog params runtime
            json.append("  \"fog_params_runtime\": {\n");
            json.append("    \"start\": ").append(renderer.getFogStart()).append(",\n");
            json.append("    \"end\": ").append(renderer.getFogEnd()).append(",\n");
            json.append("    \"mode\": \"").append(renderer.getFogModeName()).append("\"\n");
            json.append("  },\n");
            
            // Fog locations
            int fogMode = renderer.getFogMode();
            json.append("  \"fog_locations_runtime\": {\n");
            json.append("    \"terrain\": ").append(fogMode == Renderer.FOG_WORLD_ONLY).append(",\n");
            json.append("    \"post\": false,\n");
            json.append("    \"sky\": false,\n");
            json.append("    \"water\": ").append(fogMode == Renderer.FOG_WORLD_ONLY).append("\n");
            json.append("  },\n");
            
            // Timestamp
            json.append("  \"capture_timestamp\": \"").append(Instant.now().toString()).append("\"\n");
            json.append("}\n");
            
            FileWriter writer = new FileWriter(file);
            writer.write(json.toString());
            writer.close();
            System.out.println("[ProfileCapture] Saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[ProfileCapture] Failed to save render_state.json: " + e.getMessage());
        }
    }
    
    private void saveFogProbe(String profileDir, CaptureProfile profile) {
        try {
            File file = new File(profileDir, "probe.json");
            
            // Read center pixel from framebuffer
            // We need to sample fog values from the debug views
            // For now, calculate theoretical fog values at camera position
            
            float cameraY = player.getCamera().getPosition().y;
            float viewDist = 50.0f; // Approximate distance to center of view
            
            // Distance fog factor
            float fogStart = renderer.getFogStart();
            float fogEnd = renderer.getFogEnd();
            float fogDist = Math.max(0, Math.min(1, (viewDist - fogStart) / (fogEnd - fogStart)));
            
            // Height fog (disabled in current build)
            float fogHeight = 0.0f;
            
            // Combined
            float fogCombined = Math.max(fogDist, fogHeight);
            
            // Linear depth
            float near = player.getCamera().getNearPlane();
            float far = player.getCamera().getFarPlane();
            float depthLinear = viewDist / far;
            
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"fog_dist\": ").append(fogDist).append(",\n");
            json.append("  \"fog_height\": ").append(fogHeight).append(",\n");
            json.append("  \"fog_combined\": ").append(fogCombined).append(",\n");
            json.append("  \"depth_linear\": ").append(depthLinear).append(",\n");
            json.append("  \"world_y\": ").append(cameraY).append(",\n");
            json.append("  \"view_distance\": ").append(viewDist).append("\n");
            json.append("}\n");
            
            FileWriter writer = new FileWriter(file);
            writer.write(json.toString());
            writer.close();
            System.out.println("[ProfileCapture] Saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[ProfileCapture] Failed to save probe.json: " + e.getMessage());
        }
    }
    
    private void saveSpawnReport(String profileDir, CaptureProfile profile) {
        try {
            File file = new File(profileDir, "spawn_report.json");
            
            GenPipeline pipeline = chunkManager != null ? chunkManager.getPipeline() : null;
            SpawnPointFinder.SpawnPoint spawn = null;
            if (pipeline != null) {
                spawn = SpawnPointFinder.find(pipeline.getContext());
            }
            
            float spawnX = spawn != null ? (float) spawn.x() : player.getCamera().getPosition().x;
            float spawnY = spawn != null ? (float) spawn.y() : player.getCamera().getPosition().y;
            float spawnZ = spawn != null ? (float) spawn.z() : player.getCamera().getPosition().z;
            
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"spawn_x\": ").append(spawnX).append(",\n");
            json.append("  \"spawn_y\": ").append(spawnY).append(",\n");
            json.append("  \"spawn_z\": ").append(spawnZ).append(",\n");
            json.append("  \"world_seed\": ").append(chunkManager != null ? chunkManager.getSeed() : 0).append(",\n");
            json.append("  \"git_head_hash\": \"").append(gitHeadHash).append("\",\n");
            json.append("  \"profile_name\": \"").append(profile.getName()).append("\"\n");
            json.append("}\n");
            
            FileWriter writer = new FileWriter(file);
            writer.write(json.toString());
            writer.close();
            System.out.println("[ProfileCapture] Saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[ProfileCapture] Failed to save spawn_report.json: " + e.getMessage());
        }
    }
}
