#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurDirection {
    vec2 Direction;
};

layout(std140) uniform AsterionQuality { float Quality; };

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 stepUv = Direction / max(InSize, vec2(1.0));
    vec3 glow = texture(InSampler, texCoord).rgb * 0.227027;
    glow += texture(InSampler, texCoord + stepUv * 1.384615).rgb * 0.316216;
    glow += texture(InSampler, texCoord - stepUv * 1.384615).rgb * 0.316216;
    if (Quality >= 1.5) {
        glow += texture(InSampler, texCoord + stepUv * 3.230769).rgb * 0.070270;
        glow += texture(InSampler, texCoord - stepUv * 3.230769).rgb * 0.070270;
    }
    fragColor = vec4(glow, 1.0);
}
