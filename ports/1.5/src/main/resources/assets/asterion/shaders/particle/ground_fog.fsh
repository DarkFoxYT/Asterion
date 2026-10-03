#version 330 core

in vec2 planeUV;
in vec4 fogColor;
in float cameraDistance;
in vec2 textureMotion;
out vec4 FragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    vec2 blend = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(cell), hash(cell + vec2(1.0, 0.0)), blend.x),
               mix(hash(cell + vec2(0.0, 1.0)), hash(cell + vec2(1.0)), blend.x), blend.y);
}

void main() {
    vec2 p = planeUV * 2.0 - 1.0;
    float c = cos(textureMotion.x);
    float s = sin(textureMotion.x);
    vec2 q = mat2(c, -s, s, c) * p;
    vec2 seed = vec2(textureMotion.y * 7.3, textureMotion.y * 3.1);
    // One continuous wispy texture, rotating inside the existing haze plane.
    float broad = noise(q * vec2(2.4, 4.1) + seed);
    float detail = noise(q * vec2(6.0, 9.0) + seed + broad * 1.8);
    float wisps = broad * 0.7 + detail * 0.3;
    float radial = length(q * vec2(0.88, 1.25));
    float edge = 1.0 - smoothstep(0.12, 1.0, radial + (0.5 - broad) * 0.55);
    edge *= 1.0 - smoothstep(0.78, 1.0, length(p));
    float body = mix(0.24, 1.0, smoothstep(0.18, 0.78, wisps));

    // Adapt the reference sky shader's gentle horizon blend to a local fog plane.
    float horizonBlend = smoothstep(16.0, 96.0, cameraDistance);
    vec3 color = mix(fogColor.rgb, fogColor.rgb * 0.84, horizonBlend * 0.45);
    float alpha = fogColor.a * edge * body;
    if (alpha < 0.002) discard;
    FragColor = vec4(color, alpha);
}
