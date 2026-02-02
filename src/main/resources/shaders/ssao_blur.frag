#version 330 core
in vec2 vTexCoord;
out float fragOcclusion;

uniform sampler2D uSSAOInput;

void main() {
    // Simple 4x4 box blur to smooth SSAO noise
    vec2 texelSize = 1.0 / vec2(textureSize(uSSAOInput, 0));
    float result = 0.0;
    for (int x = -2; x < 2; x++) {
        for (int y = -2; y < 2; y++) {
            vec2 offset = vec2(float(x), float(y)) * texelSize;
            result += texture(uSSAOInput, vTexCoord + offset).r;
        }
    }
    fragOcclusion = result / 16.0;
}
