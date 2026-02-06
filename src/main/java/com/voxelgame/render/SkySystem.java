package com.voxelgame.render;

/**
 * Sky System - Phase 2 implementation.
 * 
 * Provides zenith/horizon color split, time-of-day intensity curves,
 * and sun direction/color for the unified lighting model.
 * 
 * Time of day uses 0-1 range: 0 = midnight, 0.25 = sunrise, 0.5 = noon, 0.75 = sunset.
 * This differs from WorldTime ticks (0-24000) for easier curve math.
 * 
 * Key design decisions:
 * - Smooth piecewise curves (not linear) for natural color transitions
 * - Aggressive intensity drop at night (0.02) to make torches essential
 * - Zenith/horizon split for atmospheric depth (overhangs get horizon tint)
 */
public class SkySystem {

    // ========================================================================
    // TIME OF DAY CONSTANTS
    // ========================================================================
    
    /** Sunrise center point (6:00 AM in 0-1 scale) */
    private static final float T_SUNRISE = 0.25f;
    /** Noon (12:00 PM) */
    private static final float T_NOON = 0.5f;
    /** Sunset center point (6:00 PM) */
    private static final float T_SUNSET = 0.75f;
    /** Midnight */
    private static final float T_MIDNIGHT = 0.0f;

    /** Duration of sunrise/sunset transition (2 hours in game time) */
    private static final float TRANSITION_WIDTH = 2.0f / 24.0f; // ~0.083

    // ========================================================================
    // ZENITH COLORS (overhead sky)
    // ========================================================================
    
    // Daytime zenith: deep blue
    private static final float[] ZENITH_DAY = {0.30f, 0.50f, 0.90f};
    // Sunset zenith: blue-purple
    private static final float[] ZENITH_SUNSET = {0.40f, 0.30f, 0.60f};
    // Night zenith: very dark blue
    private static final float[] ZENITH_NIGHT = {0.02f, 0.02f, 0.08f};
    // Sunrise zenith: purple-blue
    private static final float[] ZENITH_SUNRISE = {0.35f, 0.25f, 0.55f};

    // ========================================================================
    // HORIZON COLORS (near ground level)
    // ========================================================================
    
    // Daytime horizon: light blue/white
    private static final float[] HORIZON_DAY = {0.60f, 0.80f, 1.00f};
    // Sunset horizon: warm orange/red
    private static final float[] HORIZON_SUNSET = {1.00f, 0.50f, 0.20f};
    // Night horizon: slightly lighter than zenith
    private static final float[] HORIZON_NIGHT = {0.05f, 0.05f, 0.10f};
    // Sunrise horizon: warm orange/pink
    private static final float[] HORIZON_SUNRISE = {0.95f, 0.55f, 0.30f};

    // ========================================================================
    // SUN COLORS
    // ========================================================================
    
    // Midday sun: warm white
    private static final float[] SUN_DAY = {1.00f, 0.98f, 0.90f};
    // Sunset sun: deep orange
    private static final float[] SUN_SUNSET = {1.00f, 0.40f, 0.10f};
    // Night: no sun (moon doesn't cast directional light in this model)
    private static final float[] SUN_NIGHT = {0.00f, 0.00f, 0.00f};
    // Sunrise sun: warm yellow-orange
    private static final float[] SUN_SUNRISE = {1.00f, 0.70f, 0.30f};

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Get zenith (overhead) sky color at the given time of day.
     * 
     * @param timeOfDay 0-1 where 0 = midnight, 0.5 = noon
     * @return RGB color components (0-1 each)
     */
    public float[] getZenithColor(float timeOfDay) {
        float t = normalizeTime(timeOfDay);
        return interpolateColor(t,
            ZENITH_NIGHT, ZENITH_SUNRISE, ZENITH_DAY, ZENITH_SUNSET);
    }

    /**
     * Get horizon (near ground) sky color at the given time of day.
     * 
     * @param timeOfDay 0-1 where 0 = midnight, 0.5 = noon
     * @return RGB color components (0-1 each)
     */
    public float[] getHorizonColor(float timeOfDay) {
        float t = normalizeTime(timeOfDay);
        return interpolateColor(t,
            HORIZON_NIGHT, HORIZON_SUNRISE, HORIZON_DAY, HORIZON_SUNSET);
    }

    /**
     * Get overall sky intensity multiplier.
     * Uses smooth curves for natural transitions.
     * Night intensity is very low (0.02) to make caves/shade genuinely dark.
     * 
     * @param timeOfDay 0-1 where 0 = midnight, 0.5 = noon
     * @return Intensity multiplier (0.02 - 1.0)
     */
    public float getSkyIntensity(float timeOfDay) {
        float t = normalizeTime(timeOfDay);
        
        // Distance from noon (0.5)
        // At noon: distFromNoon = 0
        // At midnight: distFromNoon = 0.5
        float distFromNoon = Math.abs(t - T_NOON);
        
        // Use smoothed cosine curve for natural falloff
        // cos(0) = 1 (noon = full brightness)
        // cos(π) = -1 (midnight = minimum brightness)
        float cosAngle = (float) Math.cos(distFromNoon * Math.PI / 0.5);
        
        // Map from [-1, 1] to [MIN_INTENSITY, 1.0]
        float MIN_INTENSITY = 0.02f;
        float intensity = MIN_INTENSITY + (1.0f - MIN_INTENSITY) * (cosAngle + 1.0f) * 0.5f;
        
        // Extra dampening during transition periods (sunrise/sunset)
        // This makes the drop from day to night more dramatic
        float sunriseWeight = transitionWeight(t, T_SUNRISE, TRANSITION_WIDTH);
        float sunsetWeight = transitionWeight(t, T_SUNSET, TRANSITION_WIDTH);
        float transitionDampen = 1.0f - 0.3f * Math.max(sunriseWeight, sunsetWeight);
        
        return intensity * transitionDampen;
    }

    /**
     * Get sun directional light intensity.
     * Drops to 0 faster than sky intensity (sun sets before sky goes completely dark).
     * 
     * @param timeOfDay 0-1 where 0 = midnight, 0.5 = noon
     * @return Sun intensity (0.0 - 1.0)
     */
    public float getSunIntensity(float timeOfDay) {
        float t = normalizeTime(timeOfDay);
        
        // Sun is above horizon roughly from 0.2 to 0.8
        float sunriseStart = T_SUNRISE - TRANSITION_WIDTH;
        float sunsetEnd = T_SUNSET + TRANSITION_WIDTH;
        
        if (t < sunriseStart || t > sunsetEnd) {
            return 0.0f;
        }
        
        // Smooth ramp up during sunrise
        if (t < T_SUNRISE + TRANSITION_WIDTH) {
            float rise = smoothstep(sunriseStart, T_SUNRISE + TRANSITION_WIDTH, t);
            return rise;
        }
        
        // Smooth ramp down during sunset
        if (t > T_SUNSET - TRANSITION_WIDTH) {
            float set = smoothstep(sunsetEnd, T_SUNSET - TRANSITION_WIDTH, t);
            return set;
        }
        
        // Full intensity during day
        return 1.0f;
    }

    /**
     * Get sun direction vector (normalized).
     * Sun rises in east (+X), peaks at noon (directly up), sets in west (-X).
     * 
     * @param timeOfDay 0-1 where 0 = midnight, 0.5 = noon
     * @return Direction vector (x, y, z) pointing toward sun
     */
    public float[] getSunDirection(float timeOfDay) {
        float t = normalizeTime(timeOfDay);
        
        // Map time to angle around the sky
        // t = 0.25 (sunrise): sun at horizon east (+X, Y=0)
        // t = 0.5 (noon): sun at zenith (Y=1)
        // t = 0.75 (sunset): sun at horizon west (-X, Y=0)
        // t = 0 or 1 (midnight): sun below horizon (Y<0)
        
        // Angle from midnight (radians)
        float angle = t * 2.0f * (float) Math.PI;
        
        // Y component: sin curve, peaks at noon
        // At t=0.25: sin(π/2) = 1? No, we want Y=0 at sunrise
        // Shift by π/2: at t=0.25, angle = π/2, sin(π/2 - π/2) = 0
        float y = (float) Math.sin(angle - Math.PI * 0.5);
        
        // X component: cosine, positive in morning (east), negative in evening (west)
        // At t=0.25: x should be positive (east)
        // At t=0.5: x should be 0 (overhead)
        // At t=0.75: x should be negative (west)
        float x = -(float) Math.cos(angle - Math.PI * 0.5);
        
        // Z component: slight north-south variation for realism (0 for simplicity)
        float z = 0.0f;
        
        // Normalize
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len > 0.001f) {
            x /= len;
            y /= len;
            z /= len;
        }
        
        return new float[]{x, y, z};
    }

    /**
     * Get sun color at the given time of day.
     * Warm white at noon, deep orange at sunset/sunrise.
     * 
     * @param timeOfDay 0-1 where 0 = midnight, 0.5 = noon
     * @return RGB color components (0-1 each)
     */
    public float[] getSunColor(float timeOfDay) {
        float t = normalizeTime(timeOfDay);
        return interpolateColor(t,
            SUN_NIGHT, SUN_SUNRISE, SUN_DAY, SUN_SUNSET);
    }

    /**
     * Convert WorldTime ticks (0-24000) to normalized time (0-1).
     * WorldTime: 0 = 6:00 AM, 6000 = noon, 12000 = 6:00 PM, 18000 = midnight
     * 
     * @param worldTicks Current world time in ticks
     * @return Normalized time 0-1 where 0 = midnight, 0.5 = noon
     */
    public static float worldTimeToNormalized(long worldTicks) {
        // WorldTime tick ranges:
        // 0-1000: sunrise (6-7 AM)
        // 1000-12000: day (7 AM - 6 PM)
        // 12000-13000: sunset (6-7 PM)
        // 13000-23000: night (7 PM - 5 AM)
        // 23000-24000: dawn (5-6 AM)
        
        int tod = (int) (worldTicks % 24000);
        
        // Convert Minecraft-style time to normalized 0-1 where:
        // 0 = midnight
        // 0.25 = sunrise (6 AM)
        // 0.5 = noon
        // 0.75 = sunset (6 PM)
        
        // Minecraft: tick 0 = 6 AM = 0.25 normalized
        // So we add 0.25 and wrap
        float mcNormalized = tod / 24000.0f;
        float normalized = (mcNormalized + 0.25f) % 1.0f;
        
        return normalized;
    }

    /**
     * Get fog color (for distance fog/clear color).
     * Uses a blend of zenith and horizon weighted toward horizon.
     * 
     * @param timeOfDay Normalized time 0-1
     * @return RGB fog color
     */
    public float[] getFogColor(float timeOfDay) {
        float[] zenith = getZenithColor(timeOfDay);
        float[] horizon = getHorizonColor(timeOfDay);
        
        // Fog is mostly horizon-colored (what you see looking toward distance)
        float horizonWeight = 0.7f;
        return new float[]{
            lerp(zenith[0], horizon[0], horizonWeight),
            lerp(zenith[1], horizon[1], horizonWeight),
            lerp(zenith[2], horizon[2], horizonWeight)
        };
    }

    // ========================================================================
    // INTERPOLATION HELPERS
    // ========================================================================

    /**
     * Ensure time is in 0-1 range.
     */
    private float normalizeTime(float t) {
        t = t % 1.0f;
        if (t < 0) t += 1.0f;
        return t;
    }

    /**
     * Interpolate between 4 colors around the day cycle.
     * Colors are for: midnight, sunrise, noon, sunset.
     * Uses smooth hermite interpolation within each quarter.
     */
    private float[] interpolateColor(float t, float[] midnight, float[] sunrise,
                                     float[] noon, float[] sunset) {
        // Determine which quarter of the day we're in
        if (t < T_SUNRISE) {
            // Midnight to sunrise (0.0 - 0.25)
            float localT = t / T_SUNRISE;
            localT = smoothstep(localT);
            return lerpColor(midnight, sunrise, localT);
        } else if (t < T_NOON) {
            // Sunrise to noon (0.25 - 0.5)
            float localT = (t - T_SUNRISE) / (T_NOON - T_SUNRISE);
            localT = smoothstep(localT);
            return lerpColor(sunrise, noon, localT);
        } else if (t < T_SUNSET) {
            // Noon to sunset (0.5 - 0.75)
            float localT = (t - T_NOON) / (T_SUNSET - T_NOON);
            localT = smoothstep(localT);
            return lerpColor(noon, sunset, localT);
        } else {
            // Sunset to midnight (0.75 - 1.0)
            float localT = (t - T_SUNSET) / (1.0f - T_SUNSET);
            localT = smoothstep(localT);
            return lerpColor(sunset, midnight, localT);
        }
    }

    /**
     * Linearly interpolate between two colors.
     */
    private float[] lerpColor(float[] a, float[] b, float t) {
        return new float[]{
            lerp(a[0], b[0], t),
            lerp(a[1], b[1], t),
            lerp(a[2], b[2], t)
        };
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Smooth hermite interpolation (smoothstep).
     * Gives natural-looking transitions without harsh edges.
     */
    private float smoothstep(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Smooth interpolation between two values.
     */
    private float smoothstep(float edge0, float edge1, float x) {
        float t = (x - edge0) / (edge1 - edge0);
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Compute how "in transition" we are near a specific time point.
     * Returns 1.0 when exactly at the transition point, 0.0 when far away.
     */
    private float transitionWeight(float t, float center, float width) {
        float dist = Math.abs(t - center);
        if (dist > width) return 0.0f;
        return 1.0f - dist / width;
    }
}
