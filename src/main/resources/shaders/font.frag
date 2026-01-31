#version 330 core

in vec2 vTexCoord;

uniform sampler2D uFontAtlas;
uniform vec4 uColor;

out vec4 fragColor;

void main() {
    float alpha = texture(uFontAtlas, vTexCoord).r;
    if (alpha < 0.5) discard;
    fragColor = vec4(uColor.rgb, uColor.a * alpha);
}
