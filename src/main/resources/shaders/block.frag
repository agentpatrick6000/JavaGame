#version 330 core
in vec2 vTexCoord;
in float vSkyLight;
in float vBlockLight;

uniform sampler2D uAtlas;
uniform float uAlpha;         // 1.0 for opaque pass, <1.0 for transparent pass
uniform float uSunBrightness; // 0.0 = midnight, 1.0 = noon

out vec4 fragColor;

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

    fragColor = vec4(texColor.rgb * tintedLight, texColor.a * uAlpha);
}
