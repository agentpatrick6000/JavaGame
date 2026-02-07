package com.voxelgame.core;

/**
 * Defines a capture profile for debug/spawn captures.
 * Each profile specifies rendering overrides for A/B testing.
 */
public class CaptureProfile {
    
    public enum ProfileType {
        BEFORE,           // Baseline: all effects OFF
        AFTER_FOG,        // Fog enabled, exposure default
        AFTER_EXPOSURE,   // Fog + exposure tuned
        SPAWN_BEFORE,     // Spawn validation baseline
        SPAWN_AFTER       // Spawn validation with effects
    }
    
    private final ProfileType type;
    private final String name;
    
    // Fog settings
    private final boolean fogEnabled;
    private final float fogStart;
    private final float fogEnd;
    private final boolean heightFogEnabled;
    private final float heightFogCoeff;
    
    // Exposure/tonemap settings
    private final float exposureMultiplier;
    private final float saturationMultiplier;
    private final boolean tonemapEnabled;
    private final float gamma;
    private final boolean srgbEnabled;
    
    // SSAO
    private final boolean ssaoEnabled;
    
    private CaptureProfile(ProfileType type, String name,
                           boolean fogEnabled, float fogStart, float fogEnd,
                           boolean heightFogEnabled, float heightFogCoeff,
                           float exposureMultiplier, float saturationMultiplier,
                           boolean tonemapEnabled, float gamma, boolean srgbEnabled,
                           boolean ssaoEnabled) {
        this.type = type;
        this.name = name;
        this.fogEnabled = fogEnabled;
        this.fogStart = fogStart;
        this.fogEnd = fogEnd;
        this.heightFogEnabled = heightFogEnabled;
        this.heightFogCoeff = heightFogCoeff;
        this.exposureMultiplier = exposureMultiplier;
        this.saturationMultiplier = saturationMultiplier;
        this.tonemapEnabled = tonemapEnabled;
        this.gamma = gamma;
        this.srgbEnabled = srgbEnabled;
        this.ssaoEnabled = ssaoEnabled;
    }
    
    public static CaptureProfile create(ProfileType type) {
        return switch (type) {
            case BEFORE -> new CaptureProfile(
                type, "BEFORE",
                false, 80f, 128f,    // fog disabled
                false, 0f,           // no height fog
                1.0f, 1.0f,          // neutral exposure/saturation
                false, 2.2f, false,  // no tonemap, manual gamma
                false                // no SSAO
            );
            case AFTER_FOG -> new CaptureProfile(
                type, "AFTER_FOG",
                true, 80f, 192f,     // fog enabled, tuned distances
                false, 0f,           // no height fog (removed per earlier fix)
                1.0f, 1.0f,          // neutral exposure/saturation
                false, 2.2f, false,  // no tonemap yet
                false                // no SSAO yet
            );
            case AFTER_EXPOSURE -> new CaptureProfile(
                type, "AFTER_EXPOSURE",
                true, 80f, 192f,     // fog enabled
                false, 0f,           // no height fog
                1.4f, 1.35f,         // tuned exposure/saturation
                true, 2.2f, false,   // tonemap ON
                true                 // SSAO ON
            );
            case SPAWN_BEFORE -> new CaptureProfile(
                type, "BEFORE",
                false, 80f, 128f,
                false, 0f,
                1.0f, 1.0f,
                false, 2.2f, false,
                false
            );
            case SPAWN_AFTER -> new CaptureProfile(
                type, "AFTER_SPAWN",
                true, 80f, 192f,
                false, 0f,
                1.4f, 1.35f,
                true, 2.2f, false,
                true
            );
        };
    }
    
    public static CaptureProfile fromString(String name) {
        return switch (name.toUpperCase()) {
            case "BEFORE" -> create(ProfileType.BEFORE);
            case "AFTER_FOG" -> create(ProfileType.AFTER_FOG);
            case "AFTER_EXPOSURE" -> create(ProfileType.AFTER_EXPOSURE);
            case "SPAWN_BEFORE" -> create(ProfileType.SPAWN_BEFORE);
            case "SPAWN_AFTER" -> create(ProfileType.SPAWN_AFTER);
            default -> throw new IllegalArgumentException("Unknown profile: " + name);
        };
    }
    
    // Getters
    public ProfileType getType() { return type; }
    public String getName() { return name; }
    public boolean isFogEnabled() { return fogEnabled; }
    public float getFogStart() { return fogStart; }
    public float getFogEnd() { return fogEnd; }
    public boolean isHeightFogEnabled() { return heightFogEnabled; }
    public float getHeightFogCoeff() { return heightFogCoeff; }
    public float getExposureMultiplier() { return exposureMultiplier; }
    public float getSaturationMultiplier() { return saturationMultiplier; }
    public boolean isTonemapEnabled() { return tonemapEnabled; }
    public float getGamma() { return gamma; }
    public boolean isSrgbEnabled() { return srgbEnabled; }
    public boolean isSsaoEnabled() { return ssaoEnabled; }
    
    public String getFolderName() {
        return "PROFILE_" + name;
    }
    
    /**
     * Generate JSON fragment for profile_overrides section.
     */
    public String toOverridesJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"fog_enabled\": ").append(fogEnabled).append(",\n");
        sb.append("    \"fog_start\": ").append(fogStart).append(",\n");
        sb.append("    \"fog_end\": ").append(fogEnd).append(",\n");
        sb.append("    \"height_fog_enabled\": ").append(heightFogEnabled).append(",\n");
        sb.append("    \"height_fog_coeff\": ").append(heightFogCoeff).append(",\n");
        sb.append("    \"exposure_multiplier\": ").append(exposureMultiplier).append(",\n");
        sb.append("    \"saturation_multiplier\": ").append(saturationMultiplier).append(",\n");
        sb.append("    \"tonemap_enabled\": ").append(tonemapEnabled).append(",\n");
        sb.append("    \"gamma\": ").append(gamma).append(",\n");
        sb.append("    \"srgb_enabled\": ").append(srgbEnabled).append(",\n");
        sb.append("    \"ssao_enabled\": ").append(ssaoEnabled).append("\n");
        sb.append("  }");
        return sb.toString();
    }
}
