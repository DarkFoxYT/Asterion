#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in ivec2 UV1;
in vec3 Normal;
out vec3 surfacePosition;
out vec3 surfaceNormal;
out vec2 ripplePosition;
out float foam;
out float detailQuality;
out vec3 hullPosition;
out float hullActive;
out float wakeStrength;
out float shoreExposure;
out vec2 worldSurface;
out float waterTime;
out float waterLight;

#moj_import <asterion:limbo_waves.glsl>
#moj_import <asterion:limbo_wake.glsl>
vec4 meshWave(vec2 p, float ticks) {
    vec2 base = floor(p * .5) * 2.0, f = (p - base) * .5;
    vec4 a = sampleWave(base, ticks);
    if (f.x > .01) a = mix(a, sampleWave(base + vec2(2, 0), ticks), f.x);
    if (f.y < .01) return a;
    vec4 b = sampleWave(base + vec2(0, 2), ticks);
    if (f.x > .01) b = mix(b, sampleWave(base + vec2(2), ticks), f.x);
    return mix(a, b, f.y);
}
void main() {
    int timeAndLight = int(Color.g * 255.0 + .5);
    float ticks = float(UV2.x & 65535) + float(UV2.y & 65535) * 65536.0
            + float(timeAndLight & 15) / 16.0;
    waterLight = float(timeAndLight >> 4) / 15.0;
    int flags = int(Color.b * 255.0 + .5), hullFlags = int(Color.a * 255.0 + .5);
    vec2 chunkEdge = mod(UV0, 16.0);
    bool fine = (flags & 128) != 0 && chunkEdge.x > .01 && chunkEdge.y > .01;
    vec4 w = (fine ? sampleWave(UV0, ticks) : meshWave(UV0, ticks)) * Color.r;
    vec2 relative = vec2((UV1 << 16) >> 16) / 128.0 + w.yz * .9;
    vec2 heading = Normal.xz;
    vec2 local = vec2(-relative.x * heading.x - relative.y * heading.y,
                      relative.x * heading.y - relative.y * heading.x);
    hullActive = (hullFlags & 128) != 0 ? 1.0 : 0.0;
    wakeStrength = (hullFlags & 64) != 0 ? hullActive : 0.0;
    w.x += persistentWake(UV0 + w.yz * .9).y * Color.r;
    float pitch = radians(float((flags & 63) - 32) * .5);
    float roll = radians(float((hullFlags & 63) - 32) * .5);
    vec3 hull = vec3(local.x, w.x - Normal.y * 8.0 - 17.5 / 16.0, local.y);
    hull.yz = mat2(cos(pitch), -sin(pitch), sin(pitch), cos(pitch)) * hull.yz;
    hull.xy = mat2(cos(roll), -sin(roll), sin(roll), cos(roll)) * hull.xy;
    hull.y += 17.5 / 16.0 - .01;
    hullPosition = hull.xzy;
    shoreExposure = Color.r;
    worldSurface = UV0 + w.yz * .9;
    waterTime = ticks;
    vec3 position = Position + vec3(w.y * .9, w.x, w.z * .9);
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    surfacePosition = position;
    surfaceNormal = normalize(vec3(-w.y, 1, -w.z));
    foam = smoothstep(.00127, .0093, w.w) * smoothstep(-.1, .55, w.x);
    ripplePosition = UV0 + vec2(ticks * .0022, -ticks * .0013);
    detailQuality = (flags & 64) != 0 ? 1.0 : 0.0;
}
