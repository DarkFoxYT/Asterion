#version 330

uniform sampler2D SceneSampler;
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
    vec4 scene = texture(SceneSampler, texCoord);
    float strength = clamp(Value, 0.0, 1.0);
    // Leave underwater rendering entirely to the native fluid renderer.
    if (strength < .001 || CameraData.y < River.x - .25) {
        fragColor = scene;
        return;
    }
    float depth = texture(DepthSampler, texCoord).r;
    vec3 endpoint = unproject(depth);
    vec3 ray = normalize(unproject(.9999) - unproject(.0001));
    if (dot(ray, CameraForward.xyz) < 0.0) ray = -ray;
    float travel = depth >= .9999 ? 192.0 : min(length(endpoint), 192.0);
    // One haze layer, with clear foreground and a soft sea horizon.
    float haze = (1.0 - exp(-max(0.0, travel - 24.0) * .0065 * River.z)) * .68;
    vec3 color = mix(scene.rgb, vec3(.29, .39, .30), haze);
    float bottom = River.x, top = bottom + River.y;
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
        fragColor = vec4(mix(scene.rgb, color, strength), scene.a);
        return;
    }
    float opticalDepth = 0.0;
    float stepLength = span / 12.0;
    vec2 wind = vec2(Time * .0015, -Time * .001);
    for (int i = 0; i < 12; ++i) {
        float distance = enter + (float(i) + .5) * stepLength;
        vec3 p = CameraData.xyz + ray * distance;
        float height = clamp((p.y - bottom) / River.y, 0.0, 1.0);
        float billow = noise(p.xz * .045 + wind);
        float wisp = noise(p.xz * .12 - wind * .7 + height * .6);
        float profile = smoothstep(0.0, .12, height)
                * (1.0 - smoothstep(.25 + billow * .25, 1.0, height));
        float density = mix(.12, .8, billow * .7 + wisp * .3) * profile;
        // Soft contacts, no planar water overlay, refraction, or scene-UV displacement.
        float contact = smoothstep(0.0, 1.2, travel - distance);
        opticalDepth += density * contact * stepLength * .065 * River.w;
    }
    float mist = 1.0 - exp(-min(opticalDepth, 1.65));
    color = mix(color, vec3(.46, .60, .43), mist);
    fragColor = vec4(mix(scene.rgb, color, strength), scene.a);
}
