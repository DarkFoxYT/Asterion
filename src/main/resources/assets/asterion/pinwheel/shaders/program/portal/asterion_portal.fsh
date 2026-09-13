#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord * 2.0 - 1.0;
    float radius = length(centered);
    float angle = atan(centered.y, centered.x);
    float time = GameTime * 1200.0;

    float swirl = sin(radius * 17.0 - time * 1.9 + angle * 2.0) * 0.010;
    vec2 direction = radius > 0.001 ? centered / radius : vec2(0.0);
    vec2 warpedUv = clamp(texCoord + direction * swirl, vec2(0.002), vec2(0.998));

    vec4 base = texture(Sampler0, warpedUv);
    vec4 drift = texture(Sampler0, clamp(warpedUv + vec2(
        sin(time * 0.73 + texCoord.y * 13.0),
        cos(time * 0.61 + texCoord.x * 11.0)
    ) * 0.006, vec2(0.002), vec2(0.998)));

    float alpha = max(base.a, drift.a * 0.62);
    if (alpha < 0.015) discard;

    float innerGlow = (1.0 - smoothstep(0.18, 1.22, radius)) * 0.16;
    float rim = smoothstep(0.48, 0.92, radius) * (1.0 - smoothstep(0.92, 1.30, radius));
    float pulse = 0.92 + sin(time * 1.1 + radius * 8.0) * 0.08;
    vec3 textureColor = mix(base.rgb, drift.rgb, 0.25);
    vec3 glowColor = mix(vec3(0.14, 0.32, 0.72), vec3(0.91, 0.30, 0.10), texCoord.y);
    vec3 color = textureColor * (1.05 + innerGlow) + glowColor * rim * 0.34 * pulse;

    fragColor = vec4(color, alpha) * vertexColor * ColorModulator;
}
