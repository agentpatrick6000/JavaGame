package com.voxelgame.sim;

/**
 * Difficulty levels controlling damage scaling.
 * Separate from GameMode — difficulty affects HOW HARD the game is,
 * while GameMode affects HOW YOU PLAY (creative vs survival).
 *
 * PEACEFUL — 0× mob damage, no hostile spawns (fall damage still applies at 0.5×)
 * EASY     — 0.5× damage multiplier
 * NORMAL   — 1× damage multiplier (default)
 * HARD     — 1.5× damage multiplier
 */
public enum Difficulty {

    PEACEFUL(0.0f, 0.5f),
    EASY    (0.5f, 0.5f),
    NORMAL  (1.0f, 1.0f),
    HARD    (1.5f, 1.5f);

    /** Multiplier for mob/hostile damage. */
    private final float mobDamageMultiplier;
    /** Multiplier for environmental damage (fall, void, etc.). */
    private final float envDamageMultiplier;

    Difficulty(float mobDamageMultiplier, float envDamageMultiplier) {
        this.mobDamageMultiplier = mobDamageMultiplier;
        this.envDamageMultiplier = envDamageMultiplier;
    }

    /** Damage multiplier for mob/hostile sources. */
    public float getMobDamageMultiplier() { return mobDamageMultiplier; }

    /** Damage multiplier for environmental sources (fall, void). */
    public float getEnvDamageMultiplier() { return envDamageMultiplier; }

    /**
     * Get the appropriate damage multiplier for a given damage source.
     */
    public float getDamageMultiplier(DamageSource source) {
        return switch (source) {
            case MOB -> mobDamageMultiplier;
            case FALL, VOID, GENERIC -> 1.0f; // Environmental damage never scales
        };
    }

    /** Cycle to the next difficulty (wraps around). */
    public Difficulty next() {
        Difficulty[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Safe parse from string — defaults to NORMAL if unrecognized. */
    public static Difficulty fromString(String s) {
        if (s == null) return NORMAL;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
