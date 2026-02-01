package com.voxelgame.ui;

import com.voxelgame.sim.Inventory;
import com.voxelgame.sim.Player;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import org.joml.Vector3f;

/**
 * Debug information overlay (F3 screen).
 * Shows FPS, position, chunk info, fly mode, and facing direction.
 * Uses the BitmapFont renderer for on-screen text.
 */
public class DebugOverlay {

    private boolean visible = false;
    private final BitmapFont font;

    private static final float FONT_SCALE = 2.0f;
    private static final float LINE_HEIGHT = 8.0f * FONT_SCALE + 4.0f; // char height + padding
    private static final float MARGIN_X = 8.0f;
    private static final float MARGIN_Y = 8.0f;

    // Shadow color for readability
    private static final float SHADOW_R = 0.0f;
    private static final float SHADOW_G = 0.0f;
    private static final float SHADOW_B = 0.0f;
    private static final float SHADOW_A = 0.8f;

    // Text color
    private static final float TEXT_R = 1.0f;
    private static final float TEXT_G = 1.0f;
    private static final float TEXT_B = 1.0f;
    private static final float TEXT_A = 1.0f;

    public DebugOverlay(BitmapFont font) {
        this.font = font;
    }

    public void toggle() {
        visible = !visible;
    }

    public boolean isVisible() {
        return visible;
    }

    /** Backward-compatible overload (no item entity count). */
    public void render(Player player, World world, int fps, int screenW, int screenH, boolean sprinting) {
        render(player, world, fps, screenW, screenH, sprinting, 0);
    }

    /**
     * Render debug text overlay.
     */
    public void render(Player player, World world, int fps, int screenW, int screenH,
                       boolean sprinting, int itemEntityCount) {
        if (!visible) return;

        Vector3f pos = player.getPosition();
        float yaw   = player.getCamera().getYaw();
        float pitch  = player.getCamera().getPitch();

        int cx = Math.floorDiv((int) Math.floor(pos.x), WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv((int) Math.floor(pos.z), WorldConstants.CHUNK_SIZE);
        int loadedChunks = world.getChunkMap().size();

        String facing = getFacingDirection(yaw);

        float feetY = pos.y - Player.EYE_HEIGHT;

        String[] lines = {
            String.format("FPS: %d", fps),
            String.format("Pos: %.1f / %.1f / %.1f  (feet: %.1f)", pos.x, pos.y, pos.z, feetY),
            String.format("Chunk: %d, %d", cx, cz),
            String.format("Loaded chunks: %d", loadedChunks),
            String.format("Fly: %s  Ground: %s  Sprint: %s",
                player.isFlyMode() ? "ON" : "OFF",
                player.isOnGround() ? "YES" : "NO",
                sprinting ? "YES" : "NO"),
            String.format("Facing: %s (yaw %.1f / pitch %.1f)", facing, yaw, pitch),
            String.format("Mode: %s  HP: %.1f/%.1f  (F4=cycle mode)",
                player.getGameMode(), player.getHealth(), player.getMaxHealth()),
            String.format("Inv: %d/%d slots  Items: %d  (E=inventory)",
                player.getInventory().getUsedSlotCount(), Inventory.TOTAL_SIZE, itemEntityCount),
        };

        // Render each line with shadow for readability
        float y = MARGIN_Y;
        for (String line : lines) {
            // Shadow (offset by 1 pixel)
            font.drawText(line, MARGIN_X + 1, y + 1, FONT_SCALE, screenW, screenH,
                          SHADOW_R, SHADOW_G, SHADOW_B, SHADOW_A);
            // Text
            font.drawText(line, MARGIN_X, y, FONT_SCALE, screenW, screenH,
                          TEXT_R, TEXT_G, TEXT_B, TEXT_A);
            y += LINE_HEIGHT;
        }
    }

    /**
     * Convert yaw angle to cardinal direction name.
     */
    private static String getFacingDirection(float yaw) {
        // Normalize yaw to 0-360
        float normalized = ((yaw % 360) + 360) % 360;
        if (normalized >= 337.5 || normalized < 22.5)   return "East (+X)";
        if (normalized < 67.5)  return "South-East";
        if (normalized < 112.5) return "South (+Z)";
        if (normalized < 157.5) return "South-West";
        if (normalized < 202.5) return "West (-X)";
        if (normalized < 247.5) return "North-West";
        if (normalized < 292.5) return "North (-Z)";
        return "North-East";
    }
}
