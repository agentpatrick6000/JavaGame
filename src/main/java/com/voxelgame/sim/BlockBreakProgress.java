package com.voxelgame.sim;

import com.voxelgame.world.Block;
import com.voxelgame.world.Blocks;

/**
 * Tracks the progress of breaking a block in survival mode.
 * In creative mode, blocks break instantly.
 * In survival mode, the player must hold left-click for the block's hardness duration.
 */
public class BlockBreakProgress {

    /** The block position being broken. */
    private int targetX, targetY, targetZ;
    /** The block ID being broken. */
    private int targetBlockId;
    /** Accumulated break time in seconds. */
    private float progress;
    /** Total time needed to break (from block hardness). */
    private float totalTime;
    /** Whether we're actively breaking. */
    private boolean active;

    public BlockBreakProgress() {
        reset();
    }

    /**
     * Start or continue breaking a block.
     * If the target changed, reset progress.
     *
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param blockId block type at (x,y,z)
     * @param dt delta time this frame
     * @return true if the block is now fully broken
     */
    public boolean update(int x, int y, int z, int blockId, float dt) {
        Block block = Blocks.get(blockId);

        // Unbreakable blocks
        if (!block.isBreakable()) {
            reset();
            return false;
        }

        // Instant break (hardness 0)
        if (block.getBreakTime() <= 0) {
            reset();
            return true;
        }

        // Different target? Reset progress
        if (!active || x != targetX || y != targetY || z != targetZ || blockId != targetBlockId) {
            targetX = x;
            targetY = y;
            targetZ = z;
            targetBlockId = blockId;
            progress = 0;
            totalTime = block.getBreakTime();
            active = true;
        }

        // Accumulate progress
        progress += dt;

        // Check if done
        if (progress >= totalTime) {
            reset();
            return true;
        }

        return false;
    }

    /** Reset break progress (player stopped clicking or target changed). */
    public void reset() {
        active = false;
        progress = 0;
        totalTime = 0;
        targetX = Integer.MIN_VALUE;
        targetY = Integer.MIN_VALUE;
        targetZ = Integer.MIN_VALUE;
        targetBlockId = -1;
    }

    /** Get break progress as 0..1 fraction. */
    public float getProgressFraction() {
        if (!active || totalTime <= 0) return 0;
        return Math.min(1.0f, progress / totalTime);
    }

    /** Whether actively breaking a block. */
    public boolean isActive() {
        return active;
    }

    public int getTargetX() { return targetX; }
    public int getTargetY() { return targetY; }
    public int getTargetZ() { return targetZ; }
}
