package com.voxelgame.ui;

import com.voxelgame.core.Profiler;
import com.voxelgame.sim.Inventory;
import com.voxelgame.sim.Player;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import com.voxelgame.world.stream.ChunkManager;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Debug information overlay (F3 screen).
 * Shows FPS, position, chunk info, fly mode, facing direction, and LOD stats.
 * Uses the BitmapFont renderer for on-screen text.
 * 
 * Display modes (cycle with F3):
 *   OFF → BASIC → BASIC+CHUNKS → BASIC+CHUNKS+PROFILER → OFF
 */
public class DebugOverlay {

    /** Display mode enum */
    public enum DisplayMode {
        OFF,
        BASIC,
        BASIC_CHUNKS,
        BASIC_CHUNKS_PROFILER
    }

    private DisplayMode mode = DisplayMode.OFF;
    private final BitmapFont font;

    // Base font scale (for 1080p reference resolution)
    private static final float BASE_FONT_SCALE = 1.0f;
    private static final float BASE_LINE_HEIGHT = 10.0f; // 8px char + 2px padding
    private static final float MARGIN_X = 6.0f;
    private static final float MARGIN_Y = 6.0f;
    private static final float PANEL_PADDING = 4.0f;

    // Computed scale values (updated each frame based on window size)
    private float uiScale = 1.0f;
    private float fontScale = BASE_FONT_SCALE;
    private float lineHeight = BASE_LINE_HEIGHT;

    // Max lines to prevent overflow
    private static final int MAX_LINES = 20;

    // Panel background color (translucent dark)
    private static final float BG_R = 0.0f;
    private static final float BG_G = 0.0f;
    private static final float BG_B = 0.0f;
    private static final float BG_A = 0.6f;

    // Shadow color for text readability
    private static final float SHADOW_R = 0.0f;
    private static final float SHADOW_G = 0.0f;
    private static final float SHADOW_B = 0.0f;
    private static final float SHADOW_A = 0.8f;

    // Text color (white)
    private static final float TEXT_R = 1.0f;
    private static final float TEXT_G = 1.0f;
    private static final float TEXT_B = 1.0f;
    private static final float TEXT_A = 1.0f;

    // Section header color (yellow-ish)
    private static final float HEADER_R = 1.0f;
    private static final float HEADER_G = 0.9f;
    private static final float HEADER_B = 0.4f;

    public DebugOverlay(BitmapFont font) {
        this.font = font;
    }

    /**
     * Cycle through display modes: OFF → BASIC → BASIC+CHUNKS → BASIC+CHUNKS+PROFILER → OFF
     */
    public void toggle() {
        mode = switch (mode) {
            case OFF -> DisplayMode.BASIC;
            case BASIC -> DisplayMode.BASIC_CHUNKS;
            case BASIC_CHUNKS -> DisplayMode.BASIC_CHUNKS_PROFILER;
            case BASIC_CHUNKS_PROFILER -> DisplayMode.OFF;
        };
    }

    /**
     * Get current display mode.
     */
    public DisplayMode getMode() {
        return mode;
    }

    /**
     * Check if overlay is visible (any mode except OFF).
     */
    public boolean isVisible() {
        return mode != DisplayMode.OFF;
    }

    /**
     * Calculate UI scale based on window height.
     * Reference: 1080p = scale 1.0, smaller windows get smaller text.
     */
    private void updateScale(int screenH) {
        // Scale range: 0.6 to 1.0, based on 1080p reference
        uiScale = Math.max(0.6f, Math.min(1.0f, screenH / 1080.0f));
        fontScale = BASE_FONT_SCALE * uiScale;
        lineHeight = BASE_LINE_HEIGHT * uiScale;
    }

    /** Backward-compatible overload (no entity counts or time). */
    public void render(Player player, World world, int fps, int screenW, int screenH, boolean sprinting) {
        render(player, world, fps, screenW, screenH, sprinting, 0, 0, "", null, 0, 0);
    }

    /** Backward-compatible overload (no LOD stats). */
    public void render(Player player, World world, int fps, int screenW, int screenH,
                       boolean sprinting, int itemEntityCount, int mobCount, String worldTimeStr) {
        render(player, world, fps, screenW, screenH, sprinting, itemEntityCount, mobCount, worldTimeStr, null, 0, 0);
    }

    /**
     * Render debug text overlay with LOD stats.
     */
    public void render(Player player, World world, int fps, int screenW, int screenH,
                       boolean sprinting, int itemEntityCount, int mobCount, String worldTimeStr,
                       ChunkManager chunkManager, int renderedChunks, int culledChunks) {
        if (mode == DisplayMode.OFF) return;

        // Update scale based on window size
        updateScale(screenH);

        Vector3f pos = player.getPosition();
        float yaw = player.getCamera().getYaw();
        float pitch = player.getCamera().getPitch();

        int cx = Math.floorDiv((int) Math.floor(pos.x), WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv((int) Math.floor(pos.z), WorldConstants.CHUNK_SIZE);
        int loadedChunks = world.getChunkMap().size();

        String facing = getFacingDirection(yaw);
        float feetY = pos.y - Player.EYE_HEIGHT;

        // Build lines based on display mode
        List<String> lines = new ArrayList<>();

        // === BASIC INFO (always shown when visible) ===
        lines.add(String.format("FPS: %d", fps));
        lines.add(String.format("XYZ: %.1f / %.1f / %.1f", pos.x, pos.y, pos.z));
        lines.add(String.format("Chunk: %d, %d  Facing: %s", cx, cz, facing));
        lines.add(String.format("Fly: %s  Ground: %s  Sprint: %s",
            player.isFlyMode() ? "ON" : "OFF",
            player.isOnGround() ? "Y" : "N",
            sprinting ? "Y" : "N"));
        lines.add(String.format("Mode: %s  HP: %.0f/%.0f",
            player.getGameMode(), player.getHealth(), player.getMaxHealth()));

        // === CHUNK INFO (BASIC_CHUNKS and BASIC_CHUNKS_PROFILER) ===
        if (mode == DisplayMode.BASIC_CHUNKS || mode == DisplayMode.BASIC_CHUNKS_PROFILER) {
            lines.add(""); // Spacer
            if (chunkManager != null) {
                lines.add(String.format("Chunks: %d loaded  %d rendered  %d culled",
                    loadedChunks, renderedChunks, culledChunks));
                lines.add(String.format("LOD: 0=%d 1=%d 2=%d 3=%d",
                    chunkManager.getLod0Count(), chunkManager.getLod1Count(),
                    chunkManager.getLod2Count(), chunkManager.getLod3Count()));
                lines.add(String.format("Pending: %d uploads", chunkManager.getPendingUploads()));
            } else {
                lines.add(String.format("Chunks: %d loaded", loadedChunks));
            }
            lines.add(String.format("Entities: %d items  %d mobs", itemEntityCount, mobCount));
        }

        // === PROFILER (BASIC_CHUNKS_PROFILER only) ===
        if (mode == DisplayMode.BASIC_CHUNKS_PROFILER) {
            Profiler profiler = Profiler.getInstance();
            List<String> sections = profiler.getSections();

            if (!sections.isEmpty()) {
                lines.add(""); // Spacer
                
                // Compact single-line summary of key metrics
                double frameMs = profiler.getAverageMs("Frame");
                double renderMs = profiler.getAverageMs("Render");
                double uiMs = profiler.getAverageMs("UI");
                double worldMs = profiler.getAverageMs("World");
                double chunkMs = profiler.getAverageMs("ChunkMesh");
                
                lines.add(String.format("[Perf] frame=%.1f render=%.1f ui=%.1f chunk=%.1f world=%.1fms",
                    frameMs, renderMs, uiMs, chunkMs, worldMs));

                // Additional profiler details (top 4 most expensive)
                List<String> timings = profiler.getTimings(4);
                for (String timing : timings) {
                    lines.add("  " + timing);
                }
            }
        }

        // Add scale/mode info at the bottom (for verification)
        lines.add(""); // Spacer
        lines.add(String.format("[F3 mode: %s | scale: %.2f | lines: %d]",
            mode.name(), uiScale, lines.size()));

        // Clamp to max lines
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }

        // Calculate panel dimensions
        float charWidth = 8.0f * fontScale;
        int maxLineLength = 0;
        for (String line : lines) {
            maxLineLength = Math.max(maxLineLength, line.length());
        }
        float panelWidth = MARGIN_X + (maxLineLength * charWidth) + PANEL_PADDING * 2;
        float panelHeight = MARGIN_Y + (lines.size() * lineHeight) + PANEL_PADDING * 2;

        // Render translucent background panel
        renderBackgroundPanel(0, 0, panelWidth, panelHeight, screenW, screenH);

        // Render each line
        float y = MARGIN_Y + PANEL_PADDING;
        for (String line : lines) {
            if (line.isEmpty()) {
                y += lineHeight * 0.5f; // Half-height for spacers
                continue;
            }

            float textX = MARGIN_X + PANEL_PADDING;

            // Shadow (offset by 1 pixel scaled)
            float shadowOffset = Math.max(1.0f, uiScale);
            font.drawText(line, textX + shadowOffset, y + shadowOffset, fontScale, screenW, screenH,
                          SHADOW_R, SHADOW_G, SHADOW_B, SHADOW_A);

            // Determine text color (headers get yellow tint)
            float r = TEXT_R, g = TEXT_G, b = TEXT_B;
            if (line.startsWith("[Perf]") || line.startsWith("[F3")) {
                r = HEADER_R; g = HEADER_G; b = HEADER_B;
            }

            // Main text
            font.drawText(line, textX, y, fontScale, screenW, screenH, r, g, b, TEXT_A);
            y += lineHeight;
        }
    }

    /**
     * Render a translucent background rectangle.
     * Uses immediate mode for simplicity (could be optimized with a quad shader).
     */
    private void renderBackgroundPanel(float x, float y, float width, float height, int screenW, int screenH) {
        // Save current state
        boolean depthWasEnabled = glIsEnabled(GL_DEPTH_TEST);
        
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Use legacy immediate mode for simple quad (available in GL33 compat profile)
        // Convert from top-left origin to OpenGL bottom-left
        float x0 = x;
        float y0 = screenH - y - height;
        float x1 = x + width;
        float y1 = screenH - y;

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenW, 0, screenH, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glColor4f(BG_R, BG_G, BG_B, BG_A);
        glBegin(GL_QUADS);
        glVertex2f(x0, y0);
        glVertex2f(x1, y0);
        glVertex2f(x1, y1);
        glVertex2f(x0, y1);
        glEnd();

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        // Restore color
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        if (depthWasEnabled) {
            glEnable(GL_DEPTH_TEST);
        }
    }

    /**
     * Convert yaw angle to cardinal direction name.
     */
    private static String getFacingDirection(float yaw) {
        // Normalize yaw to 0-360
        float normalized = ((yaw % 360) + 360) % 360;
        if (normalized >= 337.5 || normalized < 22.5) return "E";
        if (normalized < 67.5) return "SE";
        if (normalized < 112.5) return "S";
        if (normalized < 157.5) return "SW";
        if (normalized < 202.5) return "W";
        if (normalized < 247.5) return "NW";
        if (normalized < 292.5) return "N";
        return "NE";
    }
}
