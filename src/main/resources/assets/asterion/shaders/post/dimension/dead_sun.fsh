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
    // Entry radiance is a short world-space volume, not a screen-space bloom multiplier. The
    // integration ends at the sampled scene depth, so maze roofs and walls occlude every ray.
    float radianceScatter = 0.0;
    if (Radiance > 0.001) {
        float rayEnd = min(geometryDistance, centerDistance + Sun.w * 8.0);
        float stepLength = rayEnd / 8.0;
        for (int sampleIndex = 0; sampleIndex < 8; sampleIndex++) {
            float travel = (float(sampleIndex) + 0.5) * stepLength;
            vec3 samplePosition = CameraPos + direction * travel;
            vec3 sampleToSun = Sun.xyz - samplePosition;
            float sampleDistance = max(length(sampleToSun), 0.001);
            float sunDistance = sampleDistance / max(Sun.w, 0.001);
            float forwardScatter = pow(max(dot(direction, sampleToSun / sampleDistance), 0.0), 7.0);
            float density = exp(-max(sunDistance - 0.70, 0.0) * 0.34);
            radianceScatter += density * (0.10 + forwardScatter * 0.90)
                    * stepLength / max(Sun.w * 8.0, 0.001);
        }
        radianceScatter *= Radiance * (0.92 + 0.08 * sin(Time * 0.11));
    }
    vec3 radianceColor = mix(vec3(0.72, 0.012, 0.006), vec3(1.0, 0.19, 0.045),
            clamp(radial * 0.20, 0.0, 1.0)) * radianceScatter;
    vec3 color = (accumulated + activeCoronaTint
            * (halo * mix(0.085, 0.14, eclipse) + eclipseRing * mix(0.20, 0.72, eclipse)))
            * emission * opacity * strength * eclipseTransmission;
    color += radianceColor * opacity * strength;
    alpha = max(alpha, clamp(radianceScatter * 0.52, 0.0, 0.68));
    fragColor = vec4(color, clamp(alpha * opacity * strength, 0.0, 1.0));
}
