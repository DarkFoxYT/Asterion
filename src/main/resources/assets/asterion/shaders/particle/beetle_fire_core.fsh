#version 330 core

in vec2 quadUV;
in vec4 vColor;
out vec4 FragColor;

void main() {
    vec2 centered = quadUV * 2.0 - 1.0;
    float radial = dot(centered, centered);
    float core = smoothstep(1.0, 0.04, radial);
    float hot = smoothstep(0.48, 0.0, radial);
    vec3 color = vColor.rgb + vec3(1.35, 0.22, 0.015) * hot;
    float alpha = vColor.a * core;
    if (alpha < 0.004) discard;
    FragColor = vec4(color * alpha, alpha);
}
