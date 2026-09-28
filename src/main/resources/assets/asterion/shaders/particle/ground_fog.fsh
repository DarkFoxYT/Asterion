#version 330 core

in vec2 planeUV;
in vec4 fogColor;
in float cameraDistance;
out vec4 FragColor;

void main() {
    vec2 p = planeUV * 2.0 - 1.0;
    float radial = length(p);
    float broadWave = sin(p.x * 5.0 + p.y * 2.8) * 0.06
                    + sin(p.x * 2.9 - p.y * 4.3) * 0.045;
    float edge = 1.0 - smoothstep(0.15, 1.0, radial + broadWave);
    float body = mix(0.68, 1.0, 1.0 - smoothstep(0.0, 0.7, radial));

    // Adapt the reference sky shader's gentle horizon blend to a local fog plane.
    float horizonBlend = smoothstep(16.0, 96.0, cameraDistance);
    vec3 color = mix(fogColor.rgb, fogColor.rgb * 0.84, horizonBlend * 0.45);
    float alpha = fogColor.a * edge * body;
    if (alpha < 0.002) discard;
    FragColor = vec4(color, alpha);
}
