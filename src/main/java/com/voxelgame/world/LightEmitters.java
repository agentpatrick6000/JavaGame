package com.voxelgame.world;

/**
 * Phase 4: Light source color and intensity definitions.
 * Different light sources emit different colored light:
 * - Torch: warm orange (cozy, inviting)
 * - Fire: bright orange-red (dangerous, hot)
 * - Lava: deep orange (molten, ominous)
 * - Redstone torch: dim red (mechanical, eerie)
 * - Jack o'lantern: bright warm white
 */
public final class LightEmitters {

    private LightEmitters() {}

    // Light source colors (RGB, 0-1 normalized)
    // These are the colors at FULL intensity; they scale with emission level

    /** Torch: warm orange glow (cozy firelight) */
    public static final float[] TORCH_COLOR = {1.0f, 0.75f, 0.45f};

    /** Fire: bright orange-red (hot, dangerous) */
    public static final float[] FIRE_COLOR = {1.0f, 0.6f, 0.3f};

    /** Lava: deep orange (molten rock) */
    public static final float[] LAVA_COLOR = {1.0f, 0.4f, 0.2f};

    /** Redstone torch: dim red glow (mechanical, eerie) */
    public static final float[] REDSTONE_TORCH_COLOR = {0.8f, 0.15f, 0.08f};

    /** Jack o'lantern: bright warm white */
    public static final float[] JACK_O_LANTERN_COLOR = {1.0f, 0.9f, 0.7f};

    /** Default: neutral warm white for unknown emitters */
    public static final float[] DEFAULT_COLOR = {1.0f, 1.0f, 0.9f};

    /**
     * Get the RGB color for a light-emitting block.
     * Returns a 3-element float array [R, G, B] in 0-1 range.
     * These values are already scaled by the emission intensity.
     * 
     * @param blockId the block ID to check
     * @return RGB color array, or null if block doesn't emit light
     */
    public static float[] getLightColorRGB(int blockId) {
        int emission = Blocks.getLightEmission(blockId);
        if (emission <= 0) return null;

        // Get base color
        float[] baseColor;
        if (blockId == Blocks.TORCH.id()) {
            baseColor = TORCH_COLOR;
        } else if (blockId == Blocks.FIRE.id()) {
            baseColor = FIRE_COLOR;
        } else if (blockId == Blocks.REDSTONE_TORCH.id()) {
            baseColor = REDSTONE_TORCH_COLOR;
        } else if (blockId == Blocks.JACK_O_LANTERN.id()) {
            baseColor = JACK_O_LANTERN_COLOR;
        } else if (Blocks.isLava(blockId)) {
            baseColor = LAVA_COLOR;
        } else {
            baseColor = DEFAULT_COLOR;
        }

        // Scale by emission intensity (0-15 → 0-1)
        float intensity = emission / 15.0f;
        return new float[] {
            baseColor[0] * intensity,
            baseColor[1] * intensity,
            baseColor[2] * intensity
        };
    }

    /**
     * Get the light emission intensity for a block.
     * @param blockId the block ID
     * @return emission level 0-1 (0 = no emission, 1 = max)
     */
    public static float getLightIntensity(int blockId) {
        return Blocks.getLightEmission(blockId) / 15.0f;
    }

    /**
     * Check if a block emits light.
     * @param blockId the block ID
     * @return true if the block is a light source
     */
    public static boolean isLightSource(int blockId) {
        return Blocks.getLightEmission(blockId) > 0;
    }
}
