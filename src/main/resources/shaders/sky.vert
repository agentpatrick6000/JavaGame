#version 330 core
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aTexCoord;

out vec2 vTexCoord;

void main() {
    // Position at far plane (depth = 1.0)
    // gl_Position.z = gl_Position.w means NDC z = 1.0 after perspective divide
    gl_Position = vec4(aPos, 1.0, 1.0);
    vTexCoord = aTexCoord;
}
