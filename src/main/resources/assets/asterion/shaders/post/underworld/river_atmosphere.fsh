#version 330

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
layout(std140) uniform UnderworldTime { float Time; };
layout(std140) uniform Intensity { float Value; };
layout(std140) uniform RiverData { vec4 River; };

in vec2 texCoord;
out vec4 fragColor;

float asterionLimboHash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float asterionLimboValueNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(asterionLimboHash21(i), asterionLimboHash21(i + vec2(1, 0)), f.x),
               mix(asterionLimboHash21(i + vec2(0, 1)), asterionLimboHash21(i + vec2(1)), f.x), f.y);
}

vec3 reconstructWorld(float depth) {
    float z = CameraData.w > .5 ? depth : depth * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(texCoord * 2.0 - 1.0, z, 1.0);
    p.xyz /= abs(p.w) < .00001 ? .00001 : p.w;
    return CameraData.xyz + p.xyz;
}

vec3 worldRay() {
    vec4 nearPoint = InvViewProj * vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
    vec4 farPoint = InvViewProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    nearPoint.xyz /= abs(nearPoint.w) < .00001 ? .00001 : nearPoint.w;
    farPoint.xyz /= abs(farPoint.w) < .00001 ? .00001 : farPoint.w;
    vec3 direction = normalize(farPoint.xyz - nearPoint.xyz);
    return dot(direction, CameraForward.xyz) < 0.0 ? -direction : direction;
}

float asterionLimboRiverCenter(float z) {
    return sin(z * .008) * 18.0
         + sin(z * .019 + 1.7) * 9.0
         + sin(z * .043 + .4) * 4.0;
}

float asterionLimboCorridorMask(vec3 p, float inner, float outer) {
    float width = 1.0 - smoothstep(inner, outer, abs(p.x - asterionLimboRiverCenter(p.z)));
    return width * step(-32.0, p.z) * step(p.z, 1024.0);
}

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    float intensity = clamp(Value, 0.0, 1.0);
    if (intensity <= .001) { fragColor = scene; return; }

    float depth = texture(DepthSampler, texCoord).r;
    vec3 ray = worldRay();
    vec3 world = reconstructWorld(depth);
    float travel = depth >= .9999 ? 128.0 : min(length(world - CameraData.xyz), 128.0);
    float waterY = River.x;

    float waterT = abs(ray.y) < .0001 ? -1.0 : (waterY - CameraData.y) / ray.y;
    vec3 waterPoint = CameraData.xyz + ray * max(0.0, waterT);
    float surface = step(0.0, waterT) * step(waterT, travel - .08)
            * asterionLimboCorridorMask(waterPoint, 11.5, 18.0);
    float underwater = step(CameraData.y, waterY + .12);

    // The volume is a low river-skin: it starts just above the fluid mesh and never
    // grows into a room-filling cloud.
    float fogBottom = waterY + .06;
    float fogTop = waterY + River.y;
    float invY = abs(ray.y) < .0001 ? 10000.0 : 1.0 / ray.y;
    float t0 = (fogBottom - CameraData.y) * invY;
    float t1 = (fogTop - CameraData.y) * invY;
    float enterFog = max(0.0, min(t0, t1));
    float leaveFog = min(travel, max(t0, t1));
    float fogLength = max(0.0, leaveFog - enterFog);
    vec3 fogPoint = CameraData.xyz + ray * (enterFog + fogLength * .5);
    float fogJourney = smoothstep(38.0, 82.0, fogPoint.z)
            * (1.0 - smoothstep(995.0, 1024.0, fogPoint.z));
    float fogMask = asterionLimboCorridorMask(fogPoint, 15.0, 27.0)
            * smoothstep(0.0, .7, fogLength) * fogJourney;
    float caveMask = asterionLimboCorridorMask(world, 27.0, 44.0);

    // Most cave pixels touch neither effect. Skip every animated sample and extra scene fetch.
    if (surface + underwater + fogMask + caveMask <= .001) { fragColor = scene; return; }

    vec3 color = scene.rgb;
    float hellAtScene = smoothstep(190.0, 940.0, world.z);
    float sceneLuma = dot(color, vec3(.2126, .7152, .0722));
    float shadowLift = max(0.0, .105 - sceneLuma) * caveMask;
    vec3 readableDark = mix(vec3(.34, .39, .41), vec3(.49, .22, .10), hellAtScene);
    color += readableDark * shadowLift * .72;

    // This mask is solved against the river plane and scene depth, so the parallax
    // is composited onto the visible water render layer without tinting cave geometry.
    float waterMask = clamp(surface * River.z + underwater * .72, 0.0, .94);
    if (waterMask > .001) {
        vec3 point = surface > .01 ? waterPoint : world;
        vec2 flowA = vec2(Time * .025, -Time * .012);
        vec2 flowB = vec2(-Time * .017, Time * .021);
        float broad = asterionLimboValueNoise(point.xz * .078 + flowA);
        float detail = asterionLimboValueNoise(point.zx * .153 + flowB + 17.0);
        vec2 warp = vec2(broad - .5, detail - .5) * (.0024 + .0040 * surface);
        vec3 refracted = texture(SceneSampler, clamp(texCoord + warp, .001, .999)).rgb;
        float grazing = 1.0 - abs(ray.y);
        float extinction = clamp(.52 + grazing * .43 + underwater * .30, 0.0, .985);
        float hellWater = smoothstep(180.0, 930.0, point.z);
        float highlight = pow(max(0.0, broad * detail), 7.0) * (1.0 - grazing) * .34;
        vec3 abyss = mix(vec3(.0015, .0055, .0060), vec3(.010, .0012, .0005), hellWater);
        vec3 glint = mix(vec3(.10, .19, .18), vec3(.42, .075, .012), hellWater);
        vec3 water = mix(refracted, abyss, extinction) + glint * highlight;
        color = mix(color, water, waterMask);
    }

    if (fogMask > .001) {
        vec2 wind = vec2(Time * .0045, -Time * .0025);
        float stepLength = fogLength / 6.0;
        float transmission = 1.0;
        vec3 scattering = vec3(0.0);
        for (int sampleIndex = 0; sampleIndex < 6; ++sampleIndex) {
            float alongRay = enterFog + (float(sampleIndex) + .5) * stepLength;
            vec3 samplePoint = CameraData.xyz + ray * alongRay;
            float overRiver = asterionLimboCorridorMask(samplePoint, 15.0, 27.0);
            float banks = asterionLimboValueNoise(samplePoint.xz * .030 + wind);
            float wisps = asterionLimboValueNoise(samplePoint.zx * .072 - wind * 1.35 + 9.0);
            float height = clamp((samplePoint.y - fogBottom) / max(.01, fogTop - fogBottom), 0.0, 1.0);
            float vertical = smoothstep(0.0, .10, height) * (1.0 - smoothstep(.66, 1.0, height));
            float broadSheet = smoothstep(.21, .69, banks * .70 + wisps * .30);
            float cleanBreaks = mix(.74, 1.12, smoothstep(.30, .78, wisps));
            float density = broadSheet * cleanBreaks * mix(1.52, .62, height)
                    * vertical * overRiver * smoothstep(38.0, 82.0, samplePoint.z);
            float extinction = density * stepLength * .090 * River.w;
            float absorbed = 1.0 - exp(-extinction);
            float hellMist = smoothstep(220.0, 950.0, samplePoint.z);
            vec3 coldAsh = mix(vec3(.52, .61, .65), vec3(.78, .84, .86), wisps);
            vec3 hotAsh = mix(vec3(.46, .34, .29), vec3(.78, .60, .45), wisps);
            vec3 mistColor = mix(coldAsh, hotAsh, hellMist * .68);
            float glow = .72 + .28 * smoothstep(.55, .90, banks);
            scattering += transmission * absorbed * mistColor * glow;
            transmission *= 1.0 - absorbed;
        }
        float volumeMix = fogMask * (1.0 - transmission);
        vec3 volumeColor = scattering / max(1.0 - transmission, .001);
        color = mix(color, color * transmission + volumeColor * (1.0 - transmission), fogMask);
        color += mix(vec3(.030, .040, .046), vec3(.052, .018, .006), hellAtScene) * volumeMix;
    }

    fragColor = vec4(mix(scene.rgb, color, intensity), scene.a);
}
