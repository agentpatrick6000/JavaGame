package com.voxelgame.ui;

import com.voxelgame.sim.Difficulty;
import com.voxelgame.sim.GameMode;
import com.voxelgame.world.gen.GenConfig;
import com.voxelgame.world.gen.WorldGenPreset;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/**
 * World creation screen with preset selector and advanced world generation settings.
 *
 * Layout:
 * - World name field
 * - Game mode toggle
 * - Difficulty toggle
 * - Seed field
 * - World Type (preset) selector: Default / Amplified / Superflat / More Oceans / Floating Islands
 * - "More World Options..." button → expands to show:
 *   - Show Coordinates toggle
 *   - Bonus Chest toggle
 *   - Advanced World Gen Settings button → opens sliders for:
 *     - Sea Level (0-120)
 *     - Terrain Height Scale (0.5-3.0)
 *     - Cave Frequency (0-200%)
 *     - Ore Abundance (0-300%)
 *     - Tree Density (0-300%)
 * - Create World / Cancel buttons
 */
public class WorldCreationScreen extends Screen {

    /** Callback when world creation is confirmed or cancelled. */
    public interface CreationCallback {
        void onCreateWorld(String worldName, GameMode gameMode, Difficulty difficulty,
                           String seed, boolean showCoordinates, boolean bonusChest,
                           WorldGenPreset preset, GenConfig genConfig);
        void onCancel();
    }

    private CreationCallback callback;

    // Form state
    private String worldName = "New World";
    private int gameModeIndex = 1;
    private int difficultyIndex = 2;
    private String seedText = "";
    private boolean showCoordinates = true;
    private boolean bonusChest = false;
    private boolean showAdvanced = false;
    private boolean showGenSettings = false;

    // World type (preset)
    private int presetIndex = 0;
    private static final WorldGenPreset[] PRESETS = WorldGenPreset.values();

    // Advanced gen config (starts from preset defaults)
    private GenConfig advancedConfig;

    // Input focus: 0=world name, 1=seed
    private int focusedField = -1;

    // Cursor blink
    private float cursorTimer = 0;
    private boolean cursorVisible = true;

    // Hover tracking
    private int hoveredButton = -1;

    // Slider being dragged (-1 = none)
    private int draggingSlider = -1;

    private static final GameMode[] GAME_MODES = { GameMode.CREATIVE, GameMode.SURVIVAL };
    private static final Difficulty[] DIFFICULTIES = { Difficulty.PEACEFUL, Difficulty.EASY,
                                                        Difficulty.NORMAL, Difficulty.HARD };
    private static final String[] MODE_NAMES = { "Creative", "Survival" };
    private static final String[] DIFF_NAMES = { "Peaceful", "Easy", "Normal", "Hard" };

    // Slider definitions
    private static final String[] SLIDER_LABELS = {
        "Sea Level", "Terrain Scale", "Cave Frequency", "Ore Abundance", "Tree Density"
    };
    private static final float[] SLIDER_MIN = { 0, 50, 0, 0, 0 };
    private static final float[] SLIDER_MAX = { 120, 300, 200, 300, 300 };
    private static final String[] SLIDER_SUFFIX = { "", "%", "%", "%", "%" };

    public void setCallback(CreationCallback callback) {
        this.callback = callback;
    }

    /** Reset form to defaults. */
    public void reset() {
        worldName = "New World";
        gameModeIndex = 1;
        difficultyIndex = 2;
        seedText = "";
        showCoordinates = true;
        bonusChest = false;
        showAdvanced = false;
        showGenSettings = false;
        presetIndex = 0;
        advancedConfig = PRESETS[0].createConfig();
        focusedField = -1;
        draggingSlider = -1;
    }

    private void syncConfigFromPreset() {
        advancedConfig = PRESETS[presetIndex].createConfig();
    }

    private float getSliderValue(int index) {
        if (advancedConfig == null) advancedConfig = GenConfig.defaultConfig();
        return switch (index) {
            case 0 -> advancedConfig.seaLevel;
            case 1 -> (float)(advancedConfig.terrainHeightScale * 100.0);
            case 2 -> (float)(advancedConfig.caveFreq / 0.045 * 100.0);
            case 3 -> (float)(advancedConfig.oreAbundanceMultiplier * 100.0);
            case 4 -> (float)(advancedConfig.treeDensityMultiplier * 100.0);
            default -> 100;
        };
    }

    private void setSliderValue(int index, float value) {
        if (advancedConfig == null) advancedConfig = GenConfig.defaultConfig();
        switch (index) {
            case 0 -> advancedConfig.seaLevel = Math.round(value);
            case 1 -> advancedConfig.terrainHeightScale = value / 100.0;
            case 2 -> advancedConfig.caveFreq = 0.045 * (value / 100.0);
            case 3 -> advancedConfig.oreAbundanceMultiplier = value / 100.0;
            case 4 -> advancedConfig.treeDensityMultiplier = value / 100.0;
        }
    }

    @Override
    public void render(int screenW, int screenH, float dt) {
        this.screenW = screenW;
        this.screenH = screenH;

        cursorTimer += dt;
        if (cursorTimer > 0.5f) {
            cursorTimer = 0;
            cursorVisible = !cursorVisible;
        }

        beginDraw(screenW, screenH);
        fillRect(0, 0, screenW, screenH, 0.08f, 0.09f, 0.12f, 1.0f);
        endDraw();

        // Title
        drawCenteredTextWithShadow("Create New World", screenH * 0.03f, 3.0f,
                                    0.8f, 0.9f, 1.0f, 1.0f);

        float formX = (screenW - 400) / 2.0f;
        float formY = screenH * 0.10f;
        float rowH = 38.0f;
        float fieldW = 400.0f;
        float labelScale = 1.8f;

        // ---- World Name ----
        renderLabel("World Name:", formX, formY, labelScale);
        formY += 17;
        renderTextField(worldName, formX, formY, fieldW, 28, focusedField == 0);
        formY += 36;

        // ---- Game Mode ----
        renderLabel("Game Mode:", formX, formY, labelScale);
        formY += 17;
        beginDraw(screenW, screenH);
        float modeY = screenH - formY - 28;
        drawButton("< " + MODE_NAMES[gameModeIndex] + " >", formX, modeY, fieldW, 28,
                   hoveredButton == 0, true);
        endDraw();
        formY += 36;

        // ---- Difficulty ----
        renderLabel("Difficulty:", formX, formY, labelScale);
        formY += 17;
        beginDraw(screenW, screenH);
        float diffY = screenH - formY - 28;
        drawButton("< " + DIFF_NAMES[difficultyIndex] + " >", formX, diffY, fieldW, 28,
                   hoveredButton == 1, true);
        endDraw();
        formY += 36;

        // ---- Seed ----
        renderLabel("Seed (leave empty for random):", formX, formY, labelScale);
        formY += 17;
        renderTextField(seedText, formX, formY, fieldW, 28, focusedField == 1);
        formY += 36;

        // ---- World Type (Preset) ----
        renderLabel("World Type:", formX, formY, labelScale);
        formY += 17;
        beginDraw(screenW, screenH);
        float presetY = screenH - formY - 28;
        drawButton("< " + PRESETS[presetIndex].getDisplayName() + " >", formX, presetY, fieldW, 28,
                   hoveredButton == 10, true);
        endDraw();
        // Description below preset
        formY += 30;
        font.drawText(PRESETS[presetIndex].getDescription(), formX + 4, formY, 1.5f, screenW, screenH,
                       0.5f, 0.6f, 0.7f, 0.8f);
        formY += 20;

        // ---- More World Options ----
        beginDraw(screenW, screenH);
        float moreY = screenH - formY - 28;
        String moreLabel = showAdvanced ? "v Hide Advanced Options" : "> More World Options...";
        drawButton(moreLabel, formX, moreY, fieldW, 28, hoveredButton == 4, true);
        endDraw();
        formY += 36;

        if (showAdvanced) {
            renderToggle("Show Coordinates", showCoordinates, formX, formY, fieldW, hoveredButton == 5);
            formY += 30;
            renderToggle("Bonus Chest", bonusChest, formX, formY, fieldW, hoveredButton == 6);
            formY += 34;

            // ---- Advanced Gen Settings button ----
            beginDraw(screenW, screenH);
            float genBtnY = screenH - formY - 28;
            String genLabel = showGenSettings ? "v Hide Gen Settings" : "> Advanced Gen Settings...";
            drawButton(genLabel, formX, genBtnY, fieldW, 28, hoveredButton == 11, true);
            endDraw();
            formY += 36;

            if (showGenSettings) {
                // Render sliders
                for (int i = 0; i < SLIDER_LABELS.length; i++) {
                    // Skip terrain scale slider for flat worlds
                    if (advancedConfig != null && advancedConfig.flatWorld && i == 1) continue;

                    float val = getSliderValue(i);
                    String displayVal;
                    if (i == 0) {
                        displayVal = String.valueOf(Math.round(val));
                    } else {
                        displayVal = Math.round(val) + SLIDER_SUFFIX[i];
                    }

                    renderSlider(SLIDER_LABELS[i] + ": " + displayVal,
                        formX, formY, fieldW, 24,
                        SLIDER_MIN[i], SLIDER_MAX[i], val,
                        hoveredButton == (20 + i) || draggingSlider == i);
                    formY += 32;
                }

                // Reset to preset defaults button
                beginDraw(screenW, screenH);
                float resetY = screenH - formY - 24;
                drawButton("Reset to Preset Defaults", formX + fieldW / 4, resetY,
                    fieldW / 2, 24, hoveredButton == 30, true);
                endDraw();
                formY += 32;
            }
        }

        // ---- Create / Cancel buttons ----
        float btnRowY = Math.max(formY + 10, screenH * 0.88f);
        float halfW = (fieldW - BUTTON_GAP) / 2.0f;

        beginDraw(screenW, screenH);
        float createY = screenH - btnRowY - BUTTON_HEIGHT;
        boolean canCreate = !worldName.trim().isEmpty();
        drawButton("Create World", formX, createY, halfW, BUTTON_HEIGHT,
                   hoveredButton == 7, canCreate);
        drawButton("Cancel", formX + halfW + BUTTON_GAP, createY, halfW, BUTTON_HEIGHT,
                   hoveredButton == 8, true);
        endDraw();
    }

    private void renderLabel(String text, float x, float topY, float scale) {
        font.drawText(text, x, topY, scale, screenW, screenH,
                       0.7f, 0.8f, 0.9f, 0.9f);
    }

    private void renderTextField(String text, float formX, float topY, float fieldW,
                                  float fieldH, boolean focused) {
        beginDraw(screenW, screenH);
        float fieldY = screenH - topY - fieldH;

        fillRect(formX, fieldY, fieldW, fieldH, 0.05f, 0.05f, 0.08f, 0.9f);

        if (focused) {
            strokeRect(formX, fieldY, fieldW, fieldH, 2, 0.4f, 0.6f, 0.9f, 1.0f);
        } else {
            strokeRect(formX, fieldY, fieldW, fieldH, 1, 0.3f, 0.3f, 0.3f, 0.7f);
        }

        endDraw();

        String displayText = text;
        if (focused && cursorVisible) {
            displayText += "_";
        }
        font.drawText(displayText, formX + 6, topY + 6, 1.8f, screenW, screenH,
                       1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderToggle(String label, boolean value, float formX, float topY,
                                float fieldW, boolean hovered) {
        beginDraw(screenW, screenH);
        float toggleY = screenH - topY - 24;
        float boxSize = 20;
        float boxX = formX;

        if (hovered) {
            fillRect(boxX, toggleY + 2, boxSize, boxSize, 0.2f, 0.3f, 0.4f, 0.8f);
        } else {
            fillRect(boxX, toggleY + 2, boxSize, boxSize, 0.1f, 0.1f, 0.13f, 0.8f);
        }
        strokeRect(boxX, toggleY + 2, boxSize, boxSize, 2, 0.4f, 0.4f, 0.4f, 0.8f);

        if (value) {
            fillRect(boxX + 4, toggleY + 6, boxSize - 8, boxSize - 8,
                     0.3f, 0.8f, 0.3f, 1.0f);
        }

        endDraw();

        font.drawText(label, formX + boxSize + 8, topY + 3, 1.8f,
                       screenW, screenH, 0.8f, 0.8f, 0.8f, 0.9f);
    }

    private void renderSlider(String label, float formX, float topY, float fieldW,
                               float fieldH, float min, float max, float value,
                               boolean hovered) {
        // Label above slider
        font.drawText(label, formX, topY, 1.6f, screenW, screenH,
                       0.7f, 0.8f, 0.9f, 0.9f);

        float sliderY = screenH - topY - fieldH - 2;
        float trackX = formX + 150;
        float trackW = fieldW - 150;

        beginDraw(screenW, screenH);

        // Track background
        fillRect(trackX, sliderY + fieldH/2 - 2, trackW, 4,
                 0.15f, 0.15f, 0.2f, 0.9f);

        // Filled portion
        float ratio = Math.max(0, Math.min(1, (value - min) / (max - min)));
        fillRect(trackX, sliderY + fieldH/2 - 2, trackW * ratio, 4,
                 0.3f, 0.6f, 0.9f, 0.9f);

        // Handle
        float handleX = trackX + trackW * ratio - 6;
        float handleColor = hovered ? 0.9f : 0.7f;
        fillRect(handleX, sliderY + 2, 12, fieldH - 4,
                 handleColor, handleColor, handleColor, 1.0f);
        strokeRect(handleX, sliderY + 2, 12, fieldH - 4, 1,
                   0.4f, 0.4f, 0.4f, 0.8f);

        endDraw();
    }

    // ---- Hit test helpers ----

    private float getFormY() { return screenH * 0.10f; }
    private float getFormX() { return (screenW - 400) / 2.0f; }

    /** Compute all Y positions for hit testing. Returns array of Y positions for each element. */
    private float[] computeLayout() {
        float formX = getFormX();
        float formY = getFormY();
        float[] ys = new float[40]; // enough for all elements

        // World name
        ys[0] = formY + 17; // name field
        formY += 17 + 36;

        // Game mode
        ys[1] = formY + 17; // mode button
        formY += 17 + 36;

        // Difficulty
        ys[2] = formY + 17; // diff button
        formY += 17 + 36;

        // Seed
        ys[3] = formY + 17; // seed field
        formY += 17 + 36;

        // Preset
        ys[4] = formY + 17; // preset button
        formY += 17 + 30 + 20;

        // More options button
        ys[5] = formY; // more options
        formY += 36;

        if (showAdvanced) {
            ys[6] = formY;  // show coords toggle
            formY += 30;
            ys[7] = formY;  // bonus chest toggle
            formY += 34;
            ys[8] = formY;  // gen settings button
            formY += 36;

            if (showGenSettings) {
                int sliderIdx = 0;
                for (int i = 0; i < SLIDER_LABELS.length; i++) {
                    if (advancedConfig != null && advancedConfig.flatWorld && i == 1) continue;
                    ys[9 + sliderIdx] = formY;
                    formY += 32;
                    sliderIdx++;
                }
                ys[14] = formY; // reset button
                formY += 32;
            }
        }

        // Create/cancel
        ys[15] = Math.max(formY + 10, screenH * 0.88f);
        return ys;
    }

    @Override
    public void handleClick(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        float formX = getFormX();
        float fieldW = 400.0f;
        float[] ys = computeLayout();

        // World name field
        float nameFieldY = screenH - ys[0] - 28;
        if (isInside(clickX, clickY, formX, nameFieldY, fieldW, 28)) {
            focusedField = 0;
            return;
        }

        // Game mode button
        float modeY = screenH - ys[1] - 28;
        if (isInside(clickX, clickY, formX, modeY, fieldW, 28)) {
            gameModeIndex = (gameModeIndex + 1) % GAME_MODES.length;
            focusedField = -1;
            return;
        }

        // Difficulty button
        float diffY = screenH - ys[2] - 28;
        if (isInside(clickX, clickY, formX, diffY, fieldW, 28)) {
            difficultyIndex = (difficultyIndex + 1) % DIFFICULTIES.length;
            focusedField = -1;
            return;
        }

        // Seed field
        float seedFieldY = screenH - ys[3] - 28;
        if (isInside(clickX, clickY, formX, seedFieldY, fieldW, 28)) {
            focusedField = 1;
            return;
        }

        // Preset button
        float presetBtnY = screenH - ys[4] - 28;
        if (isInside(clickX, clickY, formX, presetBtnY, fieldW, 28)) {
            presetIndex = (presetIndex + 1) % PRESETS.length;
            syncConfigFromPreset();
            focusedField = -1;
            return;
        }

        // More options button
        float moreBtnY = screenH - ys[5] - 28;
        if (isInside(clickX, clickY, formX, moreBtnY, fieldW, 28)) {
            showAdvanced = !showAdvanced;
            focusedField = -1;
            return;
        }

        if (showAdvanced) {
            // Show coords toggle
            float coordY = screenH - ys[6] - 24;
            if (isInside(clickX, clickY, formX, coordY, fieldW, 24)) {
                showCoordinates = !showCoordinates;
                return;
            }

            // Bonus chest toggle
            float bonusY = screenH - ys[7] - 24;
            if (isInside(clickX, clickY, formX, bonusY, fieldW, 24)) {
                bonusChest = !bonusChest;
                return;
            }

            // Gen settings button
            float genBtnY = screenH - ys[8] - 28;
            if (isInside(clickX, clickY, formX, genBtnY, fieldW, 28)) {
                showGenSettings = !showGenSettings;
                focusedField = -1;
                return;
            }

            if (showGenSettings) {
                // Check slider clicks
                int sliderIdx = 0;
                for (int i = 0; i < SLIDER_LABELS.length; i++) {
                    if (advancedConfig != null && advancedConfig.flatWorld && i == 1) continue;
                    float sliderTopY = ys[9 + sliderIdx];
                    float sliderBotY = screenH - sliderTopY - 26;
                    float trackX = formX + 150;
                    float trackW = fieldW - 150;
                    if (isInside(clickX, clickY, trackX - 10, sliderBotY - 4, trackW + 20, 34)) {
                        draggingSlider = i;
                        float ratio = Math.max(0, Math.min(1, (clickX - trackX) / trackW));
                        float val = SLIDER_MIN[i] + ratio * (SLIDER_MAX[i] - SLIDER_MIN[i]);
                        setSliderValue(i, val);
                        return;
                    }
                    sliderIdx++;
                }

                // Reset button
                float resetY = screenH - ys[14] - 24;
                if (isInside(clickX, clickY, formX + fieldW/4, resetY, fieldW/2, 24)) {
                    syncConfigFromPreset();
                    return;
                }
            }
        }

        // Create / Cancel buttons
        float halfW = (fieldW - BUTTON_GAP) / 2.0f;
        float createY = screenH - ys[15] - BUTTON_HEIGHT;

        if (isInside(clickX, clickY, formX, createY, halfW, BUTTON_HEIGHT)) {
            if (!worldName.trim().isEmpty() && callback != null) {
                callback.onCreateWorld(worldName.trim(), GAME_MODES[gameModeIndex],
                    DIFFICULTIES[difficultyIndex], seedText.trim(),
                    showCoordinates, bonusChest,
                    PRESETS[presetIndex], advancedConfig);
            }
        } else if (isInside(clickX, clickY, formX + halfW + BUTTON_GAP, createY, halfW, BUTTON_HEIGHT)) {
            if (callback != null) callback.onCancel();
        }

        focusedField = -1;
    }

    /** Handle mouse drag for sliders. */
    public void handleMouseDrag(double mx, double my, int screenW, int screenH) {
        if (draggingSlider < 0) return;

        float formX = getFormX();
        float fieldW = 400.0f;
        float trackX = formX + 150;
        float trackW = fieldW - 150;

        float ratio = Math.max(0, Math.min(1, ((float)mx - trackX) / trackW));
        float val = SLIDER_MIN[draggingSlider] + ratio * (SLIDER_MAX[draggingSlider] - SLIDER_MIN[draggingSlider]);
        setSliderValue(draggingSlider, val);
    }

    /** Call when mouse button released. */
    public void handleMouseRelease() {
        draggingSlider = -1;
    }

    @Override
    public boolean handleKeyPress(int key) {
        if (focusedField < 0) return false;

        if (key == GLFW_KEY_BACKSPACE) {
            if (focusedField == 0 && !worldName.isEmpty()) {
                worldName = worldName.substring(0, worldName.length() - 1);
            } else if (focusedField == 1 && !seedText.isEmpty()) {
                seedText = seedText.substring(0, seedText.length() - 1);
            }
            cursorTimer = 0;
            cursorVisible = true;
            return true;
        }

        if (key == GLFW_KEY_TAB) {
            focusedField = (focusedField + 1) % 2;
            cursorTimer = 0;
            cursorVisible = true;
            return true;
        }

        if (key == GLFW_KEY_ENTER) {
            if (!worldName.trim().isEmpty() && callback != null) {
                callback.onCreateWorld(worldName.trim(), GAME_MODES[gameModeIndex],
                    DIFFICULTIES[difficultyIndex], seedText.trim(),
                    showCoordinates, bonusChest,
                    PRESETS[presetIndex], advancedConfig);
            }
            return true;
        }

        if (key == GLFW_KEY_ESCAPE) {
            if (callback != null) callback.onCancel();
            return true;
        }

        return false;
    }

    @Override
    public void handleCharTyped(char c) {
        if (focusedField < 0) return;
        if (c < 32 || c > 126) return;

        if (focusedField == 0 && worldName.length() < 30) {
            worldName += c;
        } else if (focusedField == 1 && seedText.length() < 20) {
            seedText += c;
        }
        cursorTimer = 0;
        cursorVisible = true;
    }

    public void updateHover(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        hoveredButton = -1;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        float formX = getFormX();
        float fieldW = 400.0f;
        float[] ys = computeLayout();

        // Name field
        float nameFieldY = screenH - ys[0] - 28;
        if (isInside(clickX, clickY, formX, nameFieldY, fieldW, 28)) { hoveredButton = 3; return; }

        // Game mode
        float modeY = screenH - ys[1] - 28;
        if (isInside(clickX, clickY, formX, modeY, fieldW, 28)) { hoveredButton = 0; return; }

        // Difficulty
        float diffY = screenH - ys[2] - 28;
        if (isInside(clickX, clickY, formX, diffY, fieldW, 28)) { hoveredButton = 1; return; }

        // Seed
        float seedFieldY = screenH - ys[3] - 28;
        if (isInside(clickX, clickY, formX, seedFieldY, fieldW, 28)) { hoveredButton = 2; return; }

        // Preset
        float presetBtnY = screenH - ys[4] - 28;
        if (isInside(clickX, clickY, formX, presetBtnY, fieldW, 28)) { hoveredButton = 10; return; }

        // More options
        float moreBtnY = screenH - ys[5] - 28;
        if (isInside(clickX, clickY, formX, moreBtnY, fieldW, 28)) { hoveredButton = 4; return; }

        if (showAdvanced) {
            float coordY = screenH - ys[6] - 24;
            if (isInside(clickX, clickY, formX, coordY, fieldW, 24)) { hoveredButton = 5; return; }

            float bonusY = screenH - ys[7] - 24;
            if (isInside(clickX, clickY, formX, bonusY, fieldW, 24)) { hoveredButton = 6; return; }

            float genBtnY = screenH - ys[8] - 28;
            if (isInside(clickX, clickY, formX, genBtnY, fieldW, 28)) { hoveredButton = 11; return; }

            if (showGenSettings) {
                int sliderIdx = 0;
                for (int i = 0; i < SLIDER_LABELS.length; i++) {
                    if (advancedConfig != null && advancedConfig.flatWorld && i == 1) continue;
                    float sliderTopY = ys[9 + sliderIdx];
                    float sliderBotY = screenH - sliderTopY - 26;
                    float trackX = formX + 150;
                    float trackW = fieldW - 150;
                    if (isInside(clickX, clickY, trackX - 10, sliderBotY - 4, trackW + 20, 34)) {
                        hoveredButton = 20 + i;
                        return;
                    }
                    sliderIdx++;
                }

                float resetY = screenH - ys[14] - 24;
                if (isInside(clickX, clickY, formX + fieldW/4, resetY, fieldW/2, 24)) {
                    hoveredButton = 30;
                    return;
                }
            }
        }

        // Create/Cancel
        float halfW = (fieldW - BUTTON_GAP) / 2.0f;
        float createBtnY = screenH - ys[15] - BUTTON_HEIGHT;
        if (isInside(clickX, clickY, formX, createBtnY, halfW, BUTTON_HEIGHT)) hoveredButton = 7;
        else if (isInside(clickX, clickY, formX + halfW + BUTTON_GAP, createBtnY, halfW, BUTTON_HEIGHT)) hoveredButton = 8;
    }

    public boolean hasFocusedField() { return focusedField >= 0; }
    public boolean isDraggingSlider() { return draggingSlider >= 0; }
}
