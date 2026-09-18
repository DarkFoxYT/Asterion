#version 330

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
layout(std140) uniform UnderworldTime { float Time; };
layout(std140) uniform Intensity { float Value; };
layout(std140) uniform RiverData { vec4 River; };
layout(std140) uniform CharonData { vec4 Charon; };
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
    float underwater = 0.0; // General dust volume handles submerged views.
    float shimmer = .5 + .25 * sin(point.x * .63 + point.z * .38 - Time * .06)
            + .25 * sin(point.x * -.31 + point.z * .72 + Time * .043);
    float grazing = 1.0 - abs(ray.y);
    float hell = smoothstep(180.0, 930.0, point.z);
    vec3 abyss = mix(vec3(.001, .005, .0055), vec3(.003, .006, .007), hell);
    vec3 glint = mix(vec3(.055, .105, .10), vec3(.12, .18, .20), hell);
    vec3 water = mix(scene.rgb, abyss, .68 + grazing * .26);
    water += glint * pow(shimmer, 10.0);

    float invY = 1.0 / (abs(ray.y) < .00001 ? (ray.y < 0.0 ? -.00001 : .00001) : ray.y);
    float fogT0 = (River.x + .06 - CameraData.y) * invY;
    float fogT1 = (River.x + River.y - CameraData.y) * invY;
    float fogEnter = max(0.0, min(fogT0, fogT1));
    float fogLeave = min(travel, max(fogT0, fogT1));
    float fogLength = max(0.0, fogLeave - fogEnter);
    // Four depth samples retain a real volume on the low-cost path.
    float opticalDepth = 0.0;
    float fogHell = 0.0;
    if (fogLength > .001 && River.w > .001) {
        for (int i = 0; i < 4; ++i) {
            float alongRay = fogEnter + (float(i) + .5) * fogLength * .25;
            vec3 fogPoint = CameraData.xyz + ray * alongRay;
            float fogCorridor = 1.0 - smoothstep(10.0, 19.0,
                    abs(fogPoint.x - asterionLimboFastRiverCenter(fogPoint.z)));
            float fogJourney = smoothstep(35.0, 55.0, fogPoint.z)
                    * (1.0 - smoothstep(995.0, 1024.0, fogPoint.z));
            float height = clamp((fogPoint.y - River.x - .06) / max(.01, River.y - .06), 0.0, 1.0);
            float billow = .65 + .20 * sin(fogPoint.x * .24 + fogPoint.z * .15 + fogPoint.y * 2.7 + Time * .014)
                    + .15 * sin(fogPoint.z * .31 - fogPoint.x * .19 - fogPoint.y * 3.1 - Time * .009);
            float top = mix(.58, 1.0, billow);
            float vertical = smoothstep(0.0, .12, height) * (1.0 - smoothstep(top * .4, top, height));
            float density = vertical * billow * fogCorridor * fogJourney
                    * smoothstep(0.0, .35, travel - alongRay) * fogLength * .25;
            opticalDepth += density;
            fogHell += density * smoothstep(220.0, 950.0, fogPoint.z);
        }
    }
    float distanceFog = 1.0 - exp(-opticalDepth * .65 * River.w);
    vec3 color = scene.rgb;
    if (Charon.w > .025) {
        vec3 world = reconstructWorld(depth);
        vec3 delta = (world - (Charon.xyz + vec3(0.0, 1.95, 0.0))) / vec3(1.15, 2.15, 1.15);
        float mask = 1.0 - smoothstep(.76, 1.06, length(delta));
        vec2 offset = vec2(.0035 * Charon.w, .0006 * Charon.w);
        vec3 blur = (texture(SceneSampler, clamp(texCoord - offset, .001, .999)).rgb
                + scene.rgb * 2.0
                + texture(SceneSampler, clamp(texCoord + offset, .001, .999)).rgb) * .25;
        color = mix(color, blur, mask * Charon.w * .65);
    }
    color = mix(color, water, clamp(surface * River.z + underwater * .68, 0.0, .9));
    vec3 mist = mix(vec3(.60, .69, .72), vec3(.62, .43, .34), fogHell / max(.001, opticalDepth) * .10);
    color = mix(color, mist, distanceFog);
    float luma = dot(color, vec3(.2126, .7152, .0722));
    color += mix(vec3(.25, .30, .32), vec3(.42, .16, .07), hell)
            * max(0.0, .09 - luma) * corridor * .62;
    fragColor = vec4(mix(scene.rgb, color, clamp(Value, 0.0, 1.0)), scene.a);
}
