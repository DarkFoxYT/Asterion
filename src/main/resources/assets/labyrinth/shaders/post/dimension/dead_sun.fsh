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
layout(std140) uniform Intensity { float Value; };
layout(std140) uniform LabyrinthStrength { float EffectStrength; };

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
            vec3 hot = mix(CoreTint * 1.25, CoronaTint * 0.72, smoothstep(0.42, 0.96, radius));
            float contribution = (1.0 - alpha) * density;
            accumulated += hot * contribution;
            alpha += contribution;
        }

        // Small collapsed core grounds the hollow remnant without turning it into a solid sun.
        float impact = length(cross(direction, toSun)) / max(Sun.w, 0.001);
        float core = 1.0 - smoothstep(0.10, 0.19, impact);
        float eclipseDisc = 1.0 - smoothstep(mix(0.12, 0.32, eclipse), mix(0.20, 0.41, eclipse), impact);
        accumulated += CoreTint * core * 0.22 * (1.0 - eclipse * 0.88);
        accumulated *= 1.0 - eclipseDisc * eclipse * 0.98;
        alpha = max(alpha, max(core * 0.52, eclipseDisc * eclipse * 0.98));
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

    float eclipseRing = exp(-abs(radial - mix(0.20, 0.42, eclipse)) * 18.0)
            * eclipse * sunVisibility;

    float pulse = 0.82 + 0.18 * sin(Time * 0.020 * max(Tuning.y, 0.05));
    float strength = clamp(Value * EffectStrength, 0.0, 1.0);
    float opacity = clamp(Opacity, 0.0, 1.0);
    float emission = min(Tuning.x, 4.0) * 0.34 * pulse;
    vec3 color = (accumulated + CoronaTint * (halo * 0.055 + eclipseRing * 0.20))
            * emission * opacity * strength;
    fragColor = vec4(color, clamp(alpha * opacity * strength, 0.0, 1.0));
}
