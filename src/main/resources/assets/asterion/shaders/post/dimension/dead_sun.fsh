#version 330

// World-space Minecraft adaptation of "Supernova remnant" by Duke:
// https://www.shadertoy.com/view/MdKXzc (CC BY-NC-SA 3.0).
// Reworked for Amnetic depth occlusion, fixed-step sampling, and texture-free procedural noise.

uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
layout(std140) uniform DustTime { float Time; };
layout(std140) uniform DeadSunData { vec4 Sun; };
layout(std140) uniform DeadSunTuning { vec4 Tuning; };
layout(std140) uniform DeadSunOpacity { float Opacity; };
layout(std140) uniform DeadSunCoreColor { vec3 CoreTint; };
layout(std140) uniform DeadSunCoronaColor { vec3 CoronaTint; };
layout(std140) uniform EclipseData { float Eclipse; };
layout(std140) uniform EntryRadiance { float Radiance; };
layout(std140) uniform BossDamage { float Damage; };
layout(std140) uniform Intensity { float Value; };
layout(std140) uniform AsterionStrength { float EffectStrength; };

#define CameraPos CameraData.xyz

in vec2 texCoord;
out vec4 fragColor;

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float x00 = mix(hash31(cell), hash31(cell + vec3(1, 0, 0)), f.x);
    float x10 = mix(hash31(cell + vec3(0, 1, 0)), hash31(cell + vec3(1, 1, 0)), f.x);
    float x01 = mix(hash31(cell + vec3(0, 0, 1)), hash31(cell + vec3(1, 0, 1)), f.x);
    float x11 = mix(hash31(cell + vec3(0, 1, 1)), hash31(cell + vec3(1)), f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

float wrappedAngle(float angle) {
    return atan(sin(angle), cos(angle));
}

// A stable radial fracture. Damage only reveals additional indexed cracks; time is deliberately
// absent from the geometry so a fissure can never slide, disappear, or re-roll between frames.
float fractureRay(vec2 plane, float index, float damage, out float current) {
    float radius = length(plane);
    float angle = atan(plane.y, plane.x);
    float seed = hash31(vec3(index * 7.31, index * 13.17, 41.0));
    float revealAt = 0.025 + index * 0.069;
    float reveal = smoothstep(revealAt, revealAt + 0.065, damage);
    float heading = seed * 6.2831853;
    float bend = sin(radius * (5.5 + seed * 5.0) + seed * 19.0) * (0.055 + seed * 0.09);
    bend += (noise3(vec3(radius * 5.0, index * 2.7, 9.0)) - 0.5) * 0.12;
    float angularDistance = abs(wrappedAngle(angle - heading - bend)) * max(radius, 0.10);
    float reach = mix(0.48, 1.62, reveal);
    float radialMask = smoothstep(0.08, 0.20, radius)
            * (1.0 - smoothstep(reach - 0.16, reach, radius));
    float width = mix(0.010, 0.022, seed) * mix(0.72, 1.0, reveal);
    float line = (1.0 - smoothstep(width, width * 2.8, angularDistance)) * radialMask * reveal;

    // Two short forks per primary line, anchored to it instead of generating unrelated noise.
    float forkStart = 0.28 + seed * 0.38;
    float forkAngle = heading + bend + mix(-0.62, 0.62,
            hash31(vec3(index, 77.0, 3.0)));
    float forkDistance = abs(wrappedAngle(angle - forkAngle)) * max(radius - forkStart, 0.0);
    float forkMask = smoothstep(forkStart, forkStart + 0.08, radius)
            * (1.0 - smoothstep(forkStart + 0.34, forkStart + 0.54, radius));
    line = max(line, (1.0 - smoothstep(width * 0.72, width * 2.15, forkDistance))
            * forkMask * reveal);
    current = line * (0.55 + 0.45 * sin(Time * 0.075 + radius * 31.0 + index * 2.1));
    return line;
}

vec3 worldRay(vec2 uv) {
    vec4 a = InvViewProj * vec4(uv * 2.0 - 1.0, 0.0, 1.0);
    vec4 b = InvViewProj * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    a.xyz /= abs(a.w) < 0.00001 ? 0.00001 : a.w;
    b.xyz /= abs(b.w) < 0.00001 ? 0.00001 : b.w;
    vec3 direction = normalize(b.xyz - a.xyz);
    return dot(direction, CameraForward.xyz) < 0.0 ? -direction : direction;
}

vec3 reconstructWorld(float depth) {
    float z = CameraData.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(texCoord * 2.0 - 1.0, z, 1.0);
    float safeW = abs(p.w) < 0.00001 ? 0.00001 : p.w;
    return CameraPos + p.xyz / safeW;
}

bool raySphere(vec3 origin, vec3 direction, out float nearHit, out float farHit) {
    vec3 relative = origin - Sun.xyz;
    float b = dot(direction, relative);
    float c = dot(relative, relative) - Sun.w * Sun.w;
    float discriminant = b * b - c;
    if (discriminant < 0.0) return false;
    float root = sqrt(discriminant);
    nearHit = max(-b - root, 0.0);
    farHit = -b + root;
    return farHit > nearHit;
}

float remnantDensity(vec3 p) {
    float radius = length(p);
    float shell = exp(-abs(radius - 0.72) * 20.0);
    float ringDistance = length(vec2(length(p.xz) - 0.62, p.y * 1.45));
    float equatorialRing = exp(-ringDistance * 9.0);

    // Static sphere-space detail: visually close to the reference, but never swims with the camera.
    float broad = noise3(p * 4.2 + vec3(8.0, -3.0, 13.0));
    float filament = noise3(p * 11.5 + vec3(-5.0, 17.0, 2.0));
    float structure = smoothstep(0.34, 0.78, broad * 0.68 + filament * 0.32);
    float outerFade = 1.0 - smoothstep(0.92, 1.0, radius);
    return (shell * 0.78 + equatorialRing * 0.48) * structure * outerFade * Tuning.w;
}

void main() {
    float eclipse = clamp(Eclipse, 0.0, 1.0);
    // A mostly-stable, low-frequency edge fault with sparse horizontal slips. Keeping the
    // displacement small preserves the solid black center while giving its silhouette life.
    float glitchRow = floor(texCoord.y * OutSize.y * 0.12);
    float glitchFrame = floor(Time * 0.22);
    float rowNoise = hash31(vec3(glitchRow, glitchFrame, 19.0));
    float fineNoise = noise3(vec3(texCoord * vec2(18.0, 9.0), Time * 0.060));
    float rowSlip = step(0.94, rowNoise) * (rowNoise - 0.94) * 0.22;
    float eclipseJitter = eclipse * ((fineNoise - 0.5) * 0.010 + rowSlip);
    vec3 eclipseCore = vec3(1.0, 0.003, 0.008);
    vec3 eclipseCorona = vec3(1.0, 0.018, 0.055);
    vec3 activeCoreTint = mix(CoreTint, eclipseCore, eclipse);
    vec3 activeCoronaTint = mix(CoronaTint, eclipseCorona, eclipse);
    vec3 direction = worldRay(texCoord);
    vec3 toSun = Sun.xyz - CameraPos;
    float centerDistance = length(toSun);
    float depth = texture(DepthSampler, texCoord).r;
    float geometryDistance = depth >= 0.9999 ? 100000.0 : length(reconstructWorld(depth) - CameraPos);

    float nearHit;
    float farHit;
    bool intersects = raySphere(CameraPos, direction, nearHit, farHit) && nearHit < geometryDistance;
    vec3 accumulated = vec3(0.0);
    float alpha = 0.0;

    if (intersects) {
        float endHit = min(farHit, geometryDistance);
        float stepLength = (endHit - nearHit) / 16.0;
        for (int i = 0; i < 16; ++i) {
            float distanceAlongRay = nearHit + (float(i) + 0.5) * stepLength;
            vec3 local = (CameraPos + direction * distanceAlongRay - Sun.xyz) / Sun.w;
            float density = clamp(remnantDensity(local) * stepLength / max(Sun.w, 0.001) * 0.72, 0.0, 0.42);
            float radius = length(local);
            vec3 hot = mix(activeCoreTint * 1.55, activeCoronaTint * 0.92,
                    smoothstep(0.42, 0.96, radius));
            float contribution = (1.0 - alpha) * density;
            accumulated += hot * contribution;
            alpha += contribution;
        }

        // Small collapsed core grounds the hollow remnant without turning it into a solid sun.
        float impact = length(cross(direction, toSun)) / max(Sun.w, 0.001);
        float core = 1.0 - smoothstep(0.10, 0.19, impact);
        // At full Eclipse the occluder consumes about ninety percent of the visible Dead Sun,
        // leaving only a narrow, readable corona instead of the previous half-covered disc.
        float eclipseDisc = 1.0 - smoothstep(mix(0.12, 0.88, eclipse) + eclipseJitter,
                mix(0.20, 0.94, eclipse) + eclipseJitter, impact);
        accumulated += activeCoreTint * core * 0.34 * (1.0 - eclipse * 0.72);
        accumulated *= 1.0 - eclipseDisc * eclipse;
        alpha = max(alpha, max(core * 0.52, eclipseDisc * eclipse));
    }

    float sinAngle = length(cross(direction, normalize(toSun)));
    float angularRadius = Sun.w / max(centerDistance, Sun.w + 0.001);
    float radial = sinAngle / max(angularRadius, 0.00001);
    // Emissive corona pixels do not intersect the sphere itself, so they need their own
    // scene-depth gate. Without it the Eclipse ring remains visible through maze walls.
    float sunVisibility = step(centerDistance - Sun.w * 1.20, geometryDistance);
    float halo = exp(-max(radial - 0.82, 0.0) * (6.5 / max(Tuning.z, 0.08)));
    halo *= 1.0 - smoothstep(1.75 + Tuning.z * 0.3, 2.05 + Tuning.z * 0.3, radial);
    halo *= sunVisibility;

    float eclipseRing = exp(-abs(radial - mix(0.20, 0.94, eclipse)) * 24.0)
            * eclipse * sunVisibility;
    // The occluder is composited after every part of the sun. This prevents the halo and
    // emissive ring (which are evaluated outside the sphere raymarch) from tinting its center.
    float finalEclipseDisc = 1.0 - smoothstep(mix(0.12, 0.88, eclipse) + eclipseJitter,
            mix(0.20, 0.94, eclipse) + eclipseJitter, radial);
    float eclipseTransmission = 1.0 - finalEclipseDisc * eclipse;
    // Never let the procedural black mask punch through closer terrain or the arena floor.
    alpha = max(alpha, finalEclipseDisc * eclipse * sunVisibility);

    float pulse = 0.82 + 0.18 * sin(Time * 0.020 * max(Tuning.y, 0.05));
    float strength = clamp(Value * EffectStrength, 0.0, 1.0);
    float opacity = clamp(Opacity, 0.0, 1.0);
    float emission = min(Tuning.x, 5.0) * 0.48 * pulse * mix(1.0, 1.75, eclipse);
    float radianceHalo = exp(-max(radial - 0.52, 0.0) * 1.85) * Radiance * sunVisibility;
    vec3 radianceColor = vec3(1.0, 0.025, 0.018) * radianceHalo * (1.15 + 0.25 * sin(Time * 0.18));
    // Damage permanently reveals an indexed orb-fracture network. Geometry is static and only
    // the contained current moves, giving the impression that the Dead Sun is breaking apart
    // rather than repainting itself with animated noise.
    vec3 sunDirection = normalize(toSun);
    vec3 crackAxis = normalize(cross(sunDirection,
            abs(sunDirection.y) < 0.92 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 crackAxisY = normalize(cross(sunDirection, crackAxis));
    vec2 sunPlane = vec2(dot(direction, crackAxis), dot(direction, crackAxisY))
            / max(angularRadius, 0.00001);
    float cracks = 0.0;
    float current = 0.0;
    for (int fractureIndex = 0; fractureIndex < 14; ++fractureIndex) {
        float movingCurrent;
        float line = fractureRay(sunPlane, float(fractureIndex), clamp(Damage, 0.0, 1.0),
                movingCurrent);
        cracks = max(cracks, line);
        current = max(current, movingCurrent);
    }
    // The portion beyond the orb reads as a fracture in the surrounding sky. It remains fully
    // depth-occluded by roofs/walls and grows only in late fight damage tiers.
    float outsideOrb = smoothstep(0.92, 1.03, radial);
    float skyFracture = cracks * outsideOrb * smoothstep(0.42, 0.74, Damage);
    cracks *= sunVisibility * eclipseTransmission;
    current *= sunVisibility * eclipseTransmission;
    vec3 color = (accumulated + activeCoronaTint
            * (halo * mix(0.085, 0.14, eclipse) + eclipseRing * mix(0.20, 0.72, eclipse)))
            * emission * opacity * strength * eclipseTransmission;
    color *= 1.0 - cracks * 0.94;
    color += vec3(1.0, 0.008, 0.004) * current * (0.70 + Damage * 1.45)
            * opacity * strength;
    color += vec3(0.95, 0.006, 0.003) * skyFracture * (0.35 + current)
            * opacity * strength * eclipseTransmission;
    color += radianceColor * opacity * strength;
    alpha = max(alpha, skyFracture * 0.72);
    fragColor = vec4(color, clamp(alpha * opacity * strength, 0.0, 1.0));
}
