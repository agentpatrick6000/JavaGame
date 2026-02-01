#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in float aSkyLight;
layout(location = 3) in float aBlockLight;

uniform mat4 uProjection;
uniform mat4 uView;

out vec2 vTexCoord;
out float vSkyLight;
out float vBlockLight;

void main() {
    gl_Position = uProjection * uView * vec4(aPos, 1.0);
    vTexCoord = aTexCoord;
    // Pass both light channels through — interpolation gives smooth vertex lighting
    vSkyLight = aSkyLight;
    vBlockLight = aBlockLight;
}
