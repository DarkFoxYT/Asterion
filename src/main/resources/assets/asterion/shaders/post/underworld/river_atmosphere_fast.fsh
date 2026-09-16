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

float asterionLimboFastHash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 rayDirection() {
    vec4 a = InvViewProj * vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
    vec4 b = InvViewProj * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    a.xyz /= abs(a.w) < .00001 ? .00001 : a.w;
    b.xyz /= abs(b.w) < .00001 ? .00001 : b.w;
    vec3 ray = normalize(b.xyz - a.xyz);
    return dot(ray, CameraForward.xyz) < 0.0 ? -ray : ray;
}

vec3 reconstructWorld(float depth) {
    float z = CameraData.w > .5 ? depth : depth * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(texCoord * 2.0 - 1.0, z, 1.0);
    p.xyz /= abs(p.w) < .00001 ? .00001 : p.w;
    return CameraData.xyz + p.xyz;
}

float asterionLimboFastRiverCenter(float z) {
    return sin(z * .008) * 18.0
         + sin(z * .019 + 1.7) * 9.0
         + sin(z * .043 + .4) * 4.0;
}

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec3 ray = rayDirection();
    float depth = texture(DepthSampler, texCoord).r;
    float travel = depth >= .9999 ? 96.0
            : min(length(reconstructWorld(depth) - CameraData.xyz), 96.0);
    float waterT = abs(ray.y) < .0001 ? -1.0 : (River.x - CameraData.y) / ray.y;
    vec3 point = CameraData.xyz + ray * max(0.0, waterT);
    float corridor = (1.0 - smoothstep(12.0, 19.0, abs(point.x - asterionLimboFastRiverCenter(point.z))))
            * step(-32.0, point.z) * step(point.z, 1024.0);
    float surface = step(0.0, waterT) * step(waterT, travel) * corridor;
    float underwater = step(CameraData.y, River.x + .12);
    float shimmer = asterionLimboFastHash21(floor(point.xz * .22 + vec2(Time * .025, -Time * .014)));
    float grazing = 1.0 - abs(ray.y);
    float hell = smoothstep(180.0, 930.0, point.z);
    vec3 abyss = mix(vec3(.001, .005, .0055), vec3(.010, .0012, .0005), hell);
    vec3 glint = mix(vec3(.055, .105, .10), vec3(.31, .050, .008), hell);
    vec3 water = mix(scene.rgb, abyss, .68 + grazing * .26);
    water += glint * pow(shimmer, 10.0);

    float invY = abs(ray.y) < .0001 ? 10000.0 : 1.0 / ray.y;
    float fogT0 = (River.x + .06 - CameraData.y) * invY;
    float fogT1 = (River.x + River.y - CameraData.y) * invY;
    float fogEnter = max(0.0, min(fogT0, fogT1));
    float fogLeave = min(travel, max(fogT0, fogT1));
    float fogLength = max(0.0, fogLeave - fogEnter);
    vec3 fogPoint = CameraData.xyz + ray * (fogEnter + fogLength * .5);
    float fogCorridor = (1.0 - smoothstep(15.0, 27.0,
            abs(fogPoint.x - asterionLimboFastRiverCenter(fogPoint.z))))
            * step(-32.0, fogPoint.z) * step(fogPoint.z, 1024.0);
    float fogJourney = smoothstep(38.0, 82.0, fogPoint.z)
            * (1.0 - smoothstep(995.0, 1024.0, fogPoint.z));
    float distanceFog = (1.0 - exp(-fogLength * .16 * River.w)) * fogCorridor * fogJourney;
    vec3 color = mix(scene.rgb, water, clamp(surface * River.z + underwater * .68, 0.0, .9));
    vec3 mist = mix(vec3(.60, .69, .72), vec3(.62, .43, .34), hell * .62);
    color = mix(color, mist, distanceFog * .62);
    float luma = dot(color, vec3(.2126, .7152, .0722));
    color += mix(vec3(.25, .30, .32), vec3(.42, .16, .07), hell)
            * max(0.0, .09 - luma) * corridor * .62;
    fragColor = vec4(mix(scene.rgb, color, clamp(Value, 0.0, 1.0)), scene.a);
}
