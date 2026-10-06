#version 330
#moj_import <asterion:scene_depth.glsl>
#moj_import <asterion:limbo_seas.glsl>
#moj_import <asterion:limbo_depths.glsl>

// Limbo variant of dimension/volume_integrate: the same world-space 3D dust
// field and front-to-back extinction, with a restrained neutral-grey palette.
uniform sampler2D DepthSampler;
layout(std140) uniform FireView { vec4 FireRange; };
layout(std140) uniform WaveWeather { vec4 WaveEvents; };
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
    depth = asterionClosestDepth(depth, texture(DepthSampler, texCoord + vec2(-footprint.x, -footprint.y)).r);
    depth = asterionClosestDepth(depth, texture(DepthSampler, texCoord + vec2( footprint.x, -footprint.y)).r);
    depth = asterionClosestDepth(depth, texture(DepthSampler, texCoord + vec2(-footprint.x,  footprint.y)).r);
    return asterionClosestDepth(depth, texture(DepthSampler, texCoord + footprint).r);
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

float limboAtmosphereNoise(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float x00 = mix(hash31(cell), hash31(cell + vec3(1, 0, 0)), f.x);
    float x10 = mix(hash31(cell + vec3(0, 1, 0)), hash31(cell + vec3(1, 1, 0)), f.x);
    float x01 = mix(hash31(cell + vec3(0, 0, 1)), hash31(cell + vec3(1, 0, 1)), f.x);
    float x11 = mix(hash31(cell + vec3(0, 1, 1)), hash31(cell + vec3(1)), f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

#moj_import <asterion:limbo_cascade_volume.glsl>

float densityAt(vec3 world, vec3 wind, out float light) {
    float banks = limboAtmosphereNoise((world + wind) * vec3(.032, .052, .032));
    float wisps = limboAtmosphereNoise((world - wind * 1.4) * vec3(.080, .024, .080)
            + vec3(17.0, 3.0, -9.0));
    float circulation = sin(atan(world.z, world.x) * 4.0
            + length(world.xz) * .034 - Time * .012) * .045;
    // Continuous 3D density at every altitude, including high vaults and deep caves.
    vec3 filamentPos = (world - wind * .65) * vec3(.048, .072, .048);
    float filamentNoise = limboAtmosphereNoise(filamentPos + vec3(7.3, 2.1, -3.7));
    float filament = (1.0 - smoothstep(.035, .13, abs(filamentNoise - .5)))
            * smoothstep(.42, .69, wisps);
    light = clamp(.12 + (banks - wisps) * .16 + filament * .32, 0.0, .55);
    float ocean = smoothstep(12.0, 72.0, world.z);
    return (.24 + smoothstep(.22, .77, banks * .64 + wisps * .36 + circulation) * .88)
            * (1.0 + ocean * .25) + filament * .20;
}

float lightRelief(vec3 world) {
    float relief = 0.0;
    for (int i = 0; i < 4; i++) {
        if (LightPositionRadius[i].w <= 0.0) continue;
        float radius = LightPositionRadius[i].w;
        float falloff = 1.0 - smoothstep(radius * .18, radius, distance(world, LightPositionRadius[i].xyz));
        float strength = clamp(LightColorStrength[i].w * falloff, 0.0, 1.0);
        relief = max(relief, strength);
    }
    return relief;
}

void main() {
    seaTempestStrength=WaveEvents.x; seaWhirlpoolStrength=WaveEvents.y; seaWhirlpoolCenter=WaveEvents.zw;
    float strength = clamp(Value, 0.0, 1.0);
    if (strength < .001) {
        fragColor = vec4(0, 0, 0, 1);
        return;
    }
    float depth = sceneDepth();
    vec3 direction = worldRay(texCoord);
    float travel = asterionSkyDepth(depth) ? 136.0
            : max(0.0, min(length(reconstructWorld(depth) - CameraData.xyz) - .12, 136.0));
    int samples = int(clamp(MarchSteps.x, 4.0, 8.0));
    float stepLength = travel / float(samples);
    float opticalDepth = 0.0;
    vec3 scattering = vec3(0);
    vec3 wind = vec3(Time * .006, Time * .0015, -Time * .004);

    for (int i = 0; i < 8; ++i) {
        if (i >= samples || River.z <= 0.0 || River.w <= 0.0) break;
        float along = (float(i) + .5) * stepLength;
        vec3 world = CameraData.xyz + direction * along;
        float localLight;
        LimboSeaStyle sea=limboSeaStyle(world.xz);
        float density = densityAt(world, wind, localLight)*sea.density*(1.0-sea.fire*.85);
        float relief = lightRelief(world);
        float nearRamp = smoothstep(2.0, 13.0, along);
        float extinction = min(density * mix(.45, 1.0, nearRamp)
                * (1.0 - relief * .48) * stepLength * .026 * River.z * River.w,
                max(0.0, 2.65 - opticalDepth));
        float visibility = exp(-opticalDepth);
        opticalDepth += extinction;
        vec3 grey = mix(sea.fog*.9, sea.reflection*.85, localLight);
        // Ember glow, cold silver tears, and Lethe's pale veils are world-local.
        grey+=sea.reflection*sea.fire*.12;
        grey=mix(grey,sea.fog*1.2,sea.oblivion*.25);
        scattering += visibility * (1.0 - exp(-extinction)) * grey;
    }

    float transmission = exp(-opticalDepth);
    float submerged = clamp(Underwater.x, 0.0, 1.0);
    // Darkness follows the submerged ray; local lights open a visible pool instead
    // of being erased by a constant full-screen blackout.
    float litWater = max(lightRelief(CameraData.xyz),lightRelief(CameraData.xyz+direction*min(travel,12.0)));
    litWater = smoothstep(.025,.65,litWater);
    float waterTransmission = exp(-travel * mix(.85,.065,litWater));
    transmission = mix(transmission,waterTransmission,submerged);
    scattering = mix(scattering,
            mix(vec3(.00008,.00012,.0002),vec3(.007,.012,.017),litWater)
                    * (1.0-waterTransmission), submerged);
    if(asterionSkyDepth(depth)) {
        float horizon=(1.0-smoothstep(.05,.45,abs(direction.y)))*.28;
        scattering=mix(scattering,limboSeaHorizon(CameraData.xyz,direction)*(1.0-transmission),horizon);
    }
    float fireTravel=asterionSkyDepth(depth)?FireRange.x:min(FireRange.x,max(0.0,length(reconstructWorld(depth)-CameraData.xyz)-.12));
    vec4 fire=limboSeaVolume(CameraData.xyz,direction,fireTravel,River.x,Time,
            int(clamp(MarchSteps.x*4.0-4.0,12.0,28.0)));
    fire.rgb*=1.0-submerged;fire.a=mix(fire.a,1.0,submerged);
    scattering=fire.rgb+scattering*fire.a;
    transmission*=fire.a;
    vec4 spray=limboCascadeVolume(CameraData.xyz,direction,travel,Time,samples);
    spray.rgb*=clamp(River.w,0.0,1.0)*(1.0-submerged);
    spray.a=mix(1.0,spray.a,clamp(River.w,0.0,1.0)*(1.0-submerged));
    scattering=spray.rgb+scattering*spray.a;
    transmission*=spray.a;
    fragColor = vec4(scattering * strength, mix(1.0, transmission, strength));
}
