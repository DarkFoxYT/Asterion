#version 330 core

in vec2 vUV;
out vec4 FragColor;
uniform sampler2D Sampler;
uniform float Intensity;

void main() {
    vec3 glow = max(texture(Sampler, vUV).rgb * Intensity, vec3(0.0));
    // Compress stacked HDR emitters together so their hue survives the LDR composite.
    float peak = max(glow.r, max(glow.g, glow.b));
    glow /= 1.0 + peak;
    FragColor = vec4(glow, 1.0);
}
