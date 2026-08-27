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
    float perspective = 1.0 + dot(centered, normalize(view + vec2(0.0001))) * viewAmount * 0.12;
    vec2 uv = centered / perspective + 0.5;
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
    float edge = smoothstep(0.0, 0.018, min(min(vUv.x, vUv.y), min(1.0-vUv.x, 1.0-vUv.y)));
    FragColor = vec4(color, image.a * edge * vPortal.w);
}
