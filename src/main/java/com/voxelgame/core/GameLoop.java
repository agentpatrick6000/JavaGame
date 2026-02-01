package com.voxelgame.core;

import com.voxelgame.agent.ActionQueue;
import com.voxelgame.agent.AgentServer;
import com.voxelgame.input.AutomationController;
import com.voxelgame.platform.Input;
import com.voxelgame.platform.Window;
import com.voxelgame.render.BlockHighlight;
import com.voxelgame.render.GLInit;
import com.voxelgame.render.ItemEntityRenderer;
import com.voxelgame.render.Renderer;
import com.voxelgame.save.SaveManager;
import com.voxelgame.save.WorldMeta;
import com.voxelgame.sim.BlockBreakProgress;
import com.voxelgame.sim.Controller;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.ItemEntity;
import com.voxelgame.sim.ItemEntityManager;
import com.voxelgame.sim.Physics;
import com.voxelgame.sim.Player;
import com.voxelgame.ui.BitmapFont;
import com.voxelgame.ui.DeathScreen;
import com.voxelgame.ui.DebugOverlay;
import com.voxelgame.ui.Hud;
import com.voxelgame.ui.InventoryScreen;
import com.voxelgame.ui.Screenshot;
import com.voxelgame.world.Block;
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
    private DeathScreen deathScreen;
    private InventoryScreen inventoryScreen;

    // Survival mechanics
    private BlockBreakProgress blockBreakProgress;
    private ItemEntityManager itemEntityManager;
    private ItemEntityRenderer itemEntityRenderer;

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

    // Auto-test mode (for automated screenshot testing)
    private boolean autoTestMode = false;
    private float autoTestTimer = 0;
    private int autoTestPhase = 0;

    /** Enable automation mode (socket server + optional script). */
    public void setAutomationMode(boolean enabled) { this.automationMode = enabled; }

    /** Enable agent server mode (WebSocket interface for AI agents). */
    public void setAgentServerMode(boolean enabled) { this.agentServerMode = enabled; }

    /** Set path to automation script file. */
    public void setScriptPath(String path) { this.scriptPath = path; }

    /** Enable auto-test mode (scripted screenshot sequence, then exit). */
    public void setAutoTestMode(boolean enabled) { this.autoTestMode = enabled; }

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

                    // Restore game mode, spawn point, and health
                    player.setGameMode(meta.getGameMode());
                    player.setSpawnPoint(meta.getSpawnX(), meta.getSpawnY(), meta.getSpawnZ());
                    if (!meta.getGameMode().isInvulnerable()) {
                        player.restoreHealth(meta.getPlayerHealth());
                    }

                    loadedFromSave = true;
                    System.out.println("Loaded world '" + WORLD_NAME + "' (seed=" + seed + ")");
                    System.out.println("  Player position: " +
                        meta.getPlayerX() + ", " + meta.getPlayerY() + ", " + meta.getPlayerZ());
                    System.out.println("  Game mode: " + meta.getGameMode());
                    System.out.println("  Health: " + meta.getPlayerHealth());
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
                player.setSpawnPoint((float) spawn.x(), (float) spawn.y(), (float) spawn.z());
                System.out.println("New world — Spawn point: " + spawn.x() + ", " + spawn.y() + ", " + spawn.z());
            }

            // Default to survival mode for new worlds
            player.setGameMode(GameMode.SURVIVAL);

            // Save initial metadata
            try {
                WorldMeta meta = new WorldMeta(seed);
                meta.setPlayerPosition(
                    player.getPosition().x, player.getPosition().y, player.getPosition().z
                );
                meta.setPlayerRotation(
                    player.getCamera().getYaw(), player.getCamera().getPitch()
                );
                meta.setGameMode(player.getGameMode());
                meta.setSpawnPoint(player.getSpawnX(), player.getSpawnY(), player.getSpawnZ());
                meta.setPlayerHealth(player.getHealth());
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
        deathScreen = new DeathScreen(bitmapFont);
        deathScreen.init();
        blockHighlight = new BlockHighlight();
        blockHighlight.init();

        // Inventory screen
        inventoryScreen = new InventoryScreen();
        inventoryScreen.init(bitmapFont);
        controller.setInventoryScreen(inventoryScreen);

        // Item entity renderer
        itemEntityRenderer = new ItemEntityRenderer();
        itemEntityRenderer.init();

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

            // ---- Handle respawn (R key when dead) ----
            if (player.isDead() && Input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_R)) {
                player.respawn();
                deathScreen.reset();
                if (!Input.isCursorLocked()) {
                    Input.lockCursor();
                }
            }

            // ---- Update damage flash ----
            player.updateDamageFlash(dt);

            // ---- Track pre-death state for death screen trigger ----
            boolean wasDead = player.isDead();

            // ---- Update ----
            controller.update(dt);
            physics.step(player, dt);
            chunkManager.update(player);

            // ---- Detect death transition (for death screen reset) ----
            if (player.isDead() && !wasDead) {
                deathScreen.reset();
                if (Input.isCursorLocked()) {
                    Input.unlockCursor();
                }
            }

            // ---- Update item entities ----
            itemEntityManager.update(dt, world, player, player.getInventory());

            // Raycast every frame for block highlight (skip when dead or inventory open)
            if (!player.isDead() && !controller.isInventoryOpen()) {
                currentHit = Raycast.cast(
                    world, player.getCamera().getPosition(), player.getCamera().getFront(), 8.0f
                );
                handleBlockInteraction(dt);
            } else {
                currentHit = null;
                controller.resetBreaking();
                hud.setBreakProgress(0);
            }

            // ---- Handle inventory mouse clicks ----
            if (inventoryScreen.isVisible() && Input.isLeftMouseClicked()) {
                double[] mx = new double[1], my = new double[1];
                org.lwjgl.glfw.GLFW.glfwGetCursorPos(window.getHandle(), mx, my);
                inventoryScreen.handleClick(player.getInventory(), mx[0], my[0],
                    window.getWidth(), window.getHeight());
            }

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

            // Reset GL state for world rendering
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDisable(GL_BLEND);
            glEnable(GL_CULL_FACE);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.render(player.getCamera(), w, h);

            // ---- Render item entities ----
            itemEntityRenderer.render(player.getCamera(), w, h, itemEntityManager.getItems());

            // ---- Block highlight ----
            if (currentHit != null) {
                blockHighlight.render(player.getCamera(), w, h,
                    currentHit.x(), currentHit.y(), currentHit.z());
            }

            // ---- Render UI overlay (2D) ----
            glDisable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(GL_CULL_FACE);

            hud.render(w, h, player);
            debugOverlay.render(player, world, time.getFps(), w, h, controller.isSprinting());

            // ---- Inventory screen (on top of HUD) ----
            if (inventoryScreen.isVisible()) {
                inventoryScreen.render(w, h, player.getInventory());
            }

            // ---- Death screen (on top of everything) ----
            if (player.isDead()) {
                deathScreen.render(w, h, dt);
            }

            // ---- Screenshot (F2) ----
            if (Input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F2)) {
                Screenshot.capture(w, h);
            }

            // ---- Auto-test mode (scripted screenshots) ----
            if (autoTestMode) {
                autoTestTimer += dt;
                switch (autoTestPhase) {
                    case 0:
                        if (autoTestTimer > 3.0f) { autoTestPhase = 1; autoTestTimer = 0; }
                        break;
                    case 1:
                        Screenshot.capture(w, h);
                        autoTestPhase = 2; autoTestTimer = 0;
                        break;
                    case 2:
                        if (autoTestTimer > 1.0f) {
                            autoTestPhase = player.isDead() ? 3 : 5;
                            autoTestTimer = 0;
                        }
                        break;
                    case 3:
                        Screenshot.capture(w, h);
                        autoTestPhase = 4; autoTestTimer = 0;
                        break;
                    case 4:
                        if (autoTestTimer > 2.0f) {
                            player.respawn(); deathScreen.reset();
                            autoTestPhase = 5; autoTestTimer = 0;
                        }
                        break;
                    case 5:
                        if (autoTestTimer > 3.0f) {
                            Screenshot.capture(w, h);
                            autoTestPhase = 6; autoTestTimer = 0;
                        }
                        break;
                    case 6:
                        if (autoTestTimer > 1.0f) {
                            System.out.println("[AutoTest] Test complete, exiting.");
                            window.requestClose();
                        }
                        break;
                }
            }

            // ---- End frame ----
            Input.endFrame();
            window.swapBuffers();
        }
    }

    private void handleBlockInteraction(float dt) {
        boolean agentAttack = controller.consumeAgentAttack();
        boolean agentUse = controller.consumeAgentUse();

        boolean isCreative = player.getGameMode() == GameMode.CREATIVE;

        // ---- Block Breaking (Left Click) ----
        boolean leftClickHeld = agentAttack || (Input.isCursorLocked() && Input.isLeftMouseDown());
        boolean leftClickPressed = agentAttack || (Input.isCursorLocked() && Input.isLeftMouseClicked());

        if (currentHit != null && (isCreative ? leftClickPressed : leftClickHeld)) {
            int bx = currentHit.x();
            int by = currentHit.y();
            int bz = currentHit.z();
            int blockId = world.getBlock(bx, by, bz);
            Block block = Blocks.get(blockId);

            if (block.isBreakable()) {
                if (isCreative) {
                    // Creative: instant break, no drops
                    breakBlock(bx, by, bz, blockId, false);
                    controller.resetBreaking();
                    hud.setBreakProgress(0);

                    if (agentAttack && agentActionQueue != null) {
                        agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                            "action_attack", true, block.name(), bx, by, bz));
                    }
                } else {
                    // Survival: time-based breaking via Controller
                    float breakTime = block.getBreakTime();
                    float progress = controller.updateBreaking(bx, by, bz, breakTime, dt);
                    hud.setBreakProgress(progress);

                    if (progress >= 1.0f) {
                        breakBlock(bx, by, bz, blockId, true);
                        controller.resetBreaking();
                        hud.setBreakProgress(0);

                        if (agentAttack && agentActionQueue != null) {
                            agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                                "action_attack", true, block.name(), bx, by, bz));
                        }
                    }
                }
            }
        } else {
            // Not holding attack or no target — reset breaking
            if (controller.isBreaking()) {
                controller.resetBreaking();
                hud.setBreakProgress(0);
            }

            if (agentAttack && agentActionQueue != null) {
                agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                    "action_attack", false, null, 0, 0, 0));
            }
        }

        // ---- Block Placing (Right Click) ----
        boolean rightClick = agentUse || (Input.isCursorLocked() && Input.isRightMouseClicked());

        if (rightClick && currentHit != null) {
            int px = currentHit.x() + currentHit.nx();
            int py = currentHit.y() + currentHit.ny();
            int pz = currentHit.z() + currentHit.nz();
            int placedBlockId = player.getSelectedBlock();

            if (placedBlockId > 0) {
                boolean canPlace;

                if (isCreative) {
                    canPlace = true;
                } else {
                    canPlace = player.consumeSelectedBlock();
                }

                if (canPlace) {
                    world.setBlock(px, py, pz, placedBlockId);
                    Set<ChunkPos> affected = Lighting.onBlockPlaced(world, px, py, pz);
                    chunkManager.rebuildMeshAt(px, py, pz);
                    chunkManager.rebuildChunks(affected);

                    if (agentUse && agentActionQueue != null) {
                        agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                            "action_use", true, Blocks.get(placedBlockId).name(), px, py, pz));
                    }
                }
            }
        } else if (agentUse && agentActionQueue != null) {
            agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                "action_use", false, null, 0, 0, 0));
        }
    }

    /**
     * Break a block: remove from world, update lighting, optionally spawn drops.
     */
    private void breakBlock(int bx, int by, int bz, int blockId, boolean spawnDrops) {
        Block block = Blocks.get(blockId);

        world.setBlock(bx, by, bz, 0); // AIR
        Set<ChunkPos> affected = Lighting.onBlockRemoved(world, bx, by, bz);
        chunkManager.rebuildMeshAt(bx, by, bz);
        chunkManager.rebuildChunks(affected);

        // Spawn item drop in survival mode
        if (spawnDrops) {
            int dropId = block.getDrop();
            if (dropId > 0) {
                itemEntityManager.spawnDrop(dropId, 1, bx, by, bz);
            }
        }
    }

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
            meta.setGameMode(player.getGameMode());
            meta.setSpawnPoint(player.getSpawnX(), player.getSpawnY(), player.getSpawnZ());
            meta.setPlayerHealth(player.getHealth());
            meta.setLastPlayedAt(System.currentTimeMillis());
            saveManager.saveMeta(meta);
        } catch (IOException e) {
            System.err.println("Failed to save player meta: " + e.getMessage());
        }
    }

    private void cleanup() {
        if (agentServer != null) agentServer.shutdown();
        if (automationController != null) automationController.stop();

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
        if (deathScreen != null) deathScreen.cleanup();
        if (inventoryScreen != null) inventoryScreen.cleanup();
        if (itemEntityRenderer != null) itemEntityRenderer.cleanup();
        if (bitmapFont != null) bitmapFont.cleanup();
        if (hud != null) hud.cleanup();
        renderer.cleanup();
        window.destroy();
    }

    public Window getWindow() { return window; }
    public Time getTime() { return time; }
    public Player getPlayer() { return player; }
}
