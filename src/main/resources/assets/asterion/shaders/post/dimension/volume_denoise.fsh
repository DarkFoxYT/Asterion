#version 330

uniform sampler2D VolumeSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 pixel = 1.0 / max(InSize, vec2(1.0));
    float centerDepth = texture(DepthSampler, texCoord).r;
    vec4 total = texture(VolumeSampler, texCoord) * 0.50;
    float weightSum = 0.50;

    const vec2 offsets[4] = vec2[4](
        vec2(1, 0), vec2(-1, 0), vec2(0, 1), vec2(0, -1)
    );

    for (int i = 0; i < 4; ++i) {
        vec2 uv = clamp(texCoord + offsets[i] * pixel * 1.25, vec2(0.0), vec2(1.0));
        float sampleDepth = texture(DepthSampler, uv).r;
        float depthWeight = exp(-abs(sampleDepth - centerDepth) * 180.0);
        float weight = depthWeight * 0.125;
        total += texture(VolumeSampler, uv) * weight;
        weightSum += weight;
    }

    fragColor = total / max(weightSum, 0.0001);
}
