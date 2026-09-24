#version 330

// Limbo variant of dimension/volume_integrate: the same world-space 3D dust
// field and front-to-back extinction, with a restrained neutral-grey palette.
uniform sampler2D DepthSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
layout(std140) uniform Intensity { float Value; };
layout(std140) uniform MistQuality { vec4 MarchSteps; };
layout(std140) uniform RiverData { vec4 River; };
layout(std140) uniform UnderworldTime { float Time; };
layout(std140) uniform Submersion { vec4 Underwater; };
layout(std140) uniform LocalLights { vec4 LightPositionRadius[4]; vec4 LightColorStrength[4]; };
in vec2 texCoord;
out vec4 fragColor;

float sceneDepth() {
    float depth = texture(DepthSampler, texCoord).r;
    if (textureSize(DepthSampler, 0).x <= OutSize.x * 1.15) return depth;
    // The closest solid pixel in a reduced-resolution footprint occludes fog.
    vec2 footprint = .45 / max(OutSize, vec2(1));
    depth = min(depth, texture(DepthSampler, texCoord + vec2(-footprint.x, -footprint.y)).r);
    depth = min(depth, texture(DepthSampler, texCoord + vec2( footprint.x, -footprint.y)).r);
    depth = min(depth, texture(DepthSampler, texCoord + vec2(-footprint.x,  footprint.y)).r);
    return min(depth, texture(DepthSampler, texCoord + footprint).r);
}

vec3 worldRay(vec2 uv) {
    vec4 a = InvViewProj * vec4(uv * 2.0 - 1.0, 0.0, 1.0);
    vec4 b = InvViewProj * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    a.xyz /= abs(a.w) < .00001 ? .00001 : a.w;
    b.xyz /= abs(b.w) < .00001 ? .00001 : b.w;
    vec3 direction = normalize(b.xyz - a.xyz);
    return dot(direction, CameraForward.xyz) < 0.0 ? -direction : direction;
}

vec3 reconstructWorld(float depth) {
    float z = CameraData.w > .5 ? depth : depth * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(texCoord * 2.0 - 1.0, z, 1.0);
    float safeW = abs(p.w) < .00001 ? .00001 : p.w;
    // Amnetic's inverse projection is camera-relative, as in volume_integrate.
    return CameraData.xyz + p.xyz / safeW;
}

float hash31(vec3 p) {
    p = fract(p * .1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float x00 = mix(hash31(cell), hash31(cell + vec3(1, 0, 0)), f.x);
    float x10 = mix(hash31(cell + vec3(0, 1, 0)), hash31(cell + vec3(1, 1, 0)), f.x);
    float x01 = mix(hash31(cell + vec3(0, 0, 1)), hash31(cell + vec3(1, 0, 1)), f.x);
    float x11 = mix(hash31(cell + vec3(0, 1, 1)), hash31(cell + vec3(1)), f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

float densityAt(vec3 world, vec3 wind, out float light) {
    float banks = noise3((world + wind) * vec3(.032, .052, .032));
    float wisps = noise3((world - wind * 1.4) * vec3(.080, .024, .080)
            + vec3(17.0, 3.0, -9.0));
    float circulation = sin(atan(world.z, world.x) * 4.0
            + length(world.xz) * .034 - Time * .012) * .045;
    float low = smoothstep(River.x - 1.0, River.x + 1.5, world.y)
            * (1.0 - smoothstep(River.x + 6.0, River.x + 13.0, world.y));
    float high = smoothstep(River.x + 10.0, River.x + 16.0, world.y)
            * (1.0 - smoothstep(River.x + 29.0, River.x + 38.0, world.y));
    light = clamp(.15 + (banks - wisps) * .22 + high * .08, 0.0, .35);
    float ocean = smoothstep(12.0, 72.0, world.z)
            * smoothstep(River.x - 12.0, River.x + 3.0, world.y);
    return smoothstep(.27, .73, banks * .64 + wisps * .36 + circulation)
            * (.22 + low * .85 + high * .25) * (1.0 + ocean * .65);
}

float lightRelief(vec3 world, out vec3 glow) {
    float relief = 0.0;
    glow = vec3(0);
    for (int i = 0; i < 4; i++) {
        if (LightPositionRadius[i].w <= 0.0) continue;
        float radius = LightPositionRadius[i].w;
        float falloff = 1.0 - smoothstep(radius * .18, radius, distance(world, LightPositionRadius[i].xyz));
        float strength = clamp(LightColorStrength[i].w * falloff, 0.0, 1.0);
        relief = max(relief, strength);
        glow += LightColorStrength[i].rgb * strength * .018;
    }
    return relief;
}

void main() {
    float strength = clamp(Value, 0.0, 1.0);
    if (strength < .001 || River.z <= 0.0 || River.w <= 0.0) {
        fragColor = vec4(0, 0, 0, 1);
        return;
    }
    float depth = sceneDepth();
    vec3 direction = worldRay(texCoord);
    float travel = depth >= .9999 ? 136.0
            : max(0.0, min(length(reconstructWorld(depth) - CameraData.xyz) - .12, 136.0));
    int samples = int(clamp(MarchSteps.x, 4.0, 8.0));
    float stepLength = travel / float(samples);
    float opticalDepth = 0.0;
    vec3 scattering = vec3(0);
    vec3 wind = vec3(Time * .006, Time * .0015, -Time * .004);

    for (int i = 0; i < 8; ++i) {
        if (i >= samples) break;
        float along = (float(i) + .5) * stepLength;
        vec3 world = CameraData.xyz + direction * along;
        float localLight;
        float density = densityAt(world, wind, localLight);
        vec3 lightGlow;
        float relief = lightRelief(world, lightGlow);
        float nearRamp = smoothstep(2.0, 13.0, along);
        float extinction = min(density * mix(.45, 1.0, nearRamp)
                * (1.0 - relief * .58) * stepLength * .015 * River.z * River.w,
                max(0.0, 1.32 - opticalDepth));
        float visibility = exp(-opticalDepth);
        opticalDepth += extinction;
        vec3 grey = mix(vec3(.075, .079, .084), vec3(.16, .17, .18), localLight) + lightGlow;
        scattering += visibility * (1.0 - exp(-extinction)) * grey;
    }

    float transmission = exp(-opticalDepth);
    float submerged = clamp(Underwater.x, 0.0, 1.0);
    transmission *= mix(1.0, .075 * exp(-travel * .16), submerged);
    scattering = mix(scattering,
            vec3(.0012, .0018, .0032) * (1.0 - exp(-travel * .12)), submerged);
    fragColor = vec4(scattering * strength, mix(1.0, transmission, strength));
}
