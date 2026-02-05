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
import com.voxelgame.render.PostFX;
import com.voxelgame.render.Renderer;
import com.voxelgame.render.SkyRenderer;
import com.voxelgame.save.SaveManager;
import com.voxelgame.save.WorldMeta;
import com.voxelgame.sim.ArmorItem;
import com.voxelgame.sim.ArrowEntity;
import com.voxelgame.sim.BlockBreakProgress;
import com.voxelgame.sim.Boat;
import com.voxelgame.sim.Chest;
import com.voxelgame.sim.ChestManager;
import com.voxelgame.sim.Controller;
import com.voxelgame.sim.Difficulty;
import com.voxelgame.sim.Entity;
import com.voxelgame.sim.EntityManager;
import com.voxelgame.sim.FarmingManager;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.ItemEntity;
import com.voxelgame.sim.ItemEntityManager;
import com.voxelgame.sim.Minecart;
import com.voxelgame.sim.Furnace;
import com.voxelgame.sim.FurnaceManager;
import com.voxelgame.sim.MobSpawner;
import com.voxelgame.sim.Physics;
import com.voxelgame.sim.Player;
import com.voxelgame.sim.RedstoneSystem;
import com.voxelgame.sim.TNTEntity;
import com.voxelgame.sim.ToolItem;
import com.voxelgame.sim.Inventory;
import com.voxelgame.sim.Furnace;
import com.voxelgame.sim.FurnaceManager;
import com.voxelgame.ui.BitmapFont;
import com.voxelgame.ui.DeathScreen;
import com.voxelgame.ui.DebugOverlay;
import com.voxelgame.ui.FurnaceScreen;
import com.voxelgame.ui.Hud;
import com.voxelgame.ui.ChestScreen;
import com.voxelgame.ui.FurnaceScreen;
import com.voxelgame.ui.CreativeInventoryScreen;
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
    private PostFX postFX;
    private SkyRenderer skyRenderer;
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
    private CreativeInventoryScreen creativeInventoryScreen;
    private ChestScreen chestScreen;
    private FurnaceScreen furnaceScreen;

    // Survival mechanics
    private BlockBreakProgress blockBreakProgress;
    private ItemEntityManager itemEntityManager;
    private ItemEntityRenderer itemEntityRenderer;

    // Mob system
    private WorldTime worldTime;
    private EntityManager entityManager;
    private MobSpawner mobSpawner;
    private EntityRenderer entityRenderer;
    private ChestManager chestManager;
    private FurnaceManager furnaceManager;
    private RedstoneSystem redstoneSystem;
    private FarmingManager farmingManager;

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

    // Furnace tick timer (20 ticks/second = 0.05s per tick)
    private float furnaceTickTimer = 0;
    private static final float FURNACE_TICK_INTERVAL = 0.05f;

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

    // Track create-new-world mode (--create flag)
    private boolean createNewWorldMode = false;

    /** Create a brand new world on launch (uses createAndStartWorld path). */
    public void setCreateNewWorld(String worldName) {
        this.createNewWorldMode = true;
        this.currentWorldFolder = worldName != null ? worldName : "test-create";
    }

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
        GLInit.setViewport(window.getFramebufferWidth(), window.getFramebufferHeight());

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
        settingsScreen.setChangeListener(new SettingsScreen.SettingsChangeListener() {
            @Override public void onRenderDistanceChanged(int chunks) {
                if (chunkManager != null) {
                    chunkManager.getLodConfig().setLodThreshold(chunks);
                    if (player != null) {
                        player.getCamera().setFarPlane(chunkManager.getLodConfig().getFarPlane());
                    }
                }
            }
            @Override public void onFovChanged(float fov) {
                if (player != null) player.getCamera().setFov(fov);
            }
            @Override public void onMouseSensitivityChanged(float sensitivity) {
                // Handled by controller reading from settings
            }
            @Override public void onLodThresholdChanged(int chunks) {
                if (chunkManager != null) {
                    chunkManager.getLodConfig().setLodThreshold(chunks);
                    if (player != null) {
                        player.getCamera().setFarPlane(chunkManager.getLodConfig().getFarPlane());
                    }
                }
            }
            @Override public void onMaxLodDistanceChanged(int chunks) {
                if (chunkManager != null) {
                    chunkManager.getLodConfig().setMaxRenderDistance(chunks);
                    if (player != null) {
                        player.getCamera().setFarPlane(chunkManager.getLodConfig().getFarPlane());
                    }
                }
            }
        });

        // Check if direct world mode was requested (automation/legacy)
        if (createNewWorldMode && currentWorldFolder != null) {
            // Create a brand new world (same path as UI "Create World")
            createAndStartWorld(currentWorldFolder, GameMode.SURVIVAL,
                                Difficulty.NORMAL, null);
            System.out.println("VoxelGame initialized — created new world: " + currentWorldFolder);
        } else if (currentWorldFolder != null) {
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
        world = new World();
        controller = new Controller(player, world);
        physics = new Physics();
        physics.setWorld(world);
        renderer = new Renderer(world);
        renderer.init();

        postFX = new PostFX();
        postFX.init(window.getFramebufferWidth(), window.getFramebufferHeight());

        skyRenderer = new SkyRenderer();
        skyRenderer.init();

        chunkManager = new ChunkManager(world);
        chunkManager.setSaveManager(saveManager);

        // Wire LOD config from settings to renderer and camera
        var lodConfig = chunkManager.getLodConfig();
        lodConfig.setLodThreshold(settingsScreen.getLodThreshold());
        lodConfig.setMaxRenderDistance(settingsScreen.getMaxLodDistance());
        renderer.setLodConfig(lodConfig);
        player.getCamera().setFarPlane(lodConfig.getFarPlane());

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
                    // Restore inventory
                    String invData = meta.getInventoryData();
                    if (invData != null && !invData.isEmpty()) {
                        player.getInventory().deserialize(invData);
                        System.out.println("Restored player inventory (" +
                            player.getInventory().getUsedSlotCount() + " used slots)");
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

        // Only populate inventory for creative mode — survival starts empty
        if (player.getGameMode() == GameMode.CREATIVE) {
            player.initCreativeInventory();
        }

        // UI
        hud = new Hud();
        hud.init();
        hud.setFont(bitmapFont);
        hud.setAtlas(renderer.getAtlas());
        debugOverlay = new DebugOverlay(bitmapFont);
        deathScreen = new DeathScreen(bitmapFont);
        deathScreen.init();
        blockHighlight = new BlockHighlight();
        blockHighlight.init();

        inventoryScreen = new InventoryScreen();
        inventoryScreen.init(bitmapFont);
        inventoryScreen.setAtlas(renderer.getAtlas());
        controller.setInventoryScreen(inventoryScreen);

        creativeInventoryScreen = new CreativeInventoryScreen();
        creativeInventoryScreen.init(bitmapFont);
        creativeInventoryScreen.setAtlas(renderer.getAtlas());
        controller.setCreativeInventoryScreen(creativeInventoryScreen);

        chestScreen = new ChestScreen();
        chestScreen.init(bitmapFont);
        chestScreen.setAtlas(renderer.getAtlas());
        chestManager = new ChestManager();

        // Load chest tile entities from disk
        if (!isNew) {
            try {
                chestManager.load(saveManager.getSaveDir());
            } catch (IOException e) {
                System.err.println("Failed to load chests: " + e.getMessage());
            }
        }

        furnaceScreen = new FurnaceScreen();
        furnaceScreen.init(bitmapFont);
        furnaceScreen.setAtlas(renderer.getAtlas());
        furnaceManager = new FurnaceManager();

        // Load furnace tile entities from disk
        if (!isNew) {
            try {
                furnaceManager.load(saveManager.getSaveDir());
            } catch (IOException e) {
                System.err.println("Failed to load furnaces: " + e.getMessage());
            }
        }

        blockBreakProgress = new BlockBreakProgress();
        itemEntityManager = new ItemEntityManager();
        controller.setItemEntityManager(itemEntityManager);
        itemEntityRenderer = new ItemEntityRenderer();
        itemEntityRenderer.init();
        itemEntityRenderer.setAtlas(renderer.getAtlas());

        worldTime = new WorldTime();
        entityManager = new EntityManager();
        mobSpawner = new MobSpawner();
        entityRenderer = new EntityRenderer();
        entityRenderer.init();
        redstoneSystem = new RedstoneSystem();
        farmingManager = new FarmingManager();

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
                if (furnaceManager != null) {
                    furnaceManager.save(saveManager.getSaveDir());
                }
                if (chestManager != null) {
                    chestManager.save(saveManager.getSaveDir());
                }
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
        if (creativeInventoryScreen != null) creativeInventoryScreen.cleanup();
        if (chestScreen != null) chestScreen.cleanup();
        if (furnaceScreen != null) furnaceScreen.cleanup();
        if (entityRenderer != null) entityRenderer.cleanup();
        if (itemEntityRenderer != null) itemEntityRenderer.cleanup();
        if (hud != null) hud.cleanup();
        if (postFX != null) postFX.cleanup();
        if (skyRenderer != null) skyRenderer.cleanup();
        if (renderer != null) renderer.cleanup();

        player = null;
        controller = null;
        physics = null;
        world = null;
        chunkManager = null;
        renderer = null;
        postFX = null;
        skyRenderer = null;
        saveManager = null;
        hud = null;
        debugOverlay = null;
        blockHighlight = null;
        deathScreen = null;
        inventoryScreen = null;
        creativeInventoryScreen = null;
        chestScreen = null;
        chestManager = null;
        furnaceScreen = null;
        furnaceManager = null;
        blockBreakProgress = null;
        itemEntityManager = null;
        itemEntityRenderer = null;
        worldTime = null;
        entityManager = null;
        mobSpawner = null;
        entityRenderer = null;
        if (redstoneSystem != null) redstoneSystem.clear();
        redstoneSystem = null;
        farmingManager = null;
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
                GLInit.setViewport(window.getFramebufferWidth(), window.getFramebufferHeight());
                if (postFX != null) {
                    postFX.resize(window.getFramebufferWidth(), window.getFramebufferHeight());
                }
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
        
        Profiler profiler = Profiler.getInstance();
        profiler.begin("Frame");

        // Handle ESC → close screens or pause
        if (Input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            if (furnaceScreen != null && furnaceScreen.isOpen()) {
                furnaceScreen.close(player.getInventory());
                Input.lockCursor();
            } else if (chestScreen != null && chestScreen.isOpen()) {
                chestScreen.close(player.getInventory());
                Input.lockCursor();
            } else if (creativeInventoryScreen != null && creativeInventoryScreen.isOpen()) {
                creativeInventoryScreen.close(player.getInventory());
                Input.lockCursor();
            } else if (inventoryScreen != null && inventoryScreen.isOpen()) {
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

        // Inform controller about external screen state
        controller.setExternalScreenOpen(
            (chestScreen != null && chestScreen.isOpen()) ||
            (furnaceScreen != null && furnaceScreen.isOpen()) ||
            (creativeInventoryScreen != null && creativeInventoryScreen.isOpen()));

        // Update timers
        player.updateDamageFlash(dt);
        player.updateAttackCooldown(dt);
        worldTime.update(dt);

        // Track pre-death state
        boolean wasDead = player.isDead();

        // Update game
        profiler.begin("Input/Controller");
        controller.update(dt);
        profiler.end("Input/Controller");
        
        profiler.begin("Physics");
        physics.step(player, dt);
        profiler.end("Physics");
        
        profiler.begin("ChunkManager");
        chunkManager.update(player);
        profiler.end("ChunkManager");

        // Detect death transition
        if (player.isDead() && !wasDead) {
            deathScreen.reset();
            if (Input.isCursorLocked()) {
                Input.unlockCursor();
            }
        }

        // Update entities
        profiler.begin("Entities");
        itemEntityManager.update(dt, world, player, player.getInventory());
        entityManager.update(dt, world, player, itemEntityManager);
        mobSpawner.update(dt, world, player, entityManager, worldTime);
        profiler.end("Entities");

        // Tick farming (random tick crop growth)
        profiler.begin("Farming/Tile");
        if (farmingManager != null && world != null) {
            Set<ChunkPos> farmDirty = farmingManager.update(dt, world,
                player.getPosition().x, player.getPosition().z);
            if (!farmDirty.isEmpty()) {
                chunkManager.rebuildChunks(farmDirty);
            }
        }

        // Tick furnaces
        if (furnaceManager != null) {
            furnaceTickTimer += dt;
            while (furnaceTickTimer >= FURNACE_TICK_INTERVAL) {
                furnaceTickTimer -= FURNACE_TICK_INTERVAL;
                furnaceManager.tickAll();
            }
        }
        profiler.end("Farming/Tile");

        // Process TNT chain reactions
        for (TNTEntity chain : TNTEntity.drainPendingChainTNT()) {
            chain.setExplosionCallback(makeTNTCallback());
            entityManager.addEntity(chain);
        }

        // Raycast
        profiler.begin("Raycast");
        boolean anyScreenOpen = controller.isInventoryOpen()
            || (chestScreen != null && chestScreen.isOpen())
            || (furnaceScreen != null && furnaceScreen.isOpen())
            || (creativeInventoryScreen != null && creativeInventoryScreen.isOpen());
        if (!player.isDead() && !anyScreenOpen) {
            currentHit = Raycast.cast(
                world, player.getCamera().getPosition(), player.getCamera().getFront(), 8.0f
            );
            profiler.end("Raycast");
            profiler.begin("BlockInteract");
            handleBlockInteraction(dt);
            profiler.end("BlockInteract");
        } else {
            profiler.end("Raycast");
            currentHit = null;
            controller.resetBreaking();
            hud.setBreakProgress(0);
        }

        // Inventory clicks + mouse tracking + right-click + drag
        if (inventoryScreen.isVisible()) {
            inventoryScreen.updateMouse(Input.getMouseX(), Input.getMouseY(), h);
            if (Input.isLeftMouseClicked()) {
                inventoryScreen.handleClick(player.getInventory(),
                    Input.getMouseX(), Input.getMouseY(), w, h,
                    Input.isKeyDown(GLFW_KEY_LEFT_SHIFT));
            }
            if (Input.isRightMouseClicked()) {
                inventoryScreen.handleRightClick(player.getInventory(),
                    Input.getMouseX(), Input.getMouseY(), w, h);
            }
            // Update drag state each frame
            inventoryScreen.updateDrag(player.getInventory(),
                Input.isLeftMouseDown(), Input.isRightMouseDown(),
                Input.getMouseX(), Input.getMouseY(), w, h);
            // Q key: drop hovered item from inventory screen
            if (Input.isKeyPressed(GLFW_KEY_Q)) {
                int hovered = inventoryScreen.getHoveredSlot();
                if (hovered >= 0 && hovered < Inventory.TOTAL_SIZE) {
                    Inventory.ItemStack stack = player.getInventory().getSlot(hovered);
                    if (stack != null && !stack.isEmpty()) {
                        boolean ctrlHeld = Input.isKeyDown(GLFW_KEY_LEFT_CONTROL)
                                        || Input.isKeyDown(GLFW_KEY_RIGHT_CONTROL);
                        int dropCount = ctrlHeld ? stack.getCount() : 1;
                        int durability = stack.hasDurability() ? stack.getDurability() : -1;
                        int maxDur = stack.hasDurability() ? stack.getMaxDurability() : -1;
                        itemEntityManager.dropFromPlayer(player, stack.getBlockId(), dropCount, durability, maxDur);
                        stack.remove(dropCount);
                        if (stack.isEmpty()) player.getInventory().setSlot(hovered, null);
                    }
                }
            }
        }

        // Creative inventory clicks + mouse tracking + scroll
        if (creativeInventoryScreen != null && creativeInventoryScreen.isVisible()) {
            creativeInventoryScreen.updateMouse(Input.getMouseX(), Input.getMouseY(), h);
            if (Input.isLeftMouseClicked()) {
                creativeInventoryScreen.handleClick(player.getInventory(),
                    Input.getMouseX(), Input.getMouseY(), w, h,
                    Input.isKeyDown(GLFW_KEY_LEFT_SHIFT));
            }
            if (Input.isRightMouseClicked()) {
                creativeInventoryScreen.handleRightClick(player.getInventory(),
                    Input.getMouseX(), Input.getMouseY(), w, h);
            }
            // Scroll for creative grid
            double scrollY = Input.getScrollDY();
            if (scrollY != 0) {
                creativeInventoryScreen.handleScroll(scrollY);
            }
        }

        // Furnace screen clicks + mouse tracking
        if (furnaceScreen != null && furnaceScreen.isVisible()) {
            furnaceScreen.updateMouse(Input.getMouseX(), Input.getMouseY(), h);
            if (Input.isLeftMouseClicked()) {
                furnaceScreen.handleClick(player.getInventory(),
                    Input.getMouseX(), Input.getMouseY(), w, h);
            }
        }

        // Chest screen clicks + mouse tracking
        if (chestScreen != null && chestScreen.isVisible()) {
            chestScreen.updateMouse(Input.getMouseX(), Input.getMouseY(), h);
            if (Input.isLeftMouseClicked()) {
                chestScreen.handleClick(player.getInventory(),
                    Input.getMouseX(), Input.getMouseY(), w, h);
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
        profiler.begin("Render");
        renderGameWorld(w, h, dt);
        profiler.end("Render");

        // Screenshot (F2) — use framebuffer dimensions for glReadPixels
        if (Input.isKeyPressed(GLFW_KEY_F2)) {
            Screenshot.capture(window.getFramebufferWidth(), window.getFramebufferHeight());
        }

        // Auto-test mode
        if (autoTestMode) {
            updateAutoTest(w, h, dt);
        }
        
        profiler.end("Frame");
        profiler.endFrame();
    }

    /**
     * Render the 3D game world and all in-game overlays.
     */
    private void renderGameWorld(int w, int h, float dt) {
        if (!gameInitialized) return;
        
        Profiler profiler = Profiler.getInstance();

        // Update lighting state from world time
        if (worldTime != null) {
            float[] skyColor = worldTime.getSkyColor();
            glClearColor(skyColor[0], skyColor[1], skyColor[2], 1.0f);
            renderer.updateLighting(worldTime);
        }

        // Use framebuffer dimensions for rendering (HiDPI aware)
        int fbW = window.getFramebufferWidth();
        int fbH = window.getFramebufferHeight();

        // Begin post-processing capture (render to FBO)
        boolean usePostFX = postFX != null && postFX.isInitialized();
        if (usePostFX) {
            postFX.beginSceneCapture();
            // Set sky clear color in the FBO too
            if (worldTime != null) {
                float[] skyColor = worldTime.getSkyColor();
                glClearColor(skyColor[0], skyColor[1], skyColor[2], 1.0f);
            }
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        } else {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }

        // 3D rendering
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

        // Render sky gradient first (at far depth)
        profiler.begin("Sky");
        if (skyRenderer != null && skyRenderer.isInitialized()) {
            skyRenderer.updateColors(worldTime);
            skyRenderer.render();
        }
        profiler.end("Sky");

        profiler.begin("World");
        renderer.render(player.getCamera(), fbW, fbH);
        profiler.end("World");

        // Item entities
        profiler.begin("ItemEntities");
        itemEntityRenderer.render(player.getCamera(), w, h, itemEntityManager.getItems());
        profiler.end("ItemEntities");

        // Mob entities
        profiler.begin("MobEntities");
        entityRenderer.render(player.getCamera(), w, h, entityManager.getEntities());
        profiler.end("MobEntities");

        // Block highlight
        if (currentHit != null) {
            blockHighlight.render(player.getCamera(), w, h,
                currentHit.x(), currentHit.y(), currentHit.z());
        }

        // End PostFX capture and apply effects (SSAO + tone mapping)
        profiler.begin("PostFX");
        if (usePostFX) {
            org.joml.Matrix4f projection = player.getCamera().getProjectionMatrix(fbW, fbH);
            org.joml.Matrix4f view = player.getCamera().getViewMatrix();
            postFX.endSceneAndApplyEffects(projection, view);
            GLInit.setViewport(fbW, fbH); // restore viewport after PostFX
        }
        profiler.end("PostFX");

        // 2D UI overlay
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);

        profiler.begin("UI");
        hud.render(w, h, player);
        debugOverlay.render(player, world, time.getFps(), w, h, controller.isSprinting(),
                itemEntityManager.getItemCount(), entityManager.getEntityCount(),
                worldTime.getTimeString(), chunkManager,
                renderer.getRenderedChunks(), renderer.getCulledChunks());
        profiler.end("UI");

        if (inventoryScreen.isVisible()) {
            inventoryScreen.render(w, h, player.getInventory());
        }

        if (creativeInventoryScreen != null && creativeInventoryScreen.isVisible()) {
            creativeInventoryScreen.render(w, h, player.getInventory());
        }

        if (chestScreen != null && chestScreen.isVisible()) {
            chestScreen.render(w, h, player.getInventory());
        }

        if (furnaceScreen != null && furnaceScreen.isVisible()) {
            furnaceScreen.render(w, h, player.getInventory());
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

        // ---- Bow & Arrow Combat ----
        if (player.isBowDrawn() && Input.isCursorLocked()) {
            if (Input.isRightMouseDown() && player.hasArrows()) {
                // Start/continue charging bow
                if (!player.isBowCharging()) {
                    player.startChargingBow();
                }
                player.updateBowCharge(dt);
            } else if (player.isBowCharging()) {
                // Released right-click → fire arrow
                ArrowEntity arrow = player.releaseBow();
                if (arrow != null) {
                    entityManager.addEntity(arrow);
                }
            }
        } else if (player.isBowCharging()) {
            // Switched items or lost cursor lock → cancel charge
            player.cancelBowCharge();
        }

        // Block Placing / Interaction (Right Click)
        // Don't place blocks / interact while charging bow
        boolean rightClick = agentUse || (Input.isCursorLocked() && Input.isRightMouseClicked());

        if (rightClick && !player.isBowCharging()) {
            // First: check if we right-clicked an entity (boat/minecart)
            Entity hitEntity = entityManager.raycastEntity(
                player.getCamera().getPosition(), player.getCamera().getFront(), 4.0f);

            if (hitEntity instanceof Boat boat && !player.isMounted()) {
                boat.mount();
                player.mount(boat);
            } else if (hitEntity instanceof Minecart cart && !player.isMounted()) {
                cart.mount();
                player.mount(cart);
            } else if (currentHit != null) {
                int hitBlockId = world.getBlock(currentHit.x(), currentHit.y(), currentHit.z());

                // Check if we right-clicked a chest → open UI
                if (hitBlockId == Blocks.CHEST.id()) {
                    Chest chest = chestManager.getChestAt(currentHit.x(), currentHit.y(), currentHit.z());
                    if (chest == null) {
                        chest = chestManager.createChest(currentHit.x(), currentHit.y(), currentHit.z());
                    }
                    chestScreen.open(chest);
                    Input.unlockCursor();
                }
                // Check if we right-clicked a furnace → open UI
                else if (hitBlockId == Blocks.FURNACE.id()) {
                    Furnace furnace = furnaceManager.getFurnaceAt(currentHit.x(), currentHit.y(), currentHit.z());
                    if (furnace == null) {
                        furnace = furnaceManager.createFurnace(currentHit.x(), currentHit.y(), currentHit.z());
                    }
                    furnaceScreen.open(furnace);
                    Input.unlockCursor();
                }
                // ---- Farming: Hoe on dirt/grass → farmland ----
                else if (Blocks.isHoe(player.getSelectedBlock()) && Blocks.isTillable(hitBlockId)) {
                    int bx = currentHit.x(), by = currentHit.y(), bz = currentHit.z();
                    world.setBlock(bx, by, bz, Blocks.FARMLAND.id());
                    Set<ChunkPos> affected = Lighting.onBlockPlaced(world, bx, by, bz);
                    chunkManager.rebuildMeshAt(bx, by, bz);
                    chunkManager.rebuildChunks(affected);

                    // Damage hoe tool in survival
                    if (!isCreative) {
                        Inventory.ItemStack heldTool = player.getInventory().getSlot(player.getSelectedSlot());
                        if (heldTool != null && heldTool.hasDurability()) {
                            boolean broke = heldTool.damageTool(1);
                            if (broke) {
                                player.getInventory().setSlot(player.getSelectedSlot(), null);
                                System.out.println("[Farming] Hoe broke!");
                            }
                        }
                    }
                    System.out.println("[Farming] Tilled dirt at (" + bx + ", " + by + ", " + bz + ")");
                }
                // ---- Farming: Seeds on farmland → plant wheat ----
                else if (player.getSelectedBlock() == Blocks.WHEAT_SEEDS.id() && Blocks.isFarmland(hitBlockId)) {
                    int px = currentHit.x();
                    int py = currentHit.y() + 1; // plant on top of farmland
                    int pz = currentHit.z();
                    int aboveBlock = world.getBlock(px, py, pz);
                    if (aboveBlock == Blocks.AIR.id()) {
                        world.setBlock(px, py, pz, Blocks.WHEAT_CROP_0.id());
                        chunkManager.rebuildMeshAt(px, py, pz);

                        if (!isCreative) {
                            player.consumeSelectedBlock();
                        }
                        System.out.println("[Farming] Planted wheat at (" + px + ", " + py + ", " + pz + ")");
                    }
                }
                // ---- Flint and Steel: Light fire on block face ----
                else if (Blocks.isFlintAndSteel(player.getSelectedBlock())) {
                    // Calculate position adjacent to clicked face (where fire will be placed)
                    int fx = currentHit.x() + currentHit.nx();
                    int fy = currentHit.y() + currentHit.ny();
                    int fz = currentHit.z() + currentHit.nz();
                    int targetBlock = world.getBlock(fx, fy, fz);
                    
                    // Can only place fire in air
                    if (targetBlock == Blocks.AIR.id()) {
                        // Place fire block
                        world.setBlock(fx, fy, fz, Blocks.FIRE.id());
                        Set<ChunkPos> affected = Lighting.onBlockPlaced(world, fx, fy, fz);
                        chunkManager.rebuildMeshAt(fx, fy, fz);
                        chunkManager.rebuildChunks(affected);
                        
                        // Propagate fire light (level 15)
                        propagateBlockLight(fx, fy, fz, 15);
                        
                        // Damage flint and steel in survival mode
                        if (!isCreative) {
                            Inventory.ItemStack heldTool = player.getInventory().getSlot(player.getSelectedSlot());
                            if (heldTool != null && heldTool.hasDurability()) {
                                boolean broke = heldTool.damageTool(1);
                                if (broke) {
                                    player.getInventory().setSlot(player.getSelectedSlot(), null);
                                    System.out.println("[Fire] Flint and steel broke!");
                                }
                            }
                        }
                        
                        System.out.println("[Fire] Lit fire at (" + fx + ", " + fy + ", " + fz + ")");
                    }
                }
                // Try to eat food item (cooked/raw porkchop, beef, or chicken)
                else if (player.getSelectedBlock() == Blocks.COOKED_PORKCHOP.id()
                         || player.getSelectedBlock() == Blocks.RAW_PORKCHOP.id()
                         || player.getSelectedBlock() == Blocks.COOKED_BEEF.id()
                         || player.getSelectedBlock() == Blocks.RAW_BEEF.id()
                         || player.getSelectedBlock() == Blocks.COOKED_CHICKEN.id()
                         || player.getSelectedBlock() == Blocks.RAW_CHICKEN.id()) {
                    player.tryEatHeldItem();
                }
                // Try to equip armor
                else if (ArmorItem.isArmor(player.getSelectedBlock())) {
                    ArmorItem.Slot armorSlot = ArmorItem.getSlot(player.getSelectedBlock());
                    if (armorSlot != null) {
                        Inventory.ItemStack currentArmor = player.getInventory().getArmor(armorSlot.index);
                        
                        // If there's already armor in that slot, swap it
                        if (currentArmor != null && !currentArmor.isEmpty()) {
                            int oldArmorId = currentArmor.getBlockId();
                            player.getInventory().setArmor(armorSlot.index, null);
                            player.setSelectedBlock(oldArmorId);
                        } else if (!isCreative) {
                            // Consume from inventory in survival
                            player.consumeSelectedBlock();
                        }
                        
                        // Equip new armor (with full durability)
                        int maxDur = ArmorItem.getMaxDurability(player.getSelectedBlock());
                        Inventory.ItemStack armorStack = new Inventory.ItemStack(player.getSelectedBlock(), maxDur, maxDur);
                        player.getInventory().setArmor(armorSlot.index, armorStack);
                        
                        System.out.printf("[Armor] Equipped %s (slot %s, %d durability)%n",
                            ArmorItem.getDisplayName(player.getSelectedBlock()), armorSlot, maxDur);
                    }
                } else {
                    // Normal block placement
                    int px = currentHit.x() + currentHit.nx();
                    int py = currentHit.y() + currentHit.ny();
                    int pz = currentHit.z() + currentHit.nz();
                    int placedBlockId = player.getSelectedBlock();

                    // Redstone item places as redstone wire
                    if (placedBlockId == Blocks.REDSTONE.id()) {
                        placedBlockId = Blocks.REDSTONE_WIRE.id();
                    }

                    if (placedBlockId > 0 && (Blocks.get(placedBlockId).solid() || Blocks.isNonSolidPlaceable(placedBlockId))) {
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

                            // If we placed a chest or furnace, create its tile entity
                            if (placedBlockId == Blocks.CHEST.id()) {
                                chestManager.createChest(px, py, pz);
                            } else if (placedBlockId == Blocks.FURNACE.id()) {
                                furnaceManager.createFurnace(px, py, pz);
                            }

                            // If we placed a torch or redstone torch, propagate block light
                            if (placedBlockId == Blocks.TORCH.id() || placedBlockId == Blocks.REDSTONE_TORCH.id()) {
                                propagateBlockLight(px, py, pz, Blocks.getLightEmission(placedBlockId));
                            }

                            // Redstone components: propagate redstone power
                            if (Blocks.isRedstoneComponent(placedBlockId) && redstoneSystem != null) {
                                Set<ChunkPos> rsAffected = redstoneSystem.onRedstonePlaced(world, px, py, pz);
                                chunkManager.rebuildChunks(rsAffected);
                            }

                            // Any block placed might affect adjacent redstone
                            if (redstoneSystem != null && !Blocks.isRedstoneComponent(placedBlockId)) {
                                Set<ChunkPos> rsAffected = redstoneSystem.onBlockChanged(world, px, py, pz);
                                chunkManager.rebuildChunks(rsAffected);
                            }

                            if (agentUse && agentActionQueue != null) {
                                agentActionQueue.setLastResult(new ActionQueue.ActionResult(
                                    "action_use", true, Blocks.get(placedBlockId).name(), px, py, pz));
                            }
                        }
                    } else if (placedBlockId == Blocks.BOAT_ITEM.id()) {
                        // Place boat entity on water
                        int bpx = currentHit.x() + currentHit.nx();
                        int bpy = currentHit.y() + currentHit.ny();
                        int bpz = currentHit.z() + currentHit.nz();
                        Boat boat = new Boat(bpx + 0.5f, bpy, bpz + 0.5f);
                        entityManager.addEntity(boat);
                        if (!isCreative) player.consumeSelectedBlock();
                        System.out.println("[Boat] Placed boat at " + bpx + ", " + bpy + ", " + bpz);
                    } else if (placedBlockId == Blocks.MINECART_ITEM.id()) {
                        // Place minecart entity on rail
                        int mpx = currentHit.x() + currentHit.nx();
                        int mpy = currentHit.y() + currentHit.ny();
                        int mpz = currentHit.z() + currentHit.nz();
                        Minecart cart = new Minecart(mpx + 0.5f, mpy, mpz + 0.5f);
                        cart.setRedstoneSystem(redstoneSystem);
                        entityManager.addEntity(cart);
                        if (!isCreative) player.consumeSelectedBlock();
                        System.out.println("[Minecart] Placed minecart at " + mpx + ", " + mpy + ", " + mpz);
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

        // TNT: ignite instead of break
        if (blockId == Blocks.TNT.id()) {
            world.setBlock(bx, by, bz, 0);
            Set<ChunkPos> affected = Lighting.onBlockRemoved(world, bx, by, bz);
            chunkManager.rebuildMeshAt(bx, by, bz);
            chunkManager.rebuildChunks(affected);

            TNTEntity tnt = new TNTEntity(bx + 0.5f, by, bz + 0.5f);
            tnt.setWorldRef(world);
            tnt.setExplosionCallback(makeTNTCallback());
            entityManager.addEntity(tnt);
            System.out.println("[TNT] Ignited at (" + bx + ", " + by + ", " + bz + ")");
            return;
        }

        // Furnace: drop all contents
        if (blockId == Blocks.FURNACE.id() && spawnDrops) {
            Furnace furnace = furnaceManager.removeFurnace(bx, by, bz);
            if (furnace != null) {
                Inventory.ItemStack[] drops = furnace.dropAll();
                for (Inventory.ItemStack stack : drops) {
                    if (stack != null && !stack.isEmpty()) {
                        itemEntityManager.spawnDrop(stack.getBlockId(), stack.getCount(), bx, by, bz);
                    }
                }
            }
        }

        // Chest: drop all contents
        if (blockId == Blocks.CHEST.id() && spawnDrops) {
            Chest chest = chestManager.removeChest(bx, by, bz);
            if (chest != null) {
                Inventory.ItemStack[] drops = chest.dropAll();
                for (Inventory.ItemStack stack : drops) {
                    if (stack != null && !stack.isEmpty()) {
                        itemEntityManager.spawnDrop(stack.getBlockId(), stack.getCount(), bx, by, bz);
                    }
                }
            }
        }

        // If farmland is broken, also break any wheat crop on top
        if (Blocks.isFarmland(blockId)) {
            int aboveId = world.getBlock(bx, by + 1, bz);
            if (Blocks.isWheatCrop(aboveId)) {
                breakBlock(bx, by + 1, bz, aboveId, spawnDrops);
            }
        }

        // Handle redstone component removal (before block is removed)
        boolean wasRedstone = Blocks.isRedstoneComponent(blockId);
        int oldLightEmission = Blocks.getLightEmission(blockId);

        world.setBlock(bx, by, bz, 0);
        Set<ChunkPos> affected = Lighting.onBlockRemoved(world, bx, by, bz);
        chunkManager.rebuildMeshAt(bx, by, bz);
        chunkManager.rebuildChunks(affected);

        // Remove redstone power propagation
        if (wasRedstone && redstoneSystem != null) {
            Set<ChunkPos> rsAffected = redstoneSystem.onRedstoneRemoved(world, bx, by, bz, blockId);
            chunkManager.rebuildChunks(rsAffected);
        }

        // Any block removed might affect adjacent redstone
        if (!wasRedstone && redstoneSystem != null) {
            Set<ChunkPos> rsAffected = redstoneSystem.onBlockChanged(world, bx, by, bz);
            chunkManager.rebuildChunks(rsAffected);
        }

        // Remove block light if this was a light source (redstone torch, etc.)
        if (oldLightEmission > 0) {
            Set<ChunkPos> lightAffected = Lighting.onLightSourceRemoved(world, bx, by, bz, oldLightEmission);
            chunkManager.rebuildChunks(lightAffected);
        }

        if (spawnDrops) {
            // Special case: wheat crop drops
            if (Blocks.isWheatCrop(blockId)) {
                int stage = Blocks.getWheatStage(blockId);
                if (stage >= 7) {
                    // Mature wheat: drop 1 wheat item + 0-3 seeds
                    itemEntityManager.spawnDrop(Blocks.WHEAT_ITEM.id(), 1, bx, by, bz);
                    int seedCount = 1 + (int)(Math.random() * 3); // 1-3 seeds
                    itemEntityManager.spawnDrop(Blocks.WHEAT_SEEDS.id(), seedCount, bx, by, bz);
                } else {
                    // Immature wheat: drop only 1 seed
                    itemEntityManager.spawnDrop(Blocks.WHEAT_SEEDS.id(), 1, bx, by, bz);
                }
            } else {
                int dropId = block.getDrop();

                // Special case: gravel has 10% chance to drop flint instead of itself
                if (blockId == Blocks.GRAVEL.id() && Math.random() < 0.10) {
                    dropId = Blocks.FLINT.id();
                }

                if (dropId > 0) {
                    itemEntityManager.spawnDrop(dropId, 1, bx, by, bz);
                }
            }
        }
    }

    /**
     * Simple block light propagation using BFS.
     * Called when a light-emitting block (torch) is placed.
     */
    private void propagateBlockLight(int wx, int wy, int wz, int lightLevel) {
        if (lightLevel <= 0) return;

        java.util.Queue<int[]> queue = new java.util.ArrayDeque<>();
        world.setBlockLight(wx, wy, wz, lightLevel);
        queue.add(new int[]{wx, wy, wz, lightLevel});

        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        Set<ChunkPos> affectedChunks = new java.util.HashSet<>();

        while (!queue.isEmpty()) {
            int[] entry = queue.poll();
            int ex = entry[0], ey = entry[1], ez = entry[2], level = entry[3];

            for (int[] d : dirs) {
                int nx = ex + d[0], ny = ey + d[1], nz = ez + d[2];
                if (ny < 0 || ny >= com.voxelgame.world.WorldConstants.WORLD_HEIGHT) continue;

                Block nBlock = Blocks.get(world.getBlock(nx, ny, nz));
                if (nBlock.solid() && !nBlock.transparent()) continue;

                int newLevel = level - 1;
                if (nBlock.id() == Blocks.WATER.id()) newLevel -= 2;
                if (newLevel <= 0) continue;

                int current = world.getBlockLight(nx, ny, nz);
                if (newLevel > current) {
                    world.setBlockLight(nx, ny, nz, newLevel);
                    queue.add(new int[]{nx, ny, nz, newLevel});
                    int cx = Math.floorDiv(nx, com.voxelgame.world.WorldConstants.CHUNK_SIZE);
                    int cz = Math.floorDiv(nz, com.voxelgame.world.WorldConstants.CHUNK_SIZE);
                    affectedChunks.add(new ChunkPos(cx, cz));
                }
            }
        }

        // Rebuild affected chunk meshes so the light shows
        chunkManager.rebuildChunks(affectedChunks);
    }

    /**
     * Create TNT explosion callback for chunk rebuilding.
     */
    private TNTEntity.ExplosionCallback makeTNTCallback() {
        return new TNTEntity.ExplosionCallback() {
            @Override
            public void onBlockDestroyed(int x, int y, int z) {
                chunkManager.rebuildMeshAt(x, y, z);

                // If a chest was destroyed, remove it and drop items
                if (chestManager != null) {
                    Chest chest = chestManager.removeChest(x, y, z);
                    if (chest != null) {
                        Inventory.ItemStack[] drops = chest.dropAll();
                        for (Inventory.ItemStack stack : drops) {
                            if (stack != null && !stack.isEmpty()) {
                                itemEntityManager.spawnDrop(stack.getBlockId(), stack.getCount(), x, y, z);
                            }
                        }
                    }
                }

                // If a furnace was destroyed, remove it and drop items
                if (furnaceManager != null) {
                    Furnace furnace = furnaceManager.removeFurnace(x, y, z);
                    if (furnace != null) {
                        Inventory.ItemStack[] drops = furnace.dropAll();
                        for (Inventory.ItemStack stack : drops) {
                            if (stack != null && !stack.isEmpty()) {
                                itemEntityManager.spawnDrop(stack.getBlockId(), stack.getCount(), x, y, z);
                            }
                        }
                    }
                }
            }

            @Override
            public void rebuildChunks(Set<ChunkPos> chunks) {
                chunkManager.rebuildChunks(chunks);
            }
        };
    }

    // ---- Auto-test ----

    private void updateAutoTest(int w, int h, float dt) {
        int fbW = window.getFramebufferWidth();
        int fbH = window.getFramebufferHeight();
        autoTestTimer += dt;
        switch (autoTestPhase) {
            case 0:
                if (autoTestTimer > 3.0f) { autoTestPhase = 1; autoTestTimer = 0; }
                break;
            case 1:
                Screenshot.capture(fbW, fbH);
                autoTestPhase = 2; autoTestTimer = 0;
                break;
            case 2:
                if (autoTestTimer > 1.0f) {
                    autoTestPhase = player.isDead() ? 3 : 5;
                    autoTestTimer = 0;
                }
                break;
            case 3:
                Screenshot.capture(fbW, fbH);
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
                    Screenshot.capture(fbW, fbH);
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
            if (furnaceManager != null) {
                furnaceManager.save(saveManager.getSaveDir());
            }
            if (chestManager != null) {
                chestManager.save(saveManager.getSaveDir());
            }
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
            meta.setInventoryData(player.getInventory().serialize());
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
                if (furnaceManager != null) {
                    furnaceManager.save(saveManager.getSaveDir());
                }
                if (chestManager != null) {
                    chestManager.save(saveManager.getSaveDir());
                }
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
