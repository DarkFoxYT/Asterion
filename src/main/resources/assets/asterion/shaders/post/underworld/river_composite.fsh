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
    return min(length(p.xyz / max(abs(p.w), .00001)), 192.0);
}
vec4 filteredVolume() {
    // A subtle 4x4 pixel grain keeps the stylized texture without making the
    // volume look censored or hiding its smaller particulate detail.
    vec2 screenSize = vec2(textureSize(SceneSampler, 0));
    vec2 pixelUv = (floor(texCoord * screenSize / 4.0) * 4.0 + 2.0) / screenSize;
    // Keep the depth-aware path around silhouettes so large fog pixels do not
    // leak across terrain, entities, or the water edge.
    float centerDepth = texture(DepthSampler, texCoord).r;
    if (fwidth(centerDepth) < .00002) return texture(VolumeSampler, pixelUv);
    vec2 size = vec2(textureSize(VolumeSampler, 0));
    vec2 p = texCoord * size - .5, f = fract(p);
    vec2 base = (floor(p) + .5) / size;
    float reference = viewDistance(texCoord), total = 0.0;
    vec4 result = vec4(0);
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        vec2 uv = base + vec2(x, y) / size;
        float weight = (x == 0 ? 1.0 - f.x : f.x) * (y == 0 ? 1.0 - f.y : f.y);
        weight /= 1.0 + abs(viewDistance(uv) - reference) * 4.0;
        result += texture(VolumeSampler, uv) * weight;
        total += weight;
    }
    return result / max(total, .000001);
}
void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec4 volume = filteredVolume();
    vec3 fogged = scene.rgb * volume.a + volume.rgb;
    // The atmosphere is composited after Amnetic bloom; keep bright emissive
    // pixels and their halos above it while ordinary surfaces remain fogged.
    float emissive = smoothstep(.48, 1.12, max(scene.r, max(scene.g, scene.b)));
    fragColor = vec4(mix(fogged, scene.rgb, emissive * .92), scene.a);
}
