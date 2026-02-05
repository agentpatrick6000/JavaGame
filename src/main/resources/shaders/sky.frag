#version 330 core
in vec2 vTexCoord;

// MRT output: must match scene FBO attachments
layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 fragNormal;  // Dummy normal for SSAO (sky has no surface)

uniform vec3 uZenithColor;   // Color at top of sky
uniform vec3 uHorizonColor;  // Color at horizon

void main() {
    // vTexCoord.y: 0 = bottom of screen (horizon), 1 = top of screen (zenith)
    float t = vTexCoord.y;
    
    // Use exponential falloff for more realistic atmospheric gradient
    // Horizon color dominates most of the sky, zenith color only at very top
    float horizonFade = pow(1.0 - t, 1.5);
    
    // Blend between zenith and horizon
    vec3 skyColor = mix(uZenithColor, uHorizonColor, horizonFade);
    
    // Slight dithering to prevent banding on gradient
    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    skyColor += (noise - 0.5) * 0.008;
    
    fragColor = vec4(skyColor, 1.0);
    
    // Output zero-length normal for sky (SSAO shader treats this as unoccluded)
    fragNormal = vec4(0.0, 0.0, 0.0, 1.0);
}
