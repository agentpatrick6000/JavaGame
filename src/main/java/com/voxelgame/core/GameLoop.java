package com.voxelgame.core;

import com.voxelgame.agent.ActionQueue;
import com.voxelgame.agent.AgentServer;
import com.voxelgame.input.AutomationController;
import com.voxelgame.platform.Input;
import com.voxelgame.platform.Window;
import com.voxelgame.render.BlockHighlight;
import com.voxelgame.render.GLInit;
import com.voxelgame.render.Renderer;
import com.voxelgame.save.SaveManager;
import com.voxelgame.save.WorldMeta;
import com.voxelgame.sim.Controller;
import com.voxelgame.sim.Physics;
import com.voxelgame.sim.Player;
import com.voxelgame.ui.BitmapFont;
import com.voxelgame.ui.DebugOverlay;
import com.voxelgame.ui.Hud;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.Lighting;
import com.voxelgame.world.Raycast;
import com.voxelgame.world.World;
import com.voxelgame.world.gen.GenPipeline;
import com.voxelgame.world.gen.SpawnPointFinder;
import com.voxelgame.world.stream.ChunkGenerationWorker;
import com.voxelgame.world.stream.ChunkManager;

import java.io.IOException;
import java.util.Set;

import static org.lwjgl.opengl.GL33.*;

/**
 * Game loop integrating all subsystems.
 */
public class GameLoop {

    private static final String WORLD_NAME = "default";
    private static final float AUTO_SAVE_INTERVAL = 60.0f; // seconds

    private Window window;
    private Time time;
    private Player player;
    private Controller controller;
    private Physics physics;
    private World world;
    private ChunkManager chunkManager;
    private Renderer renderer;
    private SaveManager saveManager;

    // UI
    private Hud hud;
    private BitmapFont bitmapFont;
    private DebugOverlay debugOverlay;
    private BlockHighlight blockHighlight;

    // Current raycast hit (updated each frame)
    private Raycast.HitResult currentHit;

    // Automation
    private boolean automationMode = false;
    private String scriptPath = null;
    private AutomationController automationController;

    // Agent interface
    private boolean agentServerMode = false;
    private AgentServer agentServer;
    private ActionQueue agentActionQueue;

    // Auto-save timer
    private float autoSaveTimer = 0;

    /** Enable automation mode (socket server + optional script). */
    public void setAutomationMode(boolean enabled) { this.automationMode = enabled; }

    /** Enable agent server mode (WebSocket interface for AI agents). */
    public void setAgentServerMode(boolean enabled) { this.agentServerMode = enabled; }

    /** Set path to automation script file. */
    public void setScriptPath(String path) { this.scriptPath = path; }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        window = new Window(1280, 720, "VoxelGame");
        GLInit.init();
        GLInit.setViewport(window.getWidth(), window.getHeight());

        time = new Time();
        time.init();

        player = new Player();
        controller = new Controller(player);
        physics = new Physics();

        Input.init(window.getHandle());
        Input.lockCursor();

        world = new World();
        physics.setWorld(world);
        renderer = new Renderer(world);
        renderer.init();

        // Initialize save system
        saveManager = new SaveManager(WORLD_NAME);

        chunkManager = new ChunkManager(world);
        chunkManager.setSaveManager(saveManager);

        // Load or create world
        long seed;
        boolean loadedFromSave = false;

        if (saveManager.worldExists()) {
            // Load existing world
            try {
                WorldMeta meta = saveManager.loadMeta();
                if (meta != null) {
                    seed = meta.getSeed();
                    chunkManager.setSeed(seed);
                    chunkManager.init(renderer.getAtlas());

                    // Restore player position and rotation
                    player.getCamera().getPosition().set(
                        meta.getPlayerX(), meta.getPlayerY(), meta.getPlayerZ()
                    );
                    player.getCamera().setYaw(meta.getPlayerYaw());
                    player.getCamera().setPitch(meta.getPlayerPitch());

                    loadedFromSave = true;
                    System.out.println("Loaded world '" + WORLD_NAME + "' (seed=" + seed + ")");
                    System.out.println("  Player position: " +
                        meta.getPlayerX() + ", " + meta.getPlayerY() + ", " + meta.getPlayerZ());
                }
            } catch (IOException e) {
                System.err.println("Failed to load world meta, creating new world: " + e.getMessage());
            }
        }

        if (!loadedFromSave) {
            // Create new world
            seed = ChunkGenerationWorker.DEFAULT_SEED;
            chunkManager.setSeed(seed);
            chunkManager.init(renderer.getAtlas());

            // Find spawn point
            GenPipeline pipeline = chunkManager.getPipeline();
            if (pipeline != null) {
                SpawnPointFinder.SpawnPoint spawn = SpawnPointFinder.find(pipeline.getContext());
                player.getCamera().getPosition().set(
                    (float) spawn.x(), (float) spawn.y(), (float) spawn.z()
                );
                System.out.println("New world — Spawn point: " + spawn.x() + ", " + spawn.y() + ", " + spawn.z());
            }

            // Save initial metadata
            try {
                WorldMeta meta = new WorldMeta(seed);
                meta.setPlayerPosition(
                    player.getPosition().x, player.getPosition().y, player.getPosition().z
                );
                meta.setPlayerRotation(
                    player.getCamera().getYaw(), player.getCamera().getPitch()
                );
                saveManager.saveMeta(meta);
                System.out.println("Created new world '" + WORLD_NAME + "' (seed=" + seed + ")");
            } catch (IOException e) {
                System.err.println("Failed to save initial world meta: " + e.getMessage());
            }
        }

        // UI
        hud = new Hud();
        hud.init();
        bitmapFont = new BitmapFont();
        bitmapFont.init();
        debugOverlay = new DebugOverlay(bitmapFont);
        blockHighlight = new BlockHighlight();
        blockHighlight.init();

        // Initial chunk load
        chunkManager.update(player);

        // Initialize automation if enabled
        if (automationMode) {
            automationController = new AutomationController();
            automationController.start(scriptPath);
            System.out.println("[Automation] Controller initialized");
        }

        // Initialize agent server if enabled
        if (agentServerMode) {
            agentActionQueue = new ActionQueue();
            agentServer = new AgentServer(agentActionQueue);
            controller.setAgentActionQueue(agentActionQueue);
            agentServer.start();
            System.out.println("[AgentServer] WebSocket server starting on port " + AgentServer.DEFAULT_PORT);
        }

        System.out.println("VoxelGame initialized successfully!");
    }

    private void loop() {
        while (!window.shouldClose() && 
               (automationController == null || !automationController.isQuitRequested())) {
            time.update();
            float dt = time.getDeltaTime();

            window.pollEvents();

            if (window.wasResized()) {
                GLInit.setViewport(window.getWidth(), window.getHeight());
            }

            // ---- Handle debug toggle (F3) ----
            if (Input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F3)) {
                debugOverlay.toggle();
            }

            // ---- Update ----
            controller.update(dt);
            physics.step(player, dt);
            chunkManager.update(player);

            // Raycast every frame for block highlight
            currentHit = Raycast.cast(
                world, player.getCamera().getPosition(), player.getCamera().getFront(), 8.0f
            );
            handleBlockInteraction();

            // Broadcast state to connected agents
            if (agentServer != null) {
                agentServer.broadcastState(player, world);
            }

            // ---- Auto-save ----
            autoSaveTimer += dt;
            if (autoSaveTimer >= AUTO_SAVE_INTERVAL) {
                autoSaveTimer = 0;
                performAutoSave();
            }

            // ---- Render 3D ----
            int w = window.getWidth();
            int h = window.getHeight();

            // Reset GL state for world rendering (prevent leakage from UI passes)
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDisable(GL_BLEND);
            glEnable(GL_CULL_FACE);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.render(player.getCamera(), w, h);

            // ---- Block highlight ----
            if (currentHit != null) {
                blockHighlight.render(player.getCamera(), w, h,
                    currentHit.x(), currentHit.y(), currentHit.z());
            }

            // ---- Render UI overlay (2D) ----
            // Reset state for UI pass
            glDisable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(GL_CULL_FACE);

            hud.render(w, h, player);
            debugOverlay.render(player, world, time.getFps(), w, h, controller.isSprinting());

            // ---- End frame ----
            Input.endFrame();
            window.swapBuffers();
        }
    }

    private void handleBlockInteraction() {
        // Check for agent attack/use actions (always valid, not gated by cursor lock)
        boolean agentAttack = controller.consumeAgentAttack();
        boolean agentUse = controller.consumeAgentUse();

        boolean leftClick = agentAttack || (Input.isCursorLocked() && Input.isLeftMouseClicked());
        boolean rightClick = agentUse || (Input.isCursorLocked() && Input.isRightMouseClicked());

        if (leftClick && currentHit != null) {
            // Capture block info before destroying it
            int brokenBlockId = world.getBlock(currentHit.x(), currentHit.y(), currentHit.z());
            String brokenBlockName = Blocks.get(brokenBlockId).name();

            world.setBlock(currentHit.x(), currentHit.y(), currentHit.z(), 0); // AIR
            Set<ChunkPos> affected = Lighting.onBlockRemoved(world, currentHit.x(), currentHit.y(), currentHit.z());
            chunkManager.rebuildMeshAt(currentHit.x(), currentHit.y(), currentHit.z());
            chunkManager.rebuildChunks(affected);

            // Report result for agent-triggered attacks
            if (agentAttack && agentActionQueue != null) {
                agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                    "action_attack", true, brokenBlockName,
                    currentHit.x(), currentHit.y(), currentHit.z()));
            }
        } else if (agentAttack && agentActionQueue != null) {
            // Agent attacked but missed (no block in crosshair)
            agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                "action_attack", false, null, 0, 0, 0));
        }

        if (rightClick && currentHit != null) {
            int px = currentHit.x() + currentHit.nx();
            int py = currentHit.y() + currentHit.ny();
            int pz = currentHit.z() + currentHit.nz();
            int placedBlockId = player.getSelectedBlock();
            world.setBlock(px, py, pz, placedBlockId);
            Set<ChunkPos> affected = Lighting.onBlockPlaced(world, px, py, pz);
            chunkManager.rebuildMeshAt(px, py, pz);
            chunkManager.rebuildChunks(affected);

            // Report result for agent-triggered use actions
            if (agentUse && agentActionQueue != null) {
                agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                    "action_use", true, Blocks.get(placedBlockId).name(), px, py, pz));
            }
        } else if (agentUse && agentActionQueue != null) {
            // Agent used but missed (no block surface to place on)
            agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                "action_use", false, null, 0, 0, 0));
        }
    }

    /**
     * Auto-save: save modified chunks and player position.
     */
    private void performAutoSave() {
        try {
            int saved = saveManager.saveModifiedChunks(world);
            savePlayerMeta();
            if (saved > 0) {
                System.out.println("Auto-saved " + saved + " chunks");
            }
        } catch (Exception e) {
            System.err.println("Auto-save failed: " + e.getMessage());
        }
    }

    /**
     * Save the current player position and rotation to world metadata.
     */
    private void savePlayerMeta() {
        try {
            WorldMeta meta = saveManager.loadMeta();
            if (meta == null) {
                meta = new WorldMeta(ChunkGenerationWorker.DEFAULT_SEED);
            }
            meta.setPlayerPosition(
                player.getPosition().x, player.getPosition().y, player.getPosition().z
            );
            meta.setPlayerRotation(
                player.getCamera().getYaw(), player.getCamera().getPitch()
            );
            meta.setLastPlayedAt(System.currentTimeMillis());
            saveManager.saveMeta(meta);
        } catch (IOException e) {
            System.err.println("Failed to save player meta: " + e.getMessage());
        }
    }

    private void cleanup() {
        // Stop agent server
        if (agentServer != null) {
            agentServer.shutdown();
        }

        // Stop automation
        if (automationController != null) {
            automationController.stop();
        }

        // Save everything on exit
        System.out.println("Saving world on exit...");
        try {
            int saved = saveManager.saveAllChunks(world);
            savePlayerMeta();
            System.out.println("Saved " + saved + " chunks on exit");
        } catch (Exception e) {
            System.err.println("Failed to save on exit: " + e.getMessage());
        }
        saveManager.close();

        chunkManager.shutdown();
        if (blockHighlight != null) blockHighlight.cleanup();
        if (bitmapFont != null) bitmapFont.cleanup();
        if (hud != null) hud.cleanup();
        renderer.cleanup();
        window.destroy();
    }

    public Window getWindow() { return window; }
    public Time getTime() { return time; }
    public Player getPlayer() { return player; }
}
