package com.voxelgame.core;

import com.voxelgame.agent.ActionQueue;
import com.voxelgame.agent.AgentServer;
import com.voxelgame.input.AutomationController;
import com.voxelgame.platform.Input;
import com.voxelgame.platform.Window;
import com.voxelgame.render.BlockHighlight;
import com.voxelgame.render.EntityRenderer;
import com.voxelgame.render.GLInit;
import com.voxelgame.render.ItemEntityRenderer;
import com.voxelgame.render.Renderer;
import com.voxelgame.save.SaveManager;
import com.voxelgame.save.WorldMeta;
import com.voxelgame.sim.BlockBreakProgress;
import com.voxelgame.sim.Controller;
import com.voxelgame.sim.Difficulty;
import com.voxelgame.sim.Entity;
import com.voxelgame.sim.EntityManager;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.ItemEntity;
import com.voxelgame.sim.ItemEntityManager;
import com.voxelgame.sim.MobSpawner;
import com.voxelgame.sim.Physics;
import com.voxelgame.sim.Player;
import com.voxelgame.sim.ToolItem;
import com.voxelgame.sim.Inventory;
import com.voxelgame.ui.BitmapFont;
import com.voxelgame.ui.DeathScreen;
import com.voxelgame.ui.DebugOverlay;
import com.voxelgame.ui.Hud;
import com.voxelgame.ui.InventoryScreen;
import com.voxelgame.ui.MainMenuScreen;
import com.voxelgame.ui.PauseMenuScreen;
import com.voxelgame.ui.Screenshot;
import com.voxelgame.ui.SettingsScreen;
import com.voxelgame.ui.WorldCreationScreen;
import com.voxelgame.ui.WorldListScreen;
import com.voxelgame.world.Block;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.Lighting;
import com.voxelgame.world.Raycast;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldTime;
import com.voxelgame.world.gen.GenPipeline;
import com.voxelgame.world.gen.SpawnPointFinder;
import com.voxelgame.world.stream.ChunkGenerationWorker;
import com.voxelgame.world.stream.ChunkManager;

import java.io.IOException;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/**
 * Game loop integrating all subsystems.
 * Supports screen states: MAIN_MENU, WORLD_LIST, WORLD_CREATION,
 * SETTINGS_MENU, IN_GAME, PAUSED, SETTINGS_INGAME.
 */
public class GameLoop {

    private static final float AUTO_SAVE_INTERVAL = 60.0f; // seconds

    /** Current screen state. */
    private enum ScreenState {
        MAIN_MENU,
        WORLD_LIST,
        WORLD_CREATION,
        SETTINGS_MENU,     // settings from main menu
        IN_GAME,
        PAUSED,
        SETTINGS_INGAME    // settings from pause menu
    }

    private ScreenState screenState = ScreenState.MAIN_MENU;

    private Window window;
    private Time time;
    private Player player;
    private Controller controller;
    private Physics physics;
    private World world;
    private ChunkManager chunkManager;
    private Renderer renderer;
    private SaveManager saveManager;

    // UI screens
    private BitmapFont bitmapFont;
    private MainMenuScreen mainMenuScreen;
    private WorldListScreen worldListScreen;
    private WorldCreationScreen worldCreationScreen;
    private PauseMenuScreen pauseMenuScreen;
    private SettingsScreen settingsScreen;

    // In-game UI
    private Hud hud;
    private DebugOverlay debugOverlay;
    private BlockHighlight blockHighlight;
    private DeathScreen deathScreen;
    private InventoryScreen inventoryScreen;

    // Survival mechanics
    private BlockBreakProgress blockBreakProgress;
    private ItemEntityManager itemEntityManager;
    private ItemEntityRenderer itemEntityRenderer;

    // Mob system
    private WorldTime worldTime;
    private EntityManager entityManager;
    private MobSpawner mobSpawner;
    private EntityRenderer entityRenderer;

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

    // Track which world is currently loaded
    private String currentWorldFolder = null;

    // Track whether game subsystems are initialized
    private boolean gameInitialized = false;

    // The ScreenState to return to from settings
    private ScreenState settingsReturnState = ScreenState.MAIN_MENU;

    /** Enable automation mode (socket server + optional script). */
    public void setAutomationMode(boolean enabled) { this.automationMode = enabled; }

    /** Enable agent server mode (WebSocket interface for AI agents). */
    public void setAgentServerMode(boolean enabled) { this.agentServerMode = enabled; }

    /** Set path to automation script file. */
    public void setScriptPath(String path) { this.scriptPath = path; }

    /** Enable auto-test mode (scripted screenshot sequence, then exit). */
    public void setAutoTestMode(boolean enabled) { this.autoTestMode = enabled; }

    /**
     * Skip the menu and go directly to a world (legacy / automation mode).
     * If worldName is null, uses "default".
     */
    public void setDirectWorld(String worldName) {
        this.currentWorldFolder = worldName != null ? worldName : "default";
    }

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

        Input.init(window.getHandle());

        // Initialize shared font
        bitmapFont = new BitmapFont();
        bitmapFont.init();

        // Initialize menu screens
        mainMenuScreen = new MainMenuScreen();
        mainMenuScreen.init(bitmapFont);
        mainMenuScreen.setCallback(new MainMenuScreen.MenuCallback() {
            @Override public void onSingleplayer() { switchToWorldList(); }
            @Override public void onSettings() { openSettings(ScreenState.MAIN_MENU); }
            @Override public void onQuit() { window.requestClose(); }
        });

        worldListScreen = new WorldListScreen();
        worldListScreen.init(bitmapFont);
        worldListScreen.setCallback(new WorldListScreen.WorldListCallback() {
            @Override public void onPlayWorld(String worldName) { loadAndStartWorld(worldName); }
            @Override public void onCreateNew() { switchToWorldCreation(); }
            @Override public void onBack() { switchToMainMenu(); }
        });

        worldCreationScreen = new WorldCreationScreen();
        worldCreationScreen.init(bitmapFont);
        worldCreationScreen.setCallback(new WorldCreationScreen.CreationCallback() {
            @Override
            public void onCreateWorld(String worldName, GameMode gameMode, Difficulty difficulty,
                                       String seed, boolean showCoordinates, boolean bonusChest) {
                createAndStartWorld(worldName, gameMode, difficulty, seed);
            }
            @Override public void onCancel() { switchToWorldList(); }
        });

        pauseMenuScreen = new PauseMenuScreen();
        pauseMenuScreen.init(bitmapFont);
        pauseMenuScreen.setCallback(new PauseMenuScreen.PauseCallback() {
            @Override public void onResume() { resumeGame(); }
            @Override public void onSettings() { openSettings(ScreenState.PAUSED); }
            @Override public void onSaveAndQuit() { saveAndQuitToTitle(); }
            @Override public void onQuitGame() { window.requestClose(); }
        });

        settingsScreen = new SettingsScreen();
        settingsScreen.init(bitmapFont);
        settingsScreen.setCallback(() -> closeSettings());

        // Check if direct world mode was requested (automation/legacy)
        if (currentWorldFolder != null) {
            // Skip menu, load world directly
            loadAndStartWorld(currentWorldFolder);
            System.out.println("VoxelGame initialized — direct world: " + currentWorldFolder);
        } else {
            // Set cursor visible for menu
            Input.unlockCursor();
            screenState = ScreenState.MAIN_MENU;
            System.out.println("VoxelGame initialized — showing main menu");
        }
    }

    // ---- Screen switching ----

    private void switchToMainMenu() {
        screenState = ScreenState.MAIN_MENU;
        Input.unlockCursor();
    }

    private void switchToWorldList() {
        worldListScreen.refreshWorldList();
        screenState = ScreenState.WORLD_LIST;
        Input.unlockCursor();
    }

    private void switchToWorldCreation() {
        worldCreationScreen.reset();
        screenState = ScreenState.WORLD_CREATION;
        Input.unlockCursor();
    }

    private void openSettings(ScreenState returnTo) {
        settingsReturnState = returnTo;
        if (returnTo == ScreenState.MAIN_MENU) {
            screenState = ScreenState.SETTINGS_MENU;
        } else {
            screenState = ScreenState.SETTINGS_INGAME;
        }
        Input.unlockCursor();
    }

    private void closeSettings() {
        if (settingsReturnState == ScreenState.PAUSED) {
            screenState = ScreenState.PAUSED;
            Input.unlockCursor();
        } else {
            screenState = ScreenState.MAIN_MENU;
            Input.unlockCursor();
        }
    }

    private void resumeGame() {
        screenState = ScreenState.IN_GAME;
        Input.lockCursor();
    }

    private void pauseGame() {
        screenState = ScreenState.PAUSED;
        Input.unlockCursor();
    }

    // ---- World lifecycle ----

    /**
     * Create a brand new world and start playing it.
     */
    private void createAndStartWorld(String displayName, GameMode gameMode,
                                      Difficulty difficulty, String seedText) {
        String folderName = SaveManager.toFolderName(displayName);

        // Generate seed
        long seed;
        if (seedText != null && !seedText.isEmpty()) {
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException e) {
                seed = seedText.hashCode(); // Use string hash as seed
            }
        } else {
            seed = System.nanoTime();
        }

        // Initialize game subsystems
        initGameSubsystems(folderName, seed, gameMode, difficulty, displayName, true);
    }

    /**
     * Load an existing world and start playing.
     */
    private void loadAndStartWorld(String folderName) {
        initGameSubsystems(folderName, 0, GameMode.SURVIVAL, Difficulty.NORMAL, null, false);
    }

    /**
     * Initialize all game subsystems for a world.
     */
    private void initGameSubsystems(String folderName, long seed, GameMode gameMode,
                                     Difficulty difficulty, String displayName, boolean isNew) {
        // Clean up any existing game state
        cleanupGameState();

        currentWorldFolder = folderName;
        saveManager = new SaveManager(folderName);

        player = new Player();
        controller = new Controller(player);
        physics = new Physics();

        world = new World();
        physics.setWorld(world);
        renderer = new Renderer(world);
        renderer.init();

        chunkManager = new ChunkManager(world);
        chunkManager.setSaveManager(saveManager);

        if (isNew) {
            // Create new world
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
            }

            player.setGameMode(gameMode);
            player.setDifficulty(difficulty);

            // Save initial metadata
            try {
                WorldMeta meta = new WorldMeta(seed);
                meta.setWorldName(displayName != null ? displayName : "New World");
                meta.setPlayerPosition(
                    player.getPosition().x, player.getPosition().y, player.getPosition().z
                );
                meta.setPlayerRotation(
                    player.getCamera().getYaw(), player.getCamera().getPitch()
                );
                meta.setGameMode(gameMode);
                meta.setDifficulty(difficulty);
                meta.setSpawnPoint(player.getSpawnX(), player.getSpawnY(), player.getSpawnZ());
                meta.setPlayerHealth(player.getHealth());
                saveManager.saveMeta(meta);
                System.out.println("Created new world '" + folderName + "' (seed=" + seed + ")");
            } catch (IOException e) {
                System.err.println("Failed to save initial world meta: " + e.getMessage());
            }
        } else {
            // Load existing world
            try {
                WorldMeta meta = saveManager.loadMeta();
                if (meta != null) {
                    seed = meta.getSeed();
                    chunkManager.setSeed(seed);
                    chunkManager.init(renderer.getAtlas());

                    player.getCamera().getPosition().set(
                        meta.getPlayerX(), meta.getPlayerY(), meta.getPlayerZ()
                    );
                    player.getCamera().setYaw(meta.getPlayerYaw());
                    player.getCamera().setPitch(meta.getPlayerPitch());
                    player.setGameMode(meta.getGameMode());
                    player.setDifficulty(meta.getDifficulty());
                    player.setSpawnPoint(meta.getSpawnX(), meta.getSpawnY(), meta.getSpawnZ());
                    if (!meta.getGameMode().isInvulnerable()) {
                        player.restoreHealth(meta.getPlayerHealth());
                    }
                    System.out.println("Loaded world '" + folderName + "' (seed=" + seed + ")");
                } else {
                    // Fallback: treat as new
                    seed = ChunkGenerationWorker.DEFAULT_SEED;
                    chunkManager.setSeed(seed);
                    chunkManager.init(renderer.getAtlas());
                }
            } catch (IOException e) {
                System.err.println("Failed to load world meta: " + e.getMessage());
                seed = ChunkGenerationWorker.DEFAULT_SEED;
                chunkManager.setSeed(seed);
                chunkManager.init(renderer.getAtlas());
            }
        }

        // UI
        hud = new Hud();
        hud.init();
        debugOverlay = new DebugOverlay(bitmapFont);
        deathScreen = new DeathScreen(bitmapFont);
        deathScreen.init();
        blockHighlight = new BlockHighlight();
        blockHighlight.init();

        inventoryScreen = new InventoryScreen();
        inventoryScreen.init(bitmapFont);
        controller.setInventoryScreen(inventoryScreen);

        blockBreakProgress = new BlockBreakProgress();
        itemEntityManager = new ItemEntityManager();
        itemEntityRenderer = new ItemEntityRenderer();
        itemEntityRenderer.init();

        worldTime = new WorldTime();
        entityManager = new EntityManager();
        mobSpawner = new MobSpawner();
        entityRenderer = new EntityRenderer();
        entityRenderer.init();

        // Initial chunk load
        chunkManager.update(player);

        // Initialize automation if enabled
        if (automationMode && automationController == null) {
            automationController = new AutomationController();
            automationController.start(scriptPath);
        }

        // Initialize agent server if enabled
        if (agentServerMode && agentServer == null) {
            agentActionQueue = new ActionQueue();
            agentServer = new AgentServer(agentActionQueue);
            controller.setAgentActionQueue(agentActionQueue);
            agentServer.start();
        }

        gameInitialized = true;
        autoSaveTimer = 0;

        // Switch to in-game state
        screenState = ScreenState.IN_GAME;
        Input.lockCursor();

        System.out.println("Game started — world: " + folderName);
    }

    /**
     * Save and quit back to the title screen.
     */
    private void saveAndQuitToTitle() {
        if (gameInitialized) {
            System.out.println("Saving world before returning to menu...");
            try {
                int saved = saveManager.saveAllChunks(world);
                savePlayerMeta();
                System.out.println("Saved " + saved + " chunks");
            } catch (Exception e) {
                System.err.println("Failed to save: " + e.getMessage());
            }
            cleanupGameState();
        }
        switchToMainMenu();
    }

    /**
     * Clean up all in-game state (to prepare for loading a different world or returning to menu).
     */
    private void cleanupGameState() {
        if (!gameInitialized) return;

        if (chunkManager != null) chunkManager.shutdown();
        if (saveManager != null) saveManager.close();
        if (blockHighlight != null) blockHighlight.cleanup();
        if (deathScreen != null) deathScreen.cleanup();
        if (inventoryScreen != null) inventoryScreen.cleanup();
        if (entityRenderer != null) entityRenderer.cleanup();
        if (itemEntityRenderer != null) itemEntityRenderer.cleanup();
        if (hud != null) hud.cleanup();
        if (renderer != null) renderer.cleanup();

        player = null;
        controller = null;
        physics = null;
        world = null;
        chunkManager = null;
        renderer = null;
        saveManager = null;
        hud = null;
        debugOverlay = null;
        blockHighlight = null;
        deathScreen = null;
        inventoryScreen = null;
        blockBreakProgress = null;
        itemEntityManager = null;
        itemEntityRenderer = null;
        worldTime = null;
        entityManager = null;
        mobSpawner = null;
        entityRenderer = null;
        currentHit = null;
        currentWorldFolder = null;
        gameInitialized = false;
    }

    // ---- Main loop ----

    private void loop() {
        while (!window.shouldClose() &&
               (automationController == null || !automationController.isQuitRequested())) {
            time.update();
            float dt = time.getDeltaTime();

            window.pollEvents();

            if (window.wasResized()) {
                GLInit.setViewport(window.getWidth(), window.getHeight());
            }

            int w = window.getWidth();
            int h = window.getHeight();

            // Clear screen
            glClearColor(0.08f, 0.09f, 0.12f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            switch (screenState) {
                case MAIN_MENU -> updateAndRenderMainMenu(w, h, dt);
                case WORLD_LIST -> updateAndRenderWorldList(w, h, dt);
                case WORLD_CREATION -> updateAndRenderWorldCreation(w, h, dt);
                case SETTINGS_MENU, SETTINGS_INGAME -> updateAndRenderSettings(w, h, dt);
                case IN_GAME -> updateAndRenderGame(w, h, dt);
                case PAUSED -> updateAndRenderPaused(w, h, dt);
            }

            Input.endFrame();
            window.swapBuffers();
        }
    }

    // ---- Menu update/render methods ----

    private void updateAndRenderMainMenu(int w, int h, float dt) {
        // Update hover
        double mx = Input.getMouseX();
        double my = Input.getMouseY();
        mainMenuScreen.updateHover(mx, my, w, h);

        // Handle mouse click
        if (Input.isLeftMouseClicked()) {
            mainMenuScreen.handleClick(mx, my, w, h);
        }

        // Render
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);

        mainMenuScreen.render(w, h, dt);
    }

    private void updateAndRenderWorldList(int w, int h, float dt) {
        double mx = Input.getMouseX();
        double my = Input.getMouseY();
        worldListScreen.updateHover(mx, my, w, h);

        // Handle mouse click
        if (Input.isLeftMouseClicked()) {
            worldListScreen.handleClick(mx, my, w, h);
        }

        // Handle scroll
        double scrollY = Input.getScrollDY();
        if (scrollY != 0) {
            worldListScreen.handleScroll(scrollY);
        }

        if (Input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            switchToMainMenu();
            return;
        }

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);

        worldListScreen.render(w, h, dt);
    }

    private void updateAndRenderWorldCreation(int w, int h, float dt) {
        double mx = Input.getMouseX();
        double my = Input.getMouseY();
        worldCreationScreen.updateHover(mx, my, w, h);

        // Handle mouse click
        if (Input.isLeftMouseClicked()) {
            worldCreationScreen.handleClick(mx, my, w, h);
        }

        // Handle character input for text fields
        if (Input.wasCharTyped()) {
            worldCreationScreen.handleCharTyped(Input.getCharTyped());
        }

        // Forward key presses to world creation screen
        for (int key = 0; key < 350; key++) {
            if (Input.isKeyPressed(key)) {
                worldCreationScreen.handleKeyPress(key);
            }
        }

        // ESC goes back to world list (only if no field focused)
        if (Input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            if (!worldCreationScreen.hasFocusedField()) {
                switchToWorldList();
                return;
            }
        }

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);

        worldCreationScreen.render(w, h, dt);
    }

    private void updateAndRenderSettings(int w, int h, float dt) {
        double mx = Input.getMouseX();
        double my = Input.getMouseY();
        settingsScreen.updateHover(mx, my, w, h);

        // Handle click/drag for sliders
        if (Input.isLeftMouseClicked()) {
            settingsScreen.handleClick(mx, my, w, h);
        }
        if (Input.isLeftMouseDown()) {
            settingsScreen.handleDrag(mx, my, w, h);
        } else {
            settingsScreen.handleRelease();
        }

        if (Input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            closeSettings();
            return;
        }

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);

        settingsScreen.render(w, h, dt);
    }

    private void updateAndRenderPaused(int w, int h, float dt) {
        double mx = Input.getMouseX();
        double my = Input.getMouseY();
        pauseMenuScreen.updateHover(mx, my, w, h);

        // Handle mouse click
        if (Input.isLeftMouseClicked()) {
            pauseMenuScreen.handleClick(mx, my, w, h);
        }

        if (Input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            resumeGame();
            return;
        }

        // Render game world in background (frozen)
        renderGameWorld(w, h, 0); // dt=0 means no time update

        // Render pause menu overlay on top
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);

        pauseMenuScreen.render(w, h, dt);
    }

    // ---- In-game update + render ----

    private void updateAndRenderGame(int w, int h, float dt) {
        if (!gameInitialized) return;

        // Handle ESC → pause
        if (Input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            if (inventoryScreen != null && inventoryScreen.isOpen()) {
                inventoryScreen.close(player.getInventory());
                Input.lockCursor();
            } else {
                pauseGame();
                return;
            }
        }

        // Handle debug toggle (F3)
        if (Input.isKeyPressed(GLFW_KEY_F3)) {
            debugOverlay.toggle();
        }

        // Handle respawn (R key when dead)
        if (player.isDead() && Input.isKeyPressed(GLFW_KEY_R)) {
            player.respawn();
            deathScreen.reset();
            if (!Input.isCursorLocked()) {
                Input.lockCursor();
            }
        }

        // Update timers
        player.updateDamageFlash(dt);
        player.updateAttackCooldown(dt);
        worldTime.update(dt);

        // Track pre-death state
        boolean wasDead = player.isDead();

        // Update game
        controller.update(dt);
        physics.step(player, dt);
        chunkManager.update(player);

        // Detect death transition
        if (player.isDead() && !wasDead) {
            deathScreen.reset();
            if (Input.isCursorLocked()) {
                Input.unlockCursor();
            }
        }

        // Update entities
        itemEntityManager.update(dt, world, player, player.getInventory());
        entityManager.update(dt, world, player, itemEntityManager);
        mobSpawner.update(dt, world, player, entityManager, worldTime);

        // Raycast
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

        // Inventory clicks + mouse tracking
        if (inventoryScreen.isVisible()) {
            inventoryScreen.updateMouse(Input.getMouseX(), Input.getMouseY(), h);
            if (Input.isLeftMouseClicked()) {
                inventoryScreen.handleClick(player.getInventory(),
                    Input.getMouseX(), Input.getMouseY(), w, h,
                    Input.isKeyDown(GLFW_KEY_LEFT_SHIFT));
            }
        }

        // Agent state broadcast
        if (agentServer != null) {
            agentServer.broadcastState(player, world);
        }

        // Auto-save
        autoSaveTimer += dt;
        if (autoSaveTimer >= AUTO_SAVE_INTERVAL) {
            autoSaveTimer = 0;
            performAutoSave();
        }

        // Render
        renderGameWorld(w, h, dt);

        // Screenshot (F2)
        if (Input.isKeyPressed(GLFW_KEY_F2)) {
            Screenshot.capture(w, h);
        }

        // Auto-test mode
        if (autoTestMode) {
            updateAutoTest(w, h, dt);
        }
    }

    /**
     * Render the 3D game world and all in-game overlays.
     */
    private void renderGameWorld(int w, int h, float dt) {
        if (!gameInitialized) return;

        // 3D rendering
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

        renderer.render(player.getCamera(), w, h);

        // Item entities
        itemEntityRenderer.render(player.getCamera(), w, h, itemEntityManager.getItems());

        // Mob entities
        entityRenderer.render(player.getCamera(), w, h, entityManager.getEntities());

        // Block highlight
        if (currentHit != null) {
            blockHighlight.render(player.getCamera(), w, h,
                currentHit.x(), currentHit.y(), currentHit.z());
        }

        // 2D UI overlay
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);

        hud.render(w, h, player);
        debugOverlay.render(player, world, time.getFps(), w, h, controller.isSprinting(),
                itemEntityManager.getItemCount(), entityManager.getEntityCount(),
                worldTime.getTimeString());

        if (inventoryScreen.isVisible()) {
            inventoryScreen.render(w, h, player.getInventory());
        }

        if (player.isDead()) {
            deathScreen.render(w, h, dt);
        }
    }

    // ---- Block interaction (extracted from old loop) ----

    private void handleBlockInteraction(float dt) {
        boolean agentAttack = controller.consumeAgentAttack();
        boolean agentUse = controller.consumeAgentUse();

        boolean isCreative = player.getGameMode() == GameMode.CREATIVE;

        // Entity Attack (Left Click Press)
        boolean leftClickJustPressed = agentAttack || (Input.isCursorLocked() && Input.isLeftMouseClicked());
        boolean entityHit = false;

        if (leftClickJustPressed && player.canAttack() && entityManager != null) {
            Entity hit = entityManager.raycastEntity(
                    player.getCamera().getPosition(), player.getCamera().getFront(), 4.0f
            );
            if (hit != null) {
                float damage = 1.0f;
                org.joml.Vector3f pPos = player.getPosition();
                float dx = hit.getX() - pPos.x;
                float dz = hit.getZ() - pPos.z;
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                if (len > 0.001f) { dx /= len; dz /= len; }
                float knockback = 6.0f;
                hit.damage(damage, dx * knockback, dz * knockback);
                player.resetAttackCooldown();
                entityHit = true;

                if (controller.isBreaking()) {
                    controller.resetBreaking();
                    hud.setBreakProgress(0);
                }

                if (agentAttack && agentActionQueue != null) {
                    agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                            "action_attack", true, hit.getType().name().toLowerCase(),
                            (int) hit.getX(), (int) hit.getY(), (int) hit.getZ()));
                }
            }
        }

        // Block Breaking (Left Click)
        boolean leftClickHeld = !entityHit && (agentAttack || (Input.isCursorLocked() && Input.isLeftMouseDown()));
        boolean leftClickPressed = !entityHit && (agentAttack || (Input.isCursorLocked() && Input.isLeftMouseClicked()));

        if (currentHit != null && (isCreative ? leftClickPressed : leftClickHeld)) {
            int bx = currentHit.x();
            int by = currentHit.y();
            int bz = currentHit.z();
            int blockId = world.getBlock(bx, by, bz);
            Block block = Blocks.get(blockId);

            if (block.isBreakable()) {
                if (isCreative) {
                    breakBlock(bx, by, bz, blockId, false);
                    controller.resetBreaking();
                    hud.setBreakProgress(0);

                    if (agentAttack && agentActionQueue != null) {
                        agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                            "action_attack", true, block.name(), bx, by, bz));
                    }
                } else {
                    float breakTime = block.getBreakTime();

                    // Apply tool effectiveness multiplier
                    Inventory.ItemStack heldTool = player.getInventory().getSlot(player.getSelectedSlot());
                    int toolBlockId = (heldTool != null && !heldTool.isEmpty()) ? heldTool.getBlockId() : 0;
                    float toolMultiplier = ToolItem.getEffectiveness(toolBlockId, blockId);
                    if (toolMultiplier > 1.0f) {
                        breakTime /= toolMultiplier;
                    }

                    float progress = controller.updateBreaking(bx, by, bz, breakTime, dt);
                    hud.setBreakProgress(progress);

                    if (progress >= 1.0f) {
                        // Damage tool on successful break
                        if (heldTool != null && heldTool.hasDurability()) {
                            boolean broke = heldTool.damageTool(1);
                            if (broke) {
                                player.getInventory().setSlot(player.getSelectedSlot(), null);
                                System.out.println("[Tool] Tool broke!");
                            }
                        }

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
            if (controller.isBreaking()) {
                controller.resetBreaking();
                hud.setBreakProgress(0);
            }

            if (agentAttack && agentActionQueue != null) {
                agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                    "action_attack", false, null, 0, 0, 0));
            }
        }

        // Block Placing (Right Click)
        boolean rightClick = agentUse || (Input.isCursorLocked() && Input.isRightMouseClicked());

        if (rightClick && currentHit != null) {
            int px = currentHit.x() + currentHit.nx();
            int py = currentHit.y() + currentHit.ny();
            int pz = currentHit.z() + currentHit.nz();
            int placedBlockId = player.getSelectedBlock();

            if (placedBlockId > 0 && Blocks.get(placedBlockId).solid()) {
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

    private void breakBlock(int bx, int by, int bz, int blockId, boolean spawnDrops) {
        Block block = Blocks.get(blockId);

        world.setBlock(bx, by, bz, 0);
        Set<ChunkPos> affected = Lighting.onBlockRemoved(world, bx, by, bz);
        chunkManager.rebuildMeshAt(bx, by, bz);
        chunkManager.rebuildChunks(affected);

        if (spawnDrops) {
            int dropId = block.getDrop();
            if (dropId > 0) {
                itemEntityManager.spawnDrop(dropId, 1, bx, by, bz);
            }
        }
    }

    // ---- Auto-test ----

    private void updateAutoTest(int w, int h, float dt) {
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

    // ---- Save helpers ----

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
            meta.setDifficulty(player.getDifficulty());
            meta.setSpawnPoint(player.getSpawnX(), player.getSpawnY(), player.getSpawnZ());
            meta.setPlayerHealth(player.getHealth());
            meta.setLastPlayedAt(System.currentTimeMillis());
            saveManager.saveMeta(meta);
        } catch (IOException e) {
            System.err.println("Failed to save player meta: " + e.getMessage());
        }
    }

    // ---- Cleanup ----

    private void cleanup() {
        if (agentServer != null) agentServer.shutdown();
        if (automationController != null) automationController.stop();

        // Save if currently in-game
        if (gameInitialized && saveManager != null) {
            System.out.println("Saving world on exit...");
            try {
                int saved = saveManager.saveAllChunks(world);
                savePlayerMeta();
                System.out.println("Saved " + saved + " chunks on exit");
            } catch (Exception e) {
                System.err.println("Failed to save on exit: " + e.getMessage());
            }
        }

        cleanupGameState();

        // Cleanup menu screens
        if (mainMenuScreen != null) mainMenuScreen.cleanup();
        if (worldListScreen != null) worldListScreen.cleanup();
        if (worldCreationScreen != null) worldCreationScreen.cleanup();
        if (pauseMenuScreen != null) pauseMenuScreen.cleanup();
        if (settingsScreen != null) settingsScreen.cleanup();
        if (bitmapFont != null) bitmapFont.cleanup();

        window.destroy();
    }

    public Window getWindow() { return window; }
    public Time getTime() { return time; }
    public Player getPlayer() { return player; }
}
