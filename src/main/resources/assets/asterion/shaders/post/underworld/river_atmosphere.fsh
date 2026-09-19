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
    // Leave underwater rendering entirely to the native fluid renderer.
    if (strength < .001 || (CameraData.y < River.x + 3.0 && CameraData.y < River.x + sampleWave(CameraData.xz, Time).x - .25)) {
        fragColor = scene;
        return;
    }
    float depth = texture(DepthSampler, texCoord).r;
    vec3 endpoint = unproject(depth);
    vec3 ray = normalize(unproject(.9999) - unproject(.0001));
    if (dot(ray, CameraForward.xyz) < 0.0) ray = -ray;
    float travel = depth >= .9999 ? 192.0 : min(length(endpoint), 192.0);
    // One haze layer, with clear foreground and a soft sea horizon.
    float horizonNoise = noise(ray.xz * 5.0 + CameraData.xz * .004 + Time * .0004);
    float haze = (1.0 - exp(-max(0.0, travel - 22.0) * (.0052 + horizonNoise * .0018)
            * River.z)) * .88;
    vec3 color = vec3(.19, .245, .235) * haze;
    float transmission = 1.0 - haze;
    float bottom = River.x - 3.0, top = River.x + River.y + 3.0;
    float enter = 0.0, leave = travel;
    if (abs(ray.y) < .0001) {
        if (CameraData.y < bottom || CameraData.y > top) leave = 0.0;
    } else {
        float a = (bottom - CameraData.y) / ray.y;
        float b = (top - CameraData.y) / ray.y;
        enter = max(0.0, min(a, b));
        leave = min(travel, max(a, b));
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
    float stepLength = span / 8.0;
    vec2 wind = vec2(Time * .0015, -Time * .001);
    for (int i = 0; i < 8; ++i) {
        float distance = enter + (float(i) + jitter) * stepLength;
        vec3 p = CameraData.xyz + ray * distance;
        float along = (distance - enter) / span;
        float localSurface = River.x + (along < .5 ? mix(surfaceA, surfaceB, along * 2.0)
                : mix(surfaceB, surfaceC, along * 2.0 - 1.0));
        float height = clamp((p.y - localSurface - .35) / River.y, 0.0, 1.0);
        float billow = noise(p.xz * .040 + wind + height * vec2(.8, -.55));
        float wisp = noise(p.xz * .105 - wind * .7 + height * 1.7);
        float profile = smoothstep(0.0, .12, height)
                * (1.0 - smoothstep(.25 + billow * .25, 1.0, height));
        float density = mix(.08, .92, billow * .68 + wisp * .32) * profile;
        // Soft contacts, no planar water overlay, refraction, or scene-UV displacement.
        float contact = smoothstep(0.0, 2.5, travel - distance) * smoothstep(5.0, 20.0, distance);
        opticalDepth += density * contact * stepLength * .032 * River.w;
    }
    float mist = 1.0 - exp(-min(opticalDepth, .55));
    color = mix(color, vec3(.31, .375, .35), mist);
    transmission *= 1.0 - mist;
    fragColor = vec4(color * strength, mix(1.0, transmission, strength));
}
