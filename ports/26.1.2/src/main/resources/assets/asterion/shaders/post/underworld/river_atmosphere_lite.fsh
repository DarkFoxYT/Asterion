#version 330
uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
layout(std140) uniform UnderworldTime { float Time; };
layout(std140) uniform Intensity { float Value; };
layout(std140) uniform RiverData { vec4 River; };
layout(std140) uniform PresenceData { vec4 Presence; };
layout(std140) uniform PresenceMotion { vec4 Motion; };
in vec2 texCoord;
out vec4 fragColor;
vec3 unproject(float d) {
    float z = CameraData.w > .5 ? d : d * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(texCoord * 2.0 - 1.0, z, 1.0);
    return p.xyz / max(abs(p.w), .00001);
}
void main() {
    float strength = clamp(Value, 0.0, 1.0);
    if (CameraData.y < River.x + CameraForward.w - .25) { fragColor = vec4(0,0,0,1); return; }
    float depth = texture(DepthSampler, texCoord).r;
    vec3 endpoint = unproject(depth);
    float travel = depth >= .9999 ? 96.0 : min(length(endpoint), 96.0);
    vec3 ray = normalize(unproject(.9999) - unproject(.0001));
    if (dot(ray, CameraForward.xyz) < 0.0) ray = -ray;
    // Analytic intersection with a thin mist layer: no volumetric marching on low-end GPUs.
    float nearD = 0.0, farD = travel;
    if (abs(ray.y) < .0001) {
        if (CameraData.y < River.x || CameraData.y > River.x + 3.0) farD = 0.0;
    } else {
        float a = (River.x - CameraData.y) / ray.y;
        float b = (River.x + 3.0 - CameraData.y) / ray.y;
        nearD = max(0.0, min(a,b)); farD = min(travel, max(a,b));
    }
    vec3 middle = CameraData.xyz + ray * (nearD + farD) * .5;
    float noise = texture(NoiseSampler, fract(middle.xz * .018 + vec2(Time * .0003, -Time * .0002))).r;
    float mist = 1.0 - exp(-max(0.0, farD - nearD) * (.022 + .025 * noise) * River.w);
    float haze = 1.0 - exp(-max(0.0, travel - 23.0) * .088 * River.z);
    vec3 color = mix(vec3(.012,.013,.015) * haze, vec3(.055,.058,.062), mist);
    fragColor = vec4(color * strength, mix(1.0, (1.0-haze)*(1.0-mist), strength));
}
