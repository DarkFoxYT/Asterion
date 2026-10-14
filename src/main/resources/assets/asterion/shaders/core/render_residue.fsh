#version 330 core
in vec2 vUv;
in float vHeight;
flat in vec4 vEffect;
noperspective in vec2 vScreen;
out vec4 FragColor;
void main() {
    // Clip every pixel, including eyes, even during a fast camera turn between ticks.
    float edge = smoothstep(.80, .90, abs(vScreen.x));
    if (edge <= .001 || abs(vScreen.y) > 1.0) discard;
    float opacity = vEffect.x * edge;
    if (vUv.x >= 0.0) {
        vec2 p = (vUv - .5) * vec2(1.0, 1.7);
        float radius = length(p);
        float halo = exp(-radius * radius * 12.0);
        float core = 1.0 - smoothstep(.15, .30, radius);
        float pulse = .93 + .07 * sin(vEffect.y * 2.0 + vEffect.w);
        FragColor = vec4(vec3(3.8, .015, .008) * (core + halo * .18) * pulse,
                         opacity * max(core, halo * .28));
    } else {
        float line = sin(floor(vHeight * 30.0) * 7.91 + floor(vEffect.y * 8.0) + vEffect.w);
        if (line > .985) discard;
        FragColor = vec4(0.0, 0.0, 0.0, opacity);
    }
}
