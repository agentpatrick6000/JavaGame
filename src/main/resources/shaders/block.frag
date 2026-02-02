#version 330 core
in vec2 vTexCoord;
in float vSkyLight;
in float vBlockLight;
in float vFogFactor;
in vec3 vViewPos;

uniform sampler2D uAtlas;
uniform float uAlpha;         // 1.0 for opaque pass, <1.0 for transparent pass
uniform float uSunBrightness; // 0.0 = midnight, 1.0 = noon
uniform vec3 uFogColor;       // sky/fog color (matches clear color)

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragNormal; // View-space normals for SSAO (PostFX FBO)

// Minimum ambient light so caves aren't pitch black (starlight)
const float MIN_AMBIENT = 0.02;

void main() {
    vec4 texColor = texture(uAtlas, vTexCoord);
    if (texColor.a < 0.1) discard;

    // Skylight is modulated by time-of-day (sun brightness)
    // Block light (torches etc) is constant regardless of time
    float skyContrib = vSkyLight * uSunBrightness;
    float light = max(max(skyContrib, vBlockLight), MIN_AMBIENT);

    // Slight blue tint at night for moonlight atmosphere
    vec3 tintedLight = vec3(light);
    if (uSunBrightness < 0.5) {
        float nightFactor = 1.0 - uSunBrightness * 2.0; // 0 at day, 1 at full night
        tintedLight = mix(tintedLight, tintedLight * vec3(0.7, 0.75, 1.0), nightFactor * 0.4);
    }

    vec3 litColor = texColor.rgb * tintedLight;

    // Apply distance fog (blends to sky color, hides chunk pop-in)
    vec3 finalColor = mix(litColor, uFogColor, vFogFactor);

    fragColor = vec4(finalColor, texColor.a * uAlpha);

    // Compute view-space normal from screen-space position derivatives.
    // This gives flat per-face normals for block geometry — exactly what SSAO needs.
    vec3 viewNormal = normalize(cross(dFdx(vViewPos), dFdy(vViewPos)));
    // Encode from [-1,1] to [0,1] for texture storage
    fragNormal = vec4(viewNormal * 0.5 + 0.5, 1.0);
}
