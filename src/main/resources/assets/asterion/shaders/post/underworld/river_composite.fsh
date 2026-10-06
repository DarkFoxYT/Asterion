#version 330
#moj_import <asterion:scene_depth.glsl>
uniform sampler2D SceneSampler;
uniform sampler2D VolumeSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
in vec2 texCoord;
out vec4 fragColor;
float viewDistance(vec2 uv) {
    float d = texture(DepthSampler, uv).r;
    if (asterionSkyDepth(d)) return 100000.0;
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
    float closestDepth = 100000.0;
    // Perspective spreads distant terrain over many blocks per volume texel.
    // A fixed four-block limit creates holes along otherwise continuous surfaces.
    float tolerance = max(.4, reference * .04);
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        vec2 uv = base + vec2(x, y) / size;
        float weight = (x == 0 ? 1.0 - f.x : f.x) * (y == 0 ? 1.0 - f.y : f.y);
        float difference = abs(viewDistance(uv) - reference);
        float relativeDepth = difference / tolerance;
        closestDepth = min(closestDepth, relativeDepth);
        weight *= exp(-min(4.0 * relativeDepth * relativeDepth, 80.0));
        vec4 sampleVolume = texture(VolumeSampler, uv);
        result += sampleVolume * weight;
        total += weight;
    }
    // Fade unmatched silhouettes continuously instead of switching whole texels
    // to clear fog. Smooth reconstruction also avoids exposing the volume grid.
    float confidence = 1.0 - smoothstep(1.0, 3.0, closestDepth);
    return mix(vec4(0,0,0,1), result / max(total, 1e-30), confidence);
}
void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec4 volume = filteredVolume();
    // Emissive sources and bloom composite afterwards; never treat bright model
    // pixels as transparent or desaturate their glow here.
    fragColor = vec4(scene.rgb * volume.a + volume.rgb, scene.a);
}
