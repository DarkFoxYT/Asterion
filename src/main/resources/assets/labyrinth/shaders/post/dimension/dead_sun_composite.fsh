#version 330

uniform sampler2D SceneSampler;
uniform sampler2D SunSampler;
uniform sampler2D BloomSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec4 sun = texture(SunSampler, texCoord);
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    vec3 color = scene.rgb * (1.0 - sun.a) + sun.rgb + bloom * 0.72;
    fragColor = vec4(color, scene.a);
}
