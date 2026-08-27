#version 330

uniform sampler2D SceneSampler;
uniform sampler2D VolumeSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Intensity {
    float Value;
};

layout(std140) uniform LabyrinthStrength {
    float EffectStrength;
};

in vec2 texCoord;
out vec4 fragColor;

vec3 filmicCurve(vec3 color) {
    color = max(color - 0.003, 0.0);
    return (color * (6.2 * color + 0.5)) / (color * (6.2 * color + 1.7) + 0.06);
}

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec4 volume = texture(VolumeSampler, texCoord);
    vec3 atmospheric = scene.rgb * volume.a + volume.rgb;

    float luminance = dot(atmospheric, vec3(0.2126, 0.7152, 0.0722));
    vec3 shadowTint = vec3(0.90, 0.94, 1.02);
    vec3 midTint = vec3(1.075, 0.965, 0.82);
    vec3 highlightTint = vec3(1.10, 0.93, 0.72);
    float mids = smoothstep(0.055, 0.42, luminance);
    float highlights = smoothstep(0.42, 1.05, luminance);
    vec3 gradeTint = mix(shadowTint, midTint, mids);
    gradeTint = mix(gradeTint, highlightTint, highlights);

    vec3 graded = mix(vec3(luminance), atmospheric, 0.88) * gradeTint;
    graded += volume.rgb * vec3(0.20, 0.14, 0.075);
    graded = mix(graded, filmicCurve(graded), 0.32);
    graded = (graded - 0.5) * 1.035 + 0.5;

    fragColor = vec4(mix(scene.rgb, max(graded, vec3(0.0)), clamp(Value * EffectStrength, 0.0, 1.0)), scene.a);
}
