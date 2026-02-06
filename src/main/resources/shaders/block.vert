#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in float aSkyVisibility;  // 0-1 sky visibility (with AO baked in)
layout(location = 3) in float aBlockLightR;    // Phase 4: red channel of block light
layout(location = 4) in float aBlockLightG;    // Phase 4: green channel of block light
layout(location = 5) in float aBlockLightB;    // Phase 4: blue channel of block light
layout(location = 6) in float aHorizonWeight;  // 0-1 horizon vs zenith weight
layout(location = 7) in float aIndirectR;      // Phase 3: indirect R from probes
layout(location = 8) in float aIndirectG;      // Phase 3: indirect G from probes
layout(location = 9) in float aIndirectB;      // Phase 3: indirect B from probes

uniform mat4 uProjection;
uniform mat4 uView;
uniform vec3 uCameraPos;
uniform float uFogStart;
uniform float uFogEnd;

out vec2 vTexCoord;
out float vSkyVisibility;
out vec3 vBlockLightRGB;   // Phase 4: RGB block light passed to fragment
out float vHorizonWeight;
out vec3 vIndirectRGB;     // Phase 3: indirect lighting from probes
out float vFogFactor;
out vec3 vViewPos;   // view-space position (for SSAO)
out vec3 vWorldPos;  // world-space position (for directional lighting + flicker)

void main() {
    vec4 viewPos4 = uView * vec4(aPos, 1.0);
    gl_Position = uProjection * viewPos4;
    vTexCoord = aTexCoord;
    vSkyVisibility = aSkyVisibility;
    
    // Phase 4: Pass RGB block light directly
    // If G and B are 0 but R > 0, this is a legacy mesh — apply warm color in fragment
    vBlockLightRGB = vec3(aBlockLightR, aBlockLightG, aBlockLightB);
    
    vHorizonWeight = aHorizonWeight;
    vIndirectRGB = vec3(aIndirectR, aIndirectG, aIndirectB);
    vViewPos = viewPos4.xyz;
    vWorldPos = aPos;  // Pass world position for normal calculation + flicker in fragment

    // Distance-based fog (linear, using dynamic uniforms from LOD config)
    float dist = length(aPos - uCameraPos);
    vFogFactor = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
}
