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
    return min(length(p.xyz / (abs(p.w) < .00001 ? .00001 : p.w) - CameraData.xyz), 192.0);
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
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        vec2 uv = base + vec2(x, y) / size;
        float weight = (x == 0 ? 1.0 - f.x : f.x) * (y == 0 ? 1.0 - f.y : f.y);
        float difference = abs(viewDistance(uv) - reference);
        float tolerance = min(1.0, max(.4, reference * .008));
        if (difference > tolerance) continue;
        weight *= exp(-4.0 * difference * difference / (tolerance * tolerance));
        result += texture(VolumeSampler, uv) * weight;
        total += weight;
    }
    return total < .000001 ? vec4(0, 0, 0, 1) : result / total;
}
void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec4 volume = filteredVolume();
    float high = max(scene.r, max(scene.g, scene.b));
    float low = min(scene.r, min(scene.g, scene.b));
    float luma = dot(scene.rgb, vec3(.2126, .7152, .0722));
    float saturated = smoothstep(.22, .72, (high - low) / max(high, .001));
    float fogAmount = clamp(1.0 - volume.a, 0.0, 1.0);
    // Amnetic's colored bloom belongs to the light source, not the fog volume.
    // Keep nearby fire vivid but prevent distant red spill from coloring the haze.
    vec3 sceneThroughFog = mix(scene.rgb, vec3(luma), saturated * fogAmount * .65);
    vec3 fogged = sceneThroughFog * volume.a + volume.rgb;
    float neutralHighlight = smoothstep(.58, 1.15, luma) * (1.0 - saturated);
    fragColor = vec4(mix(fogged, scene.rgb, neutralHighlight * .28), scene.a);
}
