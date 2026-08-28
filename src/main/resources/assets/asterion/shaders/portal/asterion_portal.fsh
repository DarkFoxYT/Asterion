#version 330 core

uniform sampler2D PortalSampler;

in vec2 vUv;
flat in vec4 vPortal;
flat in vec4 vEffect;
out vec4 FragColor;

void main() {
    vec2 view = vEffect.xy;
    float viewAmount = clamp(length(view), 0.0, 1.6);
    // Compress the far side and expand the near side to make the flat surface read as a window.
    vec2 centered = vUv - 0.5;
    float squareDistance = max(abs(centered.x), abs(centered.y)) * 2.0;
    float haloLayer = step(0.5, vEffect.w);
    if (haloLayer > 0.5) {
        // Square dust wake using the same oxblood/dirty-ochre palette as the volume shader.
        // This layer performs no texture or relief samples.
        float ring = smoothstep(0.62, 0.76, squareDistance)
                * (1.0 - smoothstep(0.84, 0.98, squareDistance));
        float gridPhase = (centered.x + centered.y) * 18.0 - vEffect.z * 0.38;
        float cadence = 0.72 + 0.28 * sin(gridPhase);
        float breath = 0.87 + 0.13 * sin(vEffect.z * 0.67);
        vec3 dustTint = vec3(0.2607004, 0.07607989, 0.07607989);
        vec3 fogTint = vec3(0.15294118, 0.1364837, 0.049780853);
        vec3 haloColor = mix(fogTint, dustTint, 0.58 + cadence * 0.22) * (1.18 + cadence);
        FragColor = vec4(haloColor, ring * cadence * breath * vPortal.w * 0.58);
        return;
    }
    float perspective = 1.0 + dot(centered, normalize(view + vec2(0.0001))) * viewAmount * 0.12;
    vec2 uv = centered / perspective + 0.5;
    // Only the square central shaft moves; the rectilinear maze remains stable.
    float abyss = 1.0 - smoothstep(0.12, 0.43, squareDistance);
    uv += vec2(sin(centered.y * 28.0 - vEffect.z * 0.48),
            cos(centered.x * 28.0 + vEffect.z * 0.42)) * 0.0045 * abyss;
    const float layers = 20.0;
    vec2 stepUv = view * 0.105 / layers;
    float depth = 0.0;
    float sampledHeight = 0.0;
    for (int i = 0; i < 20; ++i) {
        vec4 probe = texture(PortalSampler, clamp(uv, 0.001, 0.999));
        // Opaque artwork still gets depth: brightness, not alpha, is the height map.
        float height = dot(probe.rgb, vec3(0.2126, 0.7152, 0.0722));
        sampledHeight = height;
        depth += 1.0 / layers;
        if (depth >= 1.0 - height) break;
        uv -= stepUv;
    }
    vec4 image = texture(PortalSampler, clamp(uv, 0.001, 0.999));

    // Derive a small normal map from the same image for readable raised edges and cavities.
    vec2 texel = 1.0 / vec2(textureSize(PortalSampler, 0));
    float hL = dot(texture(PortalSampler, clamp(uv-vec2(texel.x,0.0),0.001,0.999)).rgb,
                   vec3(0.2126,0.7152,0.0722));
    float hR = dot(texture(PortalSampler, clamp(uv+vec2(texel.x,0.0),0.001,0.999)).rgb,
                   vec3(0.2126,0.7152,0.0722));
    float hD = dot(texture(PortalSampler, clamp(uv-vec2(0.0,texel.y),0.001,0.999)).rgb,
                   vec3(0.2126,0.7152,0.0722));
    float hU = dot(texture(PortalSampler, clamp(uv+vec2(0.0,texel.y),0.001,0.999)).rgb,
                   vec3(0.2126,0.7152,0.0722));
    vec3 normal = normalize(vec3((hL-hR)*7.0, (hD-hU)*7.0, 1.0));
    float reliefLight = 0.72 + max(dot(normal, normalize(vec3(-0.45,0.55,0.8))), 0.0) * 0.38;
    float cavity = mix(0.68, 1.08, sampledHeight);
    vec3 color = image.rgb * reliefLight * cavity;
    float aperture = 1.0 - smoothstep(0.91, 1.0, squareDistance);
    float innerVignette = mix(0.72 + 0.28 * (1.0 - squareDistance), 1.0, sampledHeight);
    float pitRim = smoothstep(0.18, 0.28, squareDistance)
            * (1.0 - smoothstep(0.34, 0.47, squareDistance));
    vec3 dustTint = vec3(0.2607004, 0.07607989, 0.07607989);
    vec3 fogTint = vec3(0.15294118, 0.1364837, 0.049780853);
    vec3 riftColor = mix(color * innerVignette,
            mix(fogTint, dustTint, 0.68) + color * 0.32, pitRim * 0.54);
    float coreAlpha = image.a * aperture;
    FragColor = vec4(riftColor, coreAlpha * vPortal.w);
}
