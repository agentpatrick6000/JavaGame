package com.voxelgame.core;

import com.voxelgame.render.PostFX;
import com.voxelgame.render.Renderer;
import com.voxelgame.render.ShadowRenderer;
import com.voxelgame.render.SkySystem;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.Player;
import com.voxelgame.ui.Screenshot;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldTime;
import com.voxelgame.world.stream.ChunkManager;

import java.io.*;
import java.nio.ByteBuffer;
import java.time.Instant;

import static org.lwjgl.opengl.GL33.*;

/**
 * Lighting test mode for deterministic lighting diagnosis.
 * 
 * Places a test rig and captures at three times of day:
 * - NOON (6000 ticks) - full sun
 * - SUNSET (12000 ticks) - low sun angle
 * - MIDNIGHT (18000 ticks) - no sun
 * 
 * Output: artifacts/lighting_test/{NOON|SUNSET|MIDNIGHT}/
 * Contains: final.png, albedo.png, lighting.png, depth.png, render_state.json
 */
public class LightingTest {

    // Test times of day (in WorldTime ticks: 0=6AM, 6000=noon, 12000=6PM, 18000=midnight)
    private static final int TIME_NOON = 6000;
    private static final int TIME_SUNSET = 12000;
    private static final int TIME_MIDNIGHT = 18000;
    private static final String[] TIME_NAMES = {"NOON", "SUNSET", "MIDNIGHT"};
    private static final int[] TIME_VALUES = {TIME_NOON, TIME_SUNSET, TIME_MIDNIGHT};

    // Test rig location (near spawn, in chunk 0,0)
    private static final int RIG_X = 8;
    private static final int RIG_Y = 64;
    private static final int RIG_Z = 8;

    // Camera pose (looking at the test rig)
    private static final float CAM_X = 20.0f;
    private static final float CAM_Y = 68.0f;
    private static final float CAM_Z = 20.0f;
    private static final float CAM_YAW = -135.0f;  // Looking toward origin
    private static final float CAM_PITCH = -15.0f; // Slightly down

    // Phases
    private static final int PHASE_INIT = 0;
    private static final int PHASE_PLACE_RIG = 1;
    private static final int PHASE_WARMUP = 2;
    private static final int PHASE_CAPTURE = 3;
    private static final int PHASE_NEXT_TIME = 4;
    private static final int PHASE_COMPLETE = 5;

    private int phase = PHASE_INIT;
    private int currentTimeIndex = 0;
    private int warmupFrames = 0;
    private static final int WARMUP_FRAME_COUNT = 120;
    private int captureStep = 0;
    private float timer = 0;

    private final String outputDir;
    private String gitHeadHash = "unknown";

    // References
    private Player player;
    private WorldTime worldTime;
    private Renderer renderer;
    private PostFX postFX;
    private ChunkManager chunkManager;
    private World world;
    private SkySystem skySystem;
    private int fbWidth, fbHeight;

    // Captured pixel data for center probe
    private float[] centerPixelHDR = new float[3];
    private float[] centerPixelFinal = new float[3];

    public LightingTest(String outputDir) {
        this.outputDir = outputDir != null ? outputDir : "artifacts/lighting_test";

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
            System.err.println("[LightingTest] Failed to get git hash: " + e.getMessage());
        }
    }

    public void setReferences(Player player, WorldTime worldTime, Renderer renderer,
                              PostFX postFX, ChunkManager chunkManager, World world,
                              SkySystem skySystem, int fbW, int fbH) {
        this.player = player;
        this.worldTime = worldTime;
        this.renderer = renderer;
        this.postFX = postFX;
        this.chunkManager = chunkManager;
        this.world = world;
        this.skySystem = skySystem;
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
            case PHASE_PLACE_RIG -> handlePlaceRig();
            case PHASE_WARMUP -> handleWarmup();
            case PHASE_CAPTURE -> handleCapture();
            case PHASE_NEXT_TIME -> handleNextTime();
        }
    }

    private void handleInit() {
        if (timer < 0.5f) {
            // Setup player state
            player.setGameMode(GameMode.CREATIVE);
            if (!player.isFlyMode()) {
                player.toggleFlyMode();
            }
            return;
        }

        System.out.println("[LightingTest] Starting lighting test...");
        System.out.println("[LightingTest] Output: " + outputDir);

        phase = PHASE_PLACE_RIG;
        timer = 0;
    }

    private void handlePlaceRig() {
        if (timer < 0.5f) return;

        // Place test rig: flat ground plane, 3 pillars, overhang
        placeTestRig();

        System.out.println("[LightingTest] Test rig placed at " + RIG_X + ", " + RIG_Y + ", " + RIG_Z);

        phase = PHASE_WARMUP;
        timer = 0;
        warmupFrames = 0;
    }

    private void placeTestRig() {
        // Block IDs (from Blocks.java constants)
        byte STONE = 1;
        byte COBBLESTONE = 4;
        byte WOOD = 5;
        byte BRICK = 45;

        // Clear an area first and place flat ground
        for (int dx = -5; dx <= 10; dx++) {
            for (int dz = -5; dz <= 10; dz++) {
                // Ground plane
                world.setBlock(RIG_X + dx, RIG_Y - 1, RIG_Z + dz, STONE);
                // Clear above
                for (int dy = 0; dy < 10; dy++) {
                    world.setBlock(RIG_X + dx, RIG_Y + dy, RIG_Z + dz, (byte) 0);
                }
            }
        }

        // Pillar 1: height 3 (closest to camera)
        for (int dy = 0; dy < 3; dy++) {
            world.setBlock(RIG_X, RIG_Y + dy, RIG_Z, COBBLESTONE);
        }

        // Pillar 2: height 5 (middle)
        for (int dy = 0; dy < 5; dy++) {
            world.setBlock(RIG_X + 4, RIG_Y + dy, RIG_Z, BRICK);
        }

        // Pillar 3: height 7 (tallest, furthest)
        for (int dy = 0; dy < 7; dy++) {
            world.setBlock(RIG_X + 8, RIG_Y + dy, RIG_Z, WOOD);
        }

        // Overhang "cave mouth" - 2 blocks wide, 2 tall, 3 deep
        // Creates a shadowed area underneath
        int overhangX = RIG_X;
        int overhangZ = RIG_Z + 6;
        // Floor
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                world.setBlock(overhangX + dx, RIG_Y - 1, overhangZ + dz, STONE);
            }
        }
        // Walls (sides)
        for (int dy = 0; dy < 3; dy++) {
            for (int dz = 0; dz < 3; dz++) {
                world.setBlock(overhangX - 1, RIG_Y + dy, overhangZ + dz, COBBLESTONE);
                world.setBlock(overhangX + 3, RIG_Y + dy, overhangZ + dz, COBBLESTONE);
            }
        }
        // Roof
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                world.setBlock(overhangX + dx, RIG_Y + 3, overhangZ + dz, COBBLESTONE);
            }
        }
        // Back wall
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                world.setBlock(overhangX + dx, RIG_Y + dy, overhangZ + 3, COBBLESTONE);
            }
        }

        // Mesh updates happen automatically when blocks are modified
    }

    private void handleWarmup() {
        warmupFrames++;

        // Set camera pose
        player.getCamera().getPosition().set(CAM_X, CAM_Y, CAM_Z);
        player.getCamera().setYaw(CAM_YAW);
        player.getCamera().setPitch(CAM_PITCH);

        // Set time of day
        if (worldTime != null) {
            worldTime.setWorldTick(TIME_VALUES[currentTimeIndex]);
        }

        if (warmupFrames >= WARMUP_FRAME_COUNT) {
            String timeName = TIME_NAMES[currentTimeIndex];
            System.out.println("[LightingTest] Warmup complete for " + timeName);

            // Create output directory
            String dir = outputDir + "/" + timeName;
            new File(dir).mkdirs();

            phase = PHASE_CAPTURE;
            timer = 0;
            captureStep = 0;
        }
    }

    // Two-phase capture: even steps = setup mode, odd steps = capture after render
    private void handleCapture() {
        // Keep camera and time fixed
        player.getCamera().getPosition().set(CAM_X, CAM_Y, CAM_Z);
        player.getCamera().setYaw(CAM_YAW);
        player.getCamera().setPitch(CAM_PITCH);
        if (worldTime != null) {
            worldTime.setWorldTick(TIME_VALUES[currentTimeIndex]);
        }

        String timeName = TIME_NAMES[currentTimeIndex];
        String dir = outputDir + "/" + timeName;

        // Wait for at least one frame between mode change and capture
        if (timer < 0.1f) return;

        switch (captureStep) {
            // Step 0: Setup final mode
            case 0 -> {
                renderer.setDebugView(0);
                if (postFX != null) postFX.setCompositeDebugMode(PostFX.COMPOSITE_NORMAL);
                captureStep++;
                timer = 0;
            }
            // Step 1: Capture final (after render with mode 0)
            case 1 -> {
                String path = Screenshot.captureToFile(fbWidth, fbHeight, dir + "/final.png");
                System.out.println("[LightingTest] Saved: " + path);
                captureStep++;
                timer = 0;
            }
            // Step 2: Setup albedo mode
            case 2 -> {
                renderer.setDebugView(1);
                captureStep++;
                timer = 0;
            }
            // Step 3: Capture albedo
            case 3 -> {
                String path = Screenshot.captureToFile(fbWidth, fbHeight, dir + "/albedo.png");
                System.out.println("[LightingTest] Saved: " + path);
                captureStep++;
                timer = 0;
            }
            // Step 4: Setup lighting mode
            case 4 -> {
                renderer.setDebugView(2);
                captureStep++;
                timer = 0;
            }
            // Step 5: Capture lighting
            case 5 -> {
                String path = Screenshot.captureToFile(fbWidth, fbHeight, dir + "/lighting.png");
                System.out.println("[LightingTest] Saved: " + path);
                captureStep++;
                timer = 0;
            }
            // Step 6: Setup depth mode
            case 6 -> {
                renderer.setDebugView(3);
                captureStep++;
                timer = 0;
            }
            // Step 7: Capture depth
            case 7 -> {
                String path = Screenshot.captureToFile(fbWidth, fbHeight, dir + "/depth.png");
                System.out.println("[LightingTest] Saved: " + path);
                captureStep++;
                timer = 0;
            }
            // Step 8: Setup HDR pre-tonemap mode
            case 8 -> {
                renderer.setDebugView(0);
                if (postFX != null) postFX.setCompositeDebugMode(PostFX.COMPOSITE_HDR_PRE_TONEMAP);
                captureStep++;
                timer = 0;
            }
            // Step 9: Capture HDR pre-tonemap
            case 9 -> {
                String path = Screenshot.captureToFile(fbWidth, fbHeight, dir + "/hdr_pre_tonemap.png");
                System.out.println("[LightingTest] Saved: " + path);
                captureStep++;
                timer = 0;
            }
            // Step 10: Setup LDR post-tonemap mode
            case 10 -> {
                renderer.setDebugView(0);
                if (postFX != null) postFX.setCompositeDebugMode(PostFX.COMPOSITE_LDR_POST_TONEMAP);
                captureStep++;
                timer = 0;
            }
            // Step 11: Capture LDR post-tonemap
            case 11 -> {
                String path = Screenshot.captureToFile(fbWidth, fbHeight, dir + "/ldr_post_tonemap.png");
                System.out.println("[LightingTest] Saved: " + path);
                captureStep++;
                timer = 0;
            }
            // Step 12: Finalize - reset modes, read probe, save state
            case 12 -> {
                renderer.setDebugView(0);
                if (postFX != null) postFX.setCompositeDebugMode(PostFX.COMPOSITE_NORMAL);

                // Read center pixel (probe)
                readCenterPixel();

                // Save render_state.json with lighting data and uniform audit
                saveRenderState(dir);

                phase = PHASE_NEXT_TIME;
                timer = 0;
            }
        }
    }

    private void readCenterPixel() {
        // Read center pixel from the current framebuffer
        int cx = fbWidth / 2;
        int cy = fbHeight / 2;
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);

        glReadPixels(cx, cy, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        centerPixelFinal[0] = (pixel.get(0) & 0xFF) / 255.0f;
        centerPixelFinal[1] = (pixel.get(1) & 0xFF) / 255.0f;
        centerPixelFinal[2] = (pixel.get(2) & 0xFF) / 255.0f;
    }

    private void handleNextTime() {
        currentTimeIndex++;

        if (currentTimeIndex >= TIME_VALUES.length) {
            phase = PHASE_COMPLETE;
            System.out.println("[LightingTest] All captures complete.");
        } else {
            phase = PHASE_WARMUP;
            warmupFrames = 0;
            timer = 0;
            captureStep = 0;

            System.out.println("[LightingTest] Starting " + TIME_NAMES[currentTimeIndex]);
        }
    }

    private void saveRenderState(String dir) {
        try {
            File file = new File(dir, "render_state.json");

            int timeOfDay = TIME_VALUES[currentTimeIndex];
            float normalizedTime = SkySystem.worldTimeToNormalized(timeOfDay);

            // Get lighting values from sky system
            float[] sunDir = skySystem != null ? skySystem.getSunDirection(normalizedTime) : new float[]{0, 1, 0};
            float[] sunColor = skySystem != null ? skySystem.getSunColor(normalizedTime) : new float[]{1, 1, 1};
            float sunIntensity = skySystem != null ? skySystem.getSunIntensity(normalizedTime) : 1.0f;

            float[] zenithColor = skySystem != null ? skySystem.getZenithColor(normalizedTime) : new float[]{0.5f, 0.7f, 1.0f};
            float[] horizonColor = skySystem != null ? skySystem.getHorizonColor(normalizedTime) : new float[]{0.7f, 0.8f, 1.0f};
            float skyIntensity = skySystem != null ? skySystem.getSkyIntensity(normalizedTime) : 1.0f;

            // Shadow settings
            ShadowRenderer shadowRenderer = renderer != null ? renderer.getShadowRenderer() : null;
            boolean shadowsEnabled = shadowRenderer != null && shadowRenderer.isShadowsEnabled();

            // Probe data - estimate contributions based on lighting model
            // skyRGB = skyColor * skyVis * uSkyIntensity
            // sunRGB = uSunColor * NdotL * uSunIntensity * skyVis * shadow * 0.6
            float estimatedSkyVis = 1.0f;  // Open sky
            float estimatedNdotL = Math.max(0, sunDir[1]);  // Y component for horizontal surface
            float estimatedShadow = shadowsEnabled ? 1.0f : 1.0f;  // Assume lit

            float skyContrib = (zenithColor[0] + zenithColor[1] + zenithColor[2]) / 3.0f * estimatedSkyVis * skyIntensity;
            float sunContrib = (sunColor[0] + sunColor[1] + sunColor[2]) / 3.0f * estimatedNdotL * sunIntensity * estimatedSkyVis * estimatedShadow * 0.6f;

            // Final luminance from center pixel
            float finalLuminance = 0.2126f * centerPixelFinal[0] + 0.7152f * centerPixelFinal[1] + 0.0722f * centerPixelFinal[2];

            boolean srgbEnabled = glIsEnabled(GL_FRAMEBUFFER_SRGB);

            StringBuilder json = new StringBuilder();
            json.append("{\n");

            // Git and basic info
            json.append("  \"git_head_hash\": \"").append(gitHeadHash).append("\",\n");
            json.append("  \"time_of_day_name\": \"").append(TIME_NAMES[currentTimeIndex]).append("\",\n");
            json.append("  \"time_of_day_ticks\": ").append(timeOfDay).append(",\n");
            json.append("  \"time_normalized\": ").append(normalizedTime).append(",\n");

            // Camera
            json.append("  \"camera_pos\": [").append(CAM_X).append(", ").append(CAM_Y).append(", ").append(CAM_Z).append("],\n");
            json.append("  \"camera_yaw\": ").append(CAM_YAW).append(",\n");
            json.append("  \"camera_pitch\": ").append(CAM_PITCH).append(",\n");

            // Sun
            json.append("  \"sun_dir\": [").append(sunDir[0]).append(", ").append(sunDir[1]).append(", ").append(sunDir[2]).append("],\n");
            json.append("  \"sun_color\": [").append(sunColor[0]).append(", ").append(sunColor[1]).append(", ").append(sunColor[2]).append("],\n");
            json.append("  \"sun_intensity\": ").append(sunIntensity).append(",\n");

            // Moon (not implemented separately - included in sky model)
            json.append("  \"moon_dir\": [0.0, -1.0, 0.0],\n");
            json.append("  \"moon_color\": [0.2, 0.2, 0.3],\n");
            json.append("  \"moon_intensity\": ").append(sunIntensity < 0.1f ? 0.02f : 0.0f).append(",\n");

            // Sky ambient
            json.append("  \"sky_ambient_color\": [").append(zenithColor[0]).append(", ").append(zenithColor[1]).append(", ").append(zenithColor[2]).append("],\n");
            json.append("  \"sky_ambient_intensity\": ").append(skyIntensity).append(",\n");
            json.append("  \"sky_horizon_color\": [").append(horizonColor[0]).append(", ").append(horizonColor[1]).append(", ").append(horizonColor[2]).append("],\n");

            // Block light (not used in test scene - no torches)
            json.append("  \"block_light_color\": [1.0, 0.9, 0.7],\n");
            json.append("  \"block_light_intensity\": 0.0,\n");

            // Exposure and tonemap
            float exposure = postFX != null ? postFX.getExposureMultiplier() : 1.0f;
            json.append("  \"exposure_multiplier_runtime\": ").append(exposure).append(",\n");
            json.append("  \"tonemap_operator\": \"ACES\",\n");
            json.append("  \"gamma_mode\": \"sRGB\",\n");
            json.append("  \"srgb_framebuffer_enabled_runtime\": ").append(srgbEnabled).append(",\n");

            // Shadow settings
            json.append("  \"shadow_enabled\": ").append(shadowsEnabled).append(",\n");
            json.append("  \"shadow_map_resolution\": 2048,\n");
            json.append("  \"shadow_distance_world\": 256.0,\n");
            json.append("  \"shadow_bias\": 0.005,\n");
            json.append("  \"shadow_normal_bias\": 0.01,\n");

            // Center pixel probe
            json.append("  \"center_pixel_probe\": {\n");
            json.append("    \"final_luminance\": ").append(finalLuminance).append(",\n");
            json.append("    \"final_rgb\": [").append(centerPixelFinal[0]).append(", ").append(centerPixelFinal[1]).append(", ").append(centerPixelFinal[2]).append("],\n");
            json.append("    \"direct_diffuse_contrib_estimate\": ").append(sunContrib).append(",\n");
            json.append("    \"direct_spec_contrib\": 0.0,\n");
            json.append("    \"ambient_contrib_estimate\": ").append(skyContrib).append(",\n");
            json.append("    \"shadow_factor\": ").append(estimatedShadow).append(",\n");
            json.append("    \"block_light_contrib\": 0.0\n");
            json.append("  },\n");

            // Uniform audit from renderer
            json.append("  \"shader_uniform_audit\": ").append(renderer.getUniformAuditJson()).append(",\n");
            
            // Timestamp
            json.append("  \"capture_timestamp\": \"").append(Instant.now().toString()).append("\"\n");
            json.append("}\n");

            FileWriter writer = new FileWriter(file);
            writer.write(json.toString());
            writer.close();
            System.out.println("[LightingTest] Saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[LightingTest] Failed to save render_state.json: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
