#version 330
uniform sampler2D SceneSampler;
uniform sampler2D VolumeSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
in vec2 texCoord;
out vec4 fragColor;
float viewDistance(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    if (d >= .9999) return 192.0;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, CameraData.w > .5 ? d : d * 2.0 - 1.0, 1);
    return min(distance(p.xyz / max(abs(p.w), .00001), CameraData.xyz), 192.0);
}
vec4 filteredVolume() {
    // Smooth upscale in open regions; depth-aware taps protect silhouettes.
    if (texture(DepthSampler, texCoord).r >= .9999) return vec4(0, 0, 0, 1);
    vec2 size = vec2(textureSize(VolumeSampler, 0));
    vec2 p = texCoord * size - .5, f = fract(p);
    vec2 base = (floor(p) + .5) / size;
    float reference = viewDistance(texCoord), total = 0.0, closest = 1e9;
    vec4 result = vec4(0), nearestVolume = vec4(0);
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        vec2 uv = base + vec2(x, y) / size;
        float weight = (x == 0 ? 1.0 - f.x : f.x) * (y == 0 ? 1.0 - f.y : f.y);
        float difference = abs(viewDistance(uv) - reference);
        vec4 sampleVolume = texture(VolumeSampler, uv);
        if (difference < closest) { closest = difference; nearestVolume = sampleVolume; }
        weight *= 1.0 - smoothstep(.35, 1.5, difference);
        result += sampleVolume * weight;
        total += weight;
    }
    return total > .000001 ? result / total : nearestVolume;
}
void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec4 volume = filteredVolume();
    // Keep nearby geometry readable even when multiple mist layers overlap.
    vec3 fogged = mix(scene.rgb, scene.rgb * volume.a + volume.rgb, .55);
    // The atmosphere is composited after Amnetic bloom; keep bright emissive
    // pixels and their halos above it while ordinary surfaces remain fogged.
    float emissive = smoothstep(.48, 1.12, max(scene.r, max(scene.g, scene.b)));
    fragColor = vec4(mix(fogged, scene.rgb, emissive * .92), scene.a);
}
