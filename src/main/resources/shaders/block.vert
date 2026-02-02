#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in float aSkyLight;
layout(location = 3) in float aBlockLight;

uniform mat4 uProjection;
uniform mat4 uView;
uniform vec3 uCameraPos;

out vec2 vTexCoord;
out float vSkyLight;
out float vBlockLight;
out float vFogFactor;
out vec3 vViewPos;   // view-space position (for SSAO)

// Fog parameters
const float FOG_START = 80.0;
const float FOG_END = 128.0;

void main() {
    vec4 viewPos4 = uView * vec4(aPos, 1.0);
    gl_Position = uProjection * viewPos4;
    vTexCoord = aTexCoord;
    vSkyLight = aSkyLight;
    vBlockLight = aBlockLight;
    vViewPos = viewPos4.xyz;

    // Distance-based fog (linear)
    float dist = length(aPos - uCameraPos);
    vFogFactor = clamp((dist - FOG_START) / (FOG_END - FOG_START), 0.0, 1.0);
}
