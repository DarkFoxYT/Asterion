#version 330 core

in vec2 quadUV;
in vec4 vColor;
out vec4 FragColor;

void main() {
    vec2 centered = quadUV * 2.0 - 1.0;
    float radius = dot(centered, centered);
    float halo = smoothstep(1.0, 0.08, radius);
    float core = smoothstep(0.24, 0.0, radius);
    vec3 color = vColor.rgb + vec3(1.4, 0.62, 0.06) * core;
    float alpha = vColor.a * halo;
    if (alpha < 0.004) discard;
    FragColor = vec4(color * alpha, alpha);
}
