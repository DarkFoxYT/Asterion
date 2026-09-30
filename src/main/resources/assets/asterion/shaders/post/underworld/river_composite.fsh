#version 330
uniform sampler2D SceneSampler;
uniform sampler2D VolumeSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
in vec2 texCoord;
out vec4 fragColor;
float viewDistance(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    if (d >= .9999) return 100000.0;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, CameraData.w > .5 ? d : d * 2.0 - 1.0, 1);
    // InvViewProj reconstructs camera-relative positions, not absolute world positions.
    return length(p.xyz / (abs(p.w) < .00001 ? .00001 : p.w));
}
vec4 filteredVolume() {
    // Full-resolution quality needs no reconstruction. Lower settings must not
    // blend fog belonging to the far side of a wall into its foreground pixels.
    if (all(equal(textureSize(VolumeSampler, 0), textureSize(DepthSampler, 0))))
        return texture(VolumeSampler, texCoord);
    vec2 size = vec2(textureSize(VolumeSampler, 0));
    vec2 p = texCoord * size - .5, f = fract(p);
    vec2 base = (floor(p) + .5) / size;
    float reference = viewDistance(texCoord), total = 0.0;
    vec4 result = vec4(0);
    vec4 nearest = vec4(0,0,0,1);
    bool hasNearest = false;
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        vec2 uv = base + vec2(x, y) / size;
        float weight = (x == 0 ? 1.0 - f.x : f.x) * (y == 0 ? 1.0 - f.y : f.y);
        float difference = abs(viewDistance(uv) - reference);
        float tolerance = min(4.0, max(.4, reference * .012));
        if (difference > tolerance) continue;
        weight *= exp(-4.0 * difference * difference / (tolerance * tolerance));
        vec4 sampleVolume = texture(VolumeSampler, uv);
        if (x == (f.x < .5 ? 0 : 1) && y == (f.y < .5 ? 0 : 1)) {
            nearest = sampleVolume; hasNearest = true;
        }
        result += sampleVolume * weight;
        total += weight;
    }
    if (total < .000001) return vec4(0,0,0,1);
    // Keep a subtle low-resolution pixel finish without exposing the volume's
    // integration cells. Nearest detail still obeys the same depth rejection.
    return hasNearest ? mix(result / total, nearest, .55) : result / total;
}
void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec4 volume = filteredVolume();
    // Emissive sources and bloom composite afterwards; never treat bright model
    // pixels as transparent or desaturate their glow here.
    fragColor = vec4(scene.rgb * volume.a + volume.rgb, scene.a);
}
