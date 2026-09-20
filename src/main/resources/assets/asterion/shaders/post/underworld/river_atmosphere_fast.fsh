#version 330
#moj_import <asterion:limbo_waves.glsl>


uniform sampler2D DepthSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
layout(std140) uniform UnderworldTime { float Time; };
layout(std140) uniform Intensity { float Value; };
// x: mist base, y: thickness, z: distance haze, w: low mist.
layout(std140) uniform RiverData { vec4 River; };
in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * .1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1)), f.x), f.y);
}

vec3 unproject(float depth) {
    float z = CameraData.w > .5 ? depth : depth * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(texCoord * 2.0 - 1.0, z, 1.0);
    return p.xyz / (abs(p.w) < .00001 ? .00001 : p.w);
}

void main() {
    vec4 scene = vec4(0.0, 0.0, 0.0, 1.0);
    float strength = clamp(Value, 0.0, 1.0);
    // CameraForward.w carries the shared CPU surface height, evaluated once per frame.
    if (strength < .001 || (CameraData.y < River.x + CameraForward.w - .25)) {
        fragColor = scene;
        return;
    }
    float depth = texture(DepthSampler, texCoord).r;
    vec3 endpoint = unproject(depth);
    vec3 ray = normalize(unproject(.9999) - unproject(.0001));
    if (dot(ray, CameraForward.xyz) < 0.0) ray = -ray;
    float travel = depth >= .9999 ? 192.0 : min(length(endpoint), 192.0);
    // Cheap dark distance extinction replaces detailed fog beyond thirty blocks.
    float haze = (1.0 - exp(-max(0.0, travel - 24.0) * .085 * River.z));
    vec3 color = vec3(.035, .05, .039) * haze;
    float transmission = 1.0 - haze;
    // A separate dim canopy hangs below the cave roof; the foreground stays clear.
    float cloudEnter = 3.0, cloudLeave = min(travel, 30.0);
    float cloudBottom = River.x + 15.0, cloudTop = River.x + 31.0;
    if (abs(ray.y) < .0001) {
        if (CameraData.y < cloudBottom || CameraData.y > cloudTop) cloudLeave = 0.0;
    } else {
        float ca = (cloudBottom - CameraData.y) / ray.y;
        float cb = (cloudTop - CameraData.y) / ray.y;
        cloudEnter = max(3.0, min(ca, cb));
        cloudLeave = min(cloudLeave, max(ca, cb));
    }
    float cloudSpan = max(0.0, cloudLeave - cloudEnter);
    if (cloudSpan > .01 && River.w > .001) {
        float cloudDepth = 0.0;
        for (int c = 0; c < 2; ++c) {
            float cd = cloudEnter + (float(c) + .5) * cloudSpan * .5;
            vec3 cp = CameraData.xyz + ray * cd;
            float ch = (cp.y - cloudBottom) / (cloudTop - cloudBottom);
            float shape = noise(floor(cp.xz * 4.0) * .018 + vec2(Time * .0006, -Time * .0004) + ch);
            float band = smoothstep(0.0, .25, ch) * (1.0 - smoothstep(.65, 1.0, ch));
            cloudDepth += shape * band * cloudSpan * .022 * smoothstep(3.0, 7.0, cd);
        }
        float cloud = 1.0 - exp(-min(cloudDepth * River.w, .65));
        color = mix(color, vec3(.055, .079, .052), cloud);
        transmission *= 1.0 - cloud;
    }
    float bottom = River.x - 3.0, top = River.x + River.y + 3.0;
    float enter = 3.0, leave = min(travel, 30.0);
    if (abs(ray.y) < .0001) {
        if (CameraData.y < bottom || CameraData.y > top) leave = 0.0;
    } else {
        float a = (bottom - CameraData.y) / ray.y;
        float b = (top - CameraData.y) / ray.y;
        enter = max(3.0, min(a, b));
        leave = min(min(travel, 30.0), max(a, b));
    }
    float span = max(0.0, leave - enter);
    if (span < .001 || River.w <= .001) {
        fragColor = vec4(color * strength, mix(1.0, transmission, strength));
        return;
    }
    // Fit the mist base along the ray; depth contact still protects each actual crest.
    float surfaceA = sampleWave(CameraData.xz + ray.xz * enter, Time).x;
    float surfaceB = sampleWave(CameraData.xz + ray.xz * (enter + span * .5), Time).x;
    float surfaceC = sampleWave(CameraData.xz + ray.xz * leave, Time).x;
    float opticalDepth = 0.0;
    float jitter = .5;
    float stepLength = span / 3.0;
    vec2 wind = vec2(Time * .0015, -Time * .001);
    for (int i = 0; i < 3; ++i) {
        float distance = enter + (float(i) + jitter) * stepLength;
        vec3 p = CameraData.xyz + ray * distance;
        vec2 mistPixel = floor(p.xz * 8.0) / 8.0;
        float along = (distance - enter) / span;
        float localSurface = River.x + (along < .5 ? mix(surfaceA, surfaceB, along * 2.0)
                : mix(surfaceB, surfaceC, along * 2.0 - 1.0));
        float height = clamp((p.y - localSurface - .35) / River.y, 0.0, 1.0);
        float billow = noise(mistPixel * .04 + wind + height * vec2(.8, -.55));
        float wisp = noise(mistPixel * .10 - wind * .7 + height * 1.5);
        float profile = smoothstep(0.0, .12, height)
                * (1.0 - smoothstep(.25 + billow * .25, 1.0, height));
        float density = mix(.09, .9, billow * .7 + wisp * .3) * profile;
        // Soft contacts, no planar water overlay, refraction, or scene-UV displacement.
        float contact = smoothstep(0.0, 2.5, travel - distance) * smoothstep(3.0, 7.0, distance) * (1.0 - smoothstep(24.0, 30.0, distance));
        opticalDepth += density * contact * stepLength * .11 * River.w;
    }
    float mist = 1.0 - exp(-min(opticalDepth, .9));
    color = mix(color, vec3(.23, .30, .205), mist);
    transmission *= 1.0 - mist;
    fragColor = vec4(color * strength, mix(1.0, transmission, strength));
}
