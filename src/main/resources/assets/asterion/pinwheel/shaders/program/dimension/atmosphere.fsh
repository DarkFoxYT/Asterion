#version 150

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform vec3 CameraPosition;
uniform vec3 CameraForward;
uniform vec3 CameraUp;
uniform vec3 CameraRight;
uniform vec4 ProjectionData;
uniform vec4 EffectData;
uniform vec3 DustColor;
uniform vec3 FogColor;
uniform vec3 DeadSunPosition;
uniform vec4 DeadSunData;
uniform vec3 DeadSunCoreColor;
uniform vec3 DeadSunCoronaColor;
uniform vec4 AnimationData;

in vec2 texCoord;
out vec4 fragColor;

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float valueNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(cell);
    float n100 = hash31(cell + vec3(1, 0, 0));
    float n010 = hash31(cell + vec3(0, 1, 0));
    float n110 = hash31(cell + vec3(1, 1, 0));
    float n001 = hash31(cell + vec3(0, 0, 1));
    float n101 = hash31(cell + vec3(1, 0, 1));
    float n011 = hash31(cell + vec3(0, 1, 1));
    float n111 = hash31(cell + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float linearDepth(float depth, float nearPlane, float farPlane) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) /
           max(0.0001, farPlane + nearPlane - z * (farPlane - nearPlane));
}

void main() {
    vec4 scene = texture(DiffuseSampler0, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 ray = normalize(CameraForward + CameraRight * (ndc.x * ProjectionData.x * ProjectionData.y)
                         + CameraUp * (ndc.y * ProjectionData.x));
    float forwardAmount = max(0.08, dot(ray, CameraForward));
    float forwardDepth = linearDepth(depth, ProjectionData.z, ProjectionData.w);
    float rayDistance = min(128.0, forwardDepth / forwardAmount);

    vec3 colour = scene.rgb;
    float dustStrength = max(0.0, EffectData.x);
    if (dustStrength > 0.001 && rayDistance > 0.2) {
        int steps = AnimationData.z > 1.5 ? 28 : (AnimationData.z > 0.5 ? 18 : 10);
        float stride = rayDistance / float(steps);
        float jitter = hash31(vec3(gl_FragCoord.xy, fract(AnimationData.x))) * stride;
        float integral = 0.0;
        for (int i = 0; i < 28; ++i) {
            if (i >= steps) break;
            float distanceAlongRay = min(rayDistance, (float(i) + 0.35) * stride + jitter);
            vec3 world = CameraPosition + ray * distanceAlongRay;
            vec3 drift = vec3(AnimationData.x * 0.07, AnimationData.x * 0.015, -AnimationData.x * 0.045)
                       * max(0.05, AnimationData.y);
            float broad = valueNoise(world * 0.028 + drift);
            float detail = valueNoise(world * 0.115 - drift * 1.7);
            float motes = smoothstep(0.74, 0.97, detail) * 0.55;
            float density = max(0.0, broad * 0.78 + detail * 0.22 - 0.36) + motes;
            integral += density * stride;
        }
        float fog = 1.0 - exp(-rayDistance * 0.0065 * EffectData.w * dustStrength);
        float dust = 1.0 - exp(-integral * 0.010 * EffectData.z * dustStrength);
        float amount = clamp(fog * 0.48 + dust * 0.42, 0.0, 0.62);
        vec3 atmosphere = mix(FogColor, DustColor, clamp(dust * 1.35, 0.0, 1.0));
        colour = mix(colour, atmosphere, amount);
    }

    float sunStrength = max(0.0, EffectData.y);
    if (sunStrength > 0.001) {
        vec3 toSun = DeadSunPosition - CameraPosition;
        float sunDistance = max(1.0, length(toSun));
        vec3 sunDirection = toSun / sunDistance;
        float angularRadius = clamp(DeadSunData.x / sunDistance, 0.004, 0.24);
        float angle = acos(clamp(dot(ray, sunDirection), -1.0, 1.0));
        float disc = 1.0 - smoothstep(angularRadius * 0.82, angularRadius, angle);
        float corona = exp(-max(0.0, angle - angularRadius) * 22.0 / max(0.15, DeadSunData.z));
        corona *= 1.0 - disc;
        // Geometry remains authoritative. The sun and corona only occupy unobstructed sky pixels.
        float sky = smoothstep(0.996, 0.99995, depth);
        float pulse = 0.94 + 0.06 * sin(AnimationData.x * 0.8 * max(0.05, AnimationData.y));
        vec3 radiance = DeadSunCoreColor * disc * DeadSunData.y
                      + DeadSunCoronaColor * corona * DeadSunData.y * 1.7;
        colour += radiance * sky * sunStrength * DeadSunData.w * pulse;
    }

    fragColor = vec4(colour, scene.a);
    gl_FragDepth = depth;
}
