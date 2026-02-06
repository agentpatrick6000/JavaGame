#version 330 core
in vec2 vTexCoord;
in float vSkyVisibility;   // 0-1 sky visibility (with AO and directional baked in)
in vec3 vBlockLightRGB;    // Phase 4: RGB block light (colored torch/lava/etc.)
in float vHorizonWeight;   // 0-1 how much horizon vs zenith is visible (Phase 2)
in vec3 vIndirectRGB;      // Phase 3: indirect lighting from probes
in float vFogFactor;
in vec3 vViewPos;
in vec3 vWorldPos;

uniform sampler2D uAtlas;
uniform float uAlpha;           // 1.0 for opaque pass, <1.0 for transparent pass
uniform vec3 uFogColor;         // sky/fog color (matches clear color)

// Phase 2 Sky System uniforms - zenith/horizon color split
uniform vec3 uSkyZenithColor;   // Overhead sky color (deep blue at noon, dark at night)
uniform vec3 uSkyHorizonColor;  // Horizon sky color (lighter blue at noon, orange at sunset)
uniform float uSkyIntensity;    // Overall sky intensity (1.0 at noon, 0.02 at midnight)

// Sun uniforms
uniform vec3 uSunDirection;     // Direction TO sun (normalized)
uniform vec3 uSunColor;         // Sun color (warm white at noon, orange at sunset)
uniform float uSunIntensity;    // Sun intensity (1.0 at noon, 0.0 at night)

// Phase 4: Time uniform for torch flicker
uniform float uTime;            // Game time in seconds (for flicker animation)

// Legacy uniforms (kept for backward compatibility)
uniform vec3 uSkyColor;         // Fog/ambient sky color
uniform float uSunBrightness;   // Overall sun brightness (0-1)

// Minimum ambient light (starlight) so caves aren't completely pitch black
const float MIN_AMBIENT = 0.015;

// Phase 3: Indirect light strength (controls how much probe bounce contributes)
const float INDIRECT_STRENGTH = 0.5;

// Phase 4: Legacy warm color for backward compatibility (when G/B = 0)
const vec3 LEGACY_BLOCK_LIGHT_COLOR = vec3(1.0, 0.9, 0.7);

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragNormal; // View-space normals for SSAO (PostFX FBO)

// Phase 4: Subtle flicker function for torch-lit surfaces
// Uses procedural noise based on world position + time
float flickerAmount(vec3 worldPos) {
    // Multiple frequencies for organic feel
    float noise1 = fract(sin(dot(worldPos.xz, vec2(12.9898, 78.233)) + uTime * 4.0) * 43758.5453);
    float noise2 = fract(sin(dot(worldPos.xz, vec2(93.9898, 67.345)) + uTime * 7.0) * 28461.8742);
    float combined = noise1 * 0.6 + noise2 * 0.4;
    // Subtle flicker: 0.92 - 1.0 range (8% variation max)
    return 0.92 + 0.08 * combined;
}

void main() {
    vec4 texColor = texture(uAtlas, vTexCoord);
    if (texColor.a < 0.1) discard;

    // Compute world-space normal from position derivatives (flat shading per face)
    vec3 worldNormal = normalize(cross(dFdx(vWorldPos), dFdy(vWorldPos)));

    // ========================================================================
    // PHASE 4 UNIFIED RGB LIGHTING MODEL
    // ========================================================================
    
    // 1. SKY LIGHT: Mix zenith and horizon colors based on what's visible
    //    Under overhangs: horizonWeight = 1.0 → mostly horizon color (warm at sunset)
    //    Open sky: horizonWeight = 0.3 → mix of both (natural balance)
    vec3 skyColor = mix(uSkyZenithColor, uSkyHorizonColor, vHorizonWeight);
    vec3 skyRGB = skyColor * vSkyVisibility * uSkyIntensity;
    
    // 2. SUN LIGHT: Directional sunlight (N·L), only for sky-visible surfaces
    //    Surfaces in caves (visibility=0) don't get any sun contribution
    //    Sun color changes with time of day (warm white → orange → off)
    float NdotL = max(dot(worldNormal, uSunDirection), 0.0);
    vec3 sunRGB = uSunColor * NdotL * uSunIntensity * vSkyVisibility * 0.6;
    
    // 3. BLOCK LIGHT: Phase 4 RGB light from colored sources
    //    Torches = warm orange, lava = deep red, redstone = dim red
    vec3 blockRGB = vBlockLightRGB;
    
    // Legacy compatibility: if G and B are 0 but R > 0, apply warm color
    // This handles old meshes that haven't been regenerated yet
    if (vBlockLightRGB.g < 0.001 && vBlockLightRGB.b < 0.001 && vBlockLightRGB.r > 0.001) {
        blockRGB = LEGACY_BLOCK_LIGHT_COLOR * vBlockLightRGB.r;
    }
    
    // Apply subtle flicker for torch-lit surfaces
    // Only flicker if there's significant block light (avoids flickering dark areas)
    if (length(blockRGB) > 0.05) {
        blockRGB *= flickerAmount(vWorldPos);
    }
    
    // 4. INDIRECT LIGHT (Phase 3): One-bounce GI from irradiance probes
    //    This adds subtle color bleed from nearby lit surfaces into shadows
    //    - Under trees: slight green tint from grass
    //    - Near torches: warm bounce on nearby walls
    //    - Under mountains at sunset: faint warm tones from horizon light
    vec3 indirectRGB = vIndirectRGB * INDIRECT_STRENGTH;
    
    // 5. ADDITIVE COMBINATION (not max!)
    //    This allows torches to add to moonlight, multiple light sources to stack
    vec3 totalLight = skyRGB + sunRGB + blockRGB + indirectRGB;
    
    // 6. Minimum ambient (starlight) so you can see something in caves
    //    Very dim so torches are still essential
    totalLight = max(totalLight, vec3(MIN_AMBIENT));
    
    // ========================================================================
    // NIGHT ATMOSPHERE: Subtle blue tint for moonlight feel
    // ========================================================================
    // When sky intensity is low (night), add a subtle blue tint to the lighting
    // This simulates the way our eyes perceive colors at night (rod cells)
    // But only for sky-lit surfaces — torch-lit areas stay warm
    if (uSkyIntensity < 0.3) {
        float nightFactor = 1.0 - uSkyIntensity / 0.3; // 0 at dusk, 1 at full night
        // Shift toward blue, but don't overpower torch light or indirect light
        float blockInfluence = length(blockRGB);
        float blendStrength = nightFactor * 0.3 * (1.0 - min(blockInfluence, 1.0)) * (1.0 - length(indirectRGB));
        totalLight = mix(totalLight, totalLight * vec3(0.7, 0.75, 1.0), blendStrength);
    }

    // Apply lighting to texture
    vec3 litColor = texColor.rgb * totalLight;

    // Apply distance fog (blends to sky color, hides chunk pop-in)
    vec3 finalColor = mix(litColor, uFogColor, vFogFactor);

    fragColor = vec4(finalColor, texColor.a * uAlpha);

    // Compute view-space normal from screen-space position derivatives.
    // This gives flat per-face normals for block geometry — exactly what SSAO needs.
    vec3 viewNormal = normalize(cross(dFdx(vViewPos), dFdy(vViewPos)));
    // Encode from [-1,1] to [0,1] for texture storage
    fragNormal = vec4(viewNormal * 0.5 + 0.5, 1.0);
}
