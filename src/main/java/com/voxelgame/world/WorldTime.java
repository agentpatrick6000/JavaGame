package com.voxelgame.world;

/**
 * In-game time tracking. Manages day/night cycle.
 *
 * One full day = 24000 ticks.
 * Total cycle = 17 real minutes (10 min day + 7 min night) = 1020 seconds.
 * Tick rate = 24000 / 1020 ≈ 23.53 ticks/sec.
 *
 * Time of day ranges:
 *   0-1000    sunrise / dawn
 *   1000-12000  daytime
 *   12000-13000 sunset / dusk
 *   13000-23000 nighttime
 *   23000-24000 pre-dawn
 *
 * World starts at tick 6000 (mid-morning).
 */
public class WorldTime {

    // ---- Constants ----
    public static final int DAY_LENGTH       = 24000;

    /** Real-world seconds for one full day/night cycle (10 min day + 7 min night). */
    public static final float CYCLE_SECONDS  = 1020.0f; // 17 minutes

    /** Tick rate derived from cycle length. */
    public static final float TICKS_PER_SECOND = DAY_LENGTH / CYCLE_SECONDS; // ~23.53

    // ---- Time ranges ----
    public static final int SUNRISE_START = 0;
    public static final int DAY_START     = 1000;
    public static final int SUNSET_START  = 12000;
    public static final int NIGHT_START   = 13000;
    public static final int DAWN_START    = 23000;

    // ---- State ----
    private long worldTick = 6000; // start at mid-morning
    private float tickAccumulator = 0;

    /**
     * Advance world time based on real elapsed time.
     */
    public void update(float dt) {
        tickAccumulator += dt * TICKS_PER_SECOND;
        int ticks = (int) tickAccumulator;
        if (ticks > 0) {
            worldTick += ticks;
            tickAccumulator -= ticks;
        }
    }

    /** Get the absolute world tick (increases forever). */
    public long getWorldTick() { return worldTick; }

    /** Get time of day (0-23999). */
    public int getTimeOfDay() { return (int) (worldTick % DAY_LENGTH); }

    /** Get current day number (starting from 0). */
    public int getDayNumber() { return (int) (worldTick / DAY_LENGTH); }

    /**
     * Is it currently daytime? (sunrise through sunset)
     */
    public boolean isDay() {
        int tod = getTimeOfDay();
        return tod >= SUNRISE_START && tod < SUNSET_START;
    }

    /**
     * Is it currently nighttime? (sunset through dawn)
     */
    public boolean isNight() {
        int tod = getTimeOfDay();
        return tod >= NIGHT_START || tod < SUNRISE_START;
    }

    /**
     * Get sun angle in degrees (0 = horizon east, 90 = overhead, 180 = horizon west).
     * Used for celestial rendering.
     */
    public float getSunAngle() {
        int tod = getTimeOfDay();
        // Map day portion (0-12000) to 0-180 degrees
        // Map night portion (12000-24000) to 180-360 degrees
        return (tod / (float) DAY_LENGTH) * 360.0f;
    }

    /**
     * Get sun brightness factor (0.0 = full night, 1.0 = full day).
     * Smooth transitions during sunrise/sunset.
     *
     * Night is DARK. Moon provides only minimal light (level 4 out of 15 ≈ 0.07).
     * This makes torches essential at night.
     */
    public float getSunBrightness() {
        int tod = getTimeOfDay();

        if (tod >= DAY_START && tod < SUNSET_START) {
            // Full daylight
            return 1.0f;
        } else if (tod >= NIGHT_START && tod < DAWN_START) {
            // Full night — moonlight only (very dim)
            // Moon provides light level ~4/15 = 0.27, but we want it darker
            // than that since this modulates the skylight which is already 0-1.
            // At 0.05, a sky-lit surface gets 0.05 brightness = very dark.
            return 0.05f;
        } else if (tod < DAY_START) {
            // Sunrise transition (0-1000)
            float t = tod / (float) DAY_START;
            // Smooth ease: use smoothstep for natural sunrise
            t = smoothstep(t);
            return 0.05f + 0.95f * t;
        } else if (tod < NIGHT_START) {
            // Sunset transition (12000-13000)
            float t = (tod - SUNSET_START) / (float) (NIGHT_START - SUNSET_START);
            t = smoothstep(t);
            return 1.0f - 0.95f * t;
        } else {
            // Dawn transition (23000-24000)
            float t = (tod - DAWN_START) / (float) (DAY_LENGTH - DAWN_START);
            t = smoothstep(t);
            return 0.05f + 0.95f * t;
        }
    }

    /**
     * Get sky color multiplier for fog/clear color.
     * Returns RGB multipliers for the sky.
     */
    public float[] getSkyColor() {
        float brightness = getSunBrightness();
        if (brightness > 0.5f) {
            // Day: bright blue sky
            float t = (brightness - 0.5f) * 2.0f;
            return new float[]{
                lerp(0.15f, 0.53f, t),
                lerp(0.15f, 0.68f, t),
                lerp(0.25f, 0.90f, t)
            };
        } else {
            // Night: dark blue-black
            float t = brightness * 2.0f;
            return new float[]{
                lerp(0.01f, 0.15f, t),
                lerp(0.01f, 0.15f, t),
                lerp(0.03f, 0.25f, t)
            };
        }
    }

    /** Smooth hermite interpolation (smoothstep). */
    private static float smoothstep(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    /** Linear interpolation. */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Get a human-readable time string (HH:MM format).
     * 0 ticks = 6:00 AM (Minecraft convention).
     */
    public String getTimeString() {
        int tod = getTimeOfDay();
        // Convert ticks to minutes: 24000 ticks = 1440 minutes (24 hours)
        int totalMinutes = (int) (tod / (24000.0 / 1440.0));
        // Offset by 6 hours (Minecraft: tick 0 = 6:00 AM)
        totalMinutes = (totalMinutes + 360) % 1440;
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    /** Set world tick (for save/load). */
    public void setWorldTick(long tick) {
        this.worldTick = tick;
    }
}
