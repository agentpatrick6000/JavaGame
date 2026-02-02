#version 330 core
in vec2 vTexCoord;
out float fragOcclusion;

uniform sampler2D uDepthTex;    // scene depth buffer
uniform sampler2D uNormalTex;   // view-space normals (encoded [0,1])
uniform sampler2D uNoiseTex;    // 4x4 random rotation noise

uniform mat4 uProjection;
uniform mat4 uView;
uniform vec2 uNoiseScale;       // screen_size / 4
uniform float uRadius;          // sampling radius in view space
uniform float uBias;            // depth bias to avoid self-occlusion

// 32 hemisphere kernel samples
const int KERNEL_SIZE = 32;
uniform vec3 uSamples[32];

// Reconstruct view-space position from depth
vec3 viewPosFromDepth(vec2 uv) {
    float depth = texture(uDepthTex, uv).r;
    // Convert from [0,1] depth to NDC [-1,1]
    float z = depth * 2.0 - 1.0;
    vec4 clipPos = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 viewPos = inverse(uProjection) * clipPos;
    return viewPos.xyz / viewPos.w;
}

void main() {
    // Get view-space position and normal for this fragment
    vec3 fragPos = viewPosFromDepth(vTexCoord);
    vec3 normal = texture(uNormalTex, vTexCoord).rgb * 2.0 - 1.0; // decode from [0,1]
    normal = normalize(normal);

    // Random rotation from noise texture (tiled across screen)
    vec3 randomVec = texture(uNoiseTex, vTexCoord * uNoiseScale).rgb;

    // Create TBN matrix (tangent, bitangent, normal) for oriented hemisphere
    vec3 tangent = normalize(randomVec - normal * dot(randomVec, normal));
    vec3 bitangent = cross(normal, tangent);
    mat3 TBN = mat3(tangent, bitangent, normal);

    // Accumulate occlusion
    float occlusion = 0.0;
    for (int i = 0; i < KERNEL_SIZE; i++) {
        // Get sample position in view space
        vec3 samplePos = TBN * uSamples[i]; // rotate sample to face normal
        samplePos = fragPos + samplePos * uRadius;

        // Project sample to screen space to get its depth
        vec4 offset = uProjection * vec4(samplePos, 1.0);
        offset.xyz /= offset.w;
        offset.xyz = offset.xyz * 0.5 + 0.5; // to [0,1]

        // Get the depth at this screen position
        float sampleDepth = viewPosFromDepth(offset.xy).z;

        // Range check + occlusion test
        float rangeCheck = smoothstep(0.0, 1.0, uRadius / abs(fragPos.z - sampleDepth));
        occlusion += (sampleDepth >= samplePos.z + uBias ? 1.0 : 0.0) * rangeCheck;
    }

    // Average and invert (1.0 = no occlusion, 0.0 = fully occluded)
    fragOcclusion = 1.0 - (occlusion / float(KERNEL_SIZE));
}
