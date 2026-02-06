#version 330 core

// Smooth (interpolated) inputs
in vec2 vTexCoord;
in float vSkyVisibility;   // 0-1 sky visibility (with AO and directional baked in)
in vec3 vBlockLightRGB;    // Phase 4: RGB block light (colored torch/lava/etc.)
in float vHorizonWeight;   // 0-1 how much horizon vs zenith is visible (Phase 2)
in vec3 vIndirectRGB;      // Phase 3: indirect lighting from probes
in float vFogFactor;
in vec3 vViewPos;
in vec3 vWorldPos;

// Phase 6: Flat (non-interpolated) inputs for sharp lighting mode
flat in float fSkyVisibility;
flat in vec3 fBlockLightRGB;
flat in float fHorizonWeight;
flat in vec3 fIndirectRGB;

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

// Phase 6: Smooth lighting toggle (1 = smooth interpolated, 0 = flat per-face)
uniform int uSmoothLighting;

// Legacy uniforms (kept for backward compatibility)
uniform vec3 uSkyColor;         // Fog/ambient sky color
uniform float uSunBrightness;   // Overall sun brightness (0-1)

// Phase 5: Shadow map uniforms
uniform sampler2DShadow uShadowMap0;  // Near cascade (0-16 blocks)
uniform sampler2DShadow uShadowMap1;  // Mid cascade (16-64 blocks)
uniform sampler2DShadow uShadowMap2;  // Far cascade (64-256 blocks)
uniform mat4 uLightViewProj0;         // Light-space matrix for cascade 0
uniform mat4 uLightViewProj1;         // Light-space matrix for cascade 1
uniform mat4 uLightViewProj2;         // Light-space matrix for cascade 2
uniform vec3 uCameraPos;              // Camera position for cascade selection
uniform int uShadowsEnabled;          // Whether shadows are enabled (0 = disabled at night)

// Minimum ambient light (starlight) so caves aren't completely pitch black
const float MIN_AMBIENT = 0.015;

// Phase 3: Indirect light strength (controls how much probe bounce contributes)
const float INDIRECT_STRENGTH = 0.5;

// Phase 4: Legacy warm color for backward compatibility (when G/B = 0)
const vec3 LEGACY_BLOCK_LIGHT_COLOR = vec3(1.0, 0.9, 0.7);

// Phase 5: Cascade split distances (must match ShadowRenderer.java)
const float CASCADE_SPLIT_0 = 16.0;
const float CASCADE_SPLIT_1 = 64.0;
const float CASCADE_SPLIT_2 = 256.0;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragNormal; // View-space normals for SSAO (PostFX FBO)

// Phase 5: Sample shadow map with 3x3 PCF for soft shadows
float sampleShadowPCF(sampler2DShadow shadowMap, vec3 projCoords) {
    float shadow = 0.0;
    vec2 texelSize = 1.0 / vec2(2048.0); // Shadow map size
    
    // 3x3 PCF kernel
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec3 offset = vec3(vec2(x, y) * texelSize, 0.0);
            shadow += texture(shadowMap, projCoords + offset);
        }
    }
    return shadow / 9.0;
}

// Phase 5: Calculate shadow factor (1.0 = fully lit, 0.0 = fully in shadow)
float calculateShadow(vec3 worldPos, vec3 worldNormal) {
    if (uShadowsEnabled == 0) return 1.0;
    
    // Skip shadow for surfaces facing away from sun
    float NdotL = dot(worldNormal, uSunDirection);
    if (NdotL < 0.0) return 0.0; // Back-facing = self-shadowed
    
    // Distance from camera to select cascade
    float dist = length(worldPos - uCameraPos);
    
    // Select cascade and corresponding matrix/texture
    mat4 lightVP;
    vec3 projCoords;
    float shadow;
    
    // Slope-based bias to reduce shadow acne (steeper angles need more bias)
    float bias = max(0.005 * (1.0 - NdotL), 0.001);
    
    if (dist < CASCADE_SPLIT_0) {
        // Near cascade - highest detail
        vec4 lightSpacePos = uLightViewProj0 * vec4(worldPos, 1.0);
        projCoords = lightSpacePos.xyz / lightSpacePos.w;
        projCoords = projCoords * 0.5 + 0.5;
        if (projCoords.z > 1.0 || projCoords.x < 0.0 || projCoords.x > 1.0 || 
            projCoords.y < 0.0 || projCoords.y > 1.0) return 1.0;
        projCoords.z -= bias;
        shadow = sampleShadowPCF(uShadowMap0, projCoords);
    }
    else if (dist < CASCADE_SPLIT_1) {
        // Mid cascade
        vec4 lightSpacePos = uLightViewProj1 * vec4(worldPos, 1.0);
        projCoords = lightSpacePos.xyz / lightSpacePos.w;
        projCoords = projCoords * 0.5 + 0.5;
        if (projCoords.z > 1.0 || projCoords.x < 0.0 || projCoords.x > 1.0 || 
            projCoords.y < 0.0 || projCoords.y > 1.0) return 1.0;
        projCoords.z -= bias;
        shadow = sampleShadowPCF(uShadowMap1, projCoords);
    }
    else if (dist < CASCADE_SPLIT_2) {
        // Far cascade
        vec4 lightSpacePos = uLightViewProj2 * vec4(worldPos, 1.0);
        projCoords = lightSpacePos.xyz / lightSpacePos.w;
        projCoords = projCoords * 0.5 + 0.5;
        if (projCoords.z > 1.0 || projCoords.x < 0.0 || projCoords.x > 1.0 || 
            projCoords.y < 0.0 || projCoords.y > 1.0) return 1.0;
        projCoords.z -= bias * 2.0; // More bias for far cascade
        shadow = sampleShadowPCF(uShadowMap2, projCoords);
    }
    else {
        // Beyond all cascades - no shadow
        return 1.0;
    }
    
    return shadow;
}

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
    // PHASE 6: SELECT SMOOTH OR FLAT LIGHTING INPUTS
    // ========================================================================
    // Smooth lighting: per-vertex interpolated values (gradients across faces)
    // Sharp lighting: flat per-face values (uniform color per face, classic look)
    
    float skyVis = (uSmoothLighting == 1) ? vSkyVisibility : fSkyVisibility;
    vec3 blockLightIn = (uSmoothLighting == 1) ? vBlockLightRGB : fBlockLightRGB;
    float horizonWt = (uSmoothLighting == 1) ? vHorizonWeight : fHorizonWeight;
    vec3 indirectIn = (uSmoothLighting == 1) ? vIndirectRGB : fIndirectRGB;

    // ========================================================================
    // PHASE 4 UNIFIED RGB LIGHTING MODEL
    // ========================================================================
    
    // 1. SKY LIGHT: Mix zenith and horizon colors based on what's visible
    //    Under overhangs: horizonWeight = 1.0 → mostly horizon color (warm at sunset)
    //    Open sky: horizonWeight = 0.3 → mix of both (natural balance)
    vec3 skyColor = mix(uSkyZenithColor, uSkyHorizonColor, horizonWt);
    vec3 skyRGB = skyColor * skyVis * uSkyIntensity;
    
    // 2. SUN LIGHT: Directional sunlight (N·L), only for sky-visible surfaces
    //    Surfaces in caves (visibility=0) don't get any sun contribution
    //    Sun color changes with time of day (warm white → orange → off)
    //    Phase 5: Apply shadow mapping to sun contribution
    float NdotL = max(dot(worldNormal, uSunDirection), 0.0);
    float shadow = calculateShadow(vWorldPos, worldNormal);
    vec3 sunRGB = uSunColor * NdotL * uSunIntensity * skyVis * shadow * 0.6;
    
    // 3. BLOCK LIGHT: Phase 4 RGB light from colored sources
    //    Torches = warm orange, lava = deep red, redstone = dim red
    vec3 blockRGB = blockLightIn;
    
    // Legacy compatibility: if G and B are 0 but R > 0, apply warm color
    // This handles old meshes that haven't been regenerated yet
    if (blockLightIn.g < 0.001 && blockLightIn.b < 0.001 && blockLightIn.r > 0.001) {
        blockRGB = LEGACY_BLOCK_LIGHT_COLOR * blockLightIn.r;
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
    vec3 indirectRGB = indirectIn * INDIRECT_STRENGTH;
    
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

    // ========================================================================
    // PHASE 6: ENHANCED FOG WITH HEIGHT COMPONENT
    // ========================================================================
    // Distance fog (blends to sky color, hides chunk pop-in)
    // Combined with subtle height-based fog for morning mist effect
    
    // Height fog: denser near sea level (Y=64), thinner at height
    // exp(-y * k) gives exponential falloff with height
    float heightFogFactor = exp(-max(vWorldPos.y - 64.0, 0.0) * 0.015);
    
    // Combine distance fog with height fog (height fog is subtle, max 30% contribution)
    float combinedFog = max(vFogFactor, heightFogFactor * 0.3);
    
    vec3 finalColor = mix(litColor, uFogColor, combinedFog);

    fragColor = vec4(finalColor, texColor.a * uAlpha);

    // Compute view-space normal from screen-space position derivatives.
    // This gives flat per-face normals for block geometry — exactly what SSAO needs.
    vec3 viewNormal = normalize(cross(dFdx(vViewPos), dFdy(vViewPos)));
    // Encode from [-1,1] to [0,1] for texture storage
    fragNormal = vec4(viewNormal * 0.5 + 0.5, 1.0);
}
