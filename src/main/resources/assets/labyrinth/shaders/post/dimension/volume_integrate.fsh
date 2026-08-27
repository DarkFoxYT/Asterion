#version 330

uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DustTime {
    float Time;
};

layout(std140) uniform WorldData {
    mat4 InvViewProj;
    vec4 CameraData;
    vec4 CameraForward;
};

#define CameraPos CameraData.xyz

layout(std140) uniform Intensity {
    float Value;
};

layout(std140) uniform AtmosphereSettings {
    vec3 Settings;
};

layout(std140) uniform DustColor {
    vec3 DustTint;
};

layout(std140) uniform FogColor {
    vec3 FogTint;
};

in vec2 texCoord;
out vec4 fragColor;

vec3 worldRay(vec2 uv) {
    vec4 a = InvViewProj * vec4(uv * 2.0 - 1.0, 0.0, 1.0);
    vec4 b = InvViewProj * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    a.xyz /= abs(a.w) < 0.00001 ? 0.00001 : a.w;
    b.xyz /= abs(b.w) < 0.00001 ? 0.00001 : b.w;
    vec3 direction = normalize(b.xyz - a.xyz);
    return dot(direction, CameraForward.xyz) < 0.0 ? -direction : direction;
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float x00 = mix(hash31(cell), hash31(cell + vec3(1, 0, 0)), f.x);
    float x10 = mix(hash31(cell + vec3(0, 1, 0)), hash31(cell + vec3(1, 1, 0)), f.x);
    float x01 = mix(hash31(cell + vec3(0, 0, 1)), hash31(cell + vec3(1, 0, 1)), f.x);
    float x11 = mix(hash31(cell + vec3(0, 1, 1)), hash31(cell + vec3(1)), f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

float densityAt(vec3 p) {
    float animationSpeed = Settings.z;
    vec3 wind = vec3(Time * 0.006, Time * 0.0015, -Time * 0.004) * animationSpeed;
    float banks = noise3((p + wind) * vec3(0.032, 0.052, 0.032));
    float wisps = noise3((p - wind * 1.4) * vec3(0.080, 0.024, 0.080) + vec3(17.0, 3.0, -9.0));
    float circulation = sin(atan(p.z, p.x) * 4.0 + length(p.xz) * 0.034
                            - Time * 0.012 * animationSpeed) * 0.045;
    float lowAir = 1.0 - smoothstep(28.0, 112.0, p.y);
    return smoothstep(0.30, 0.75, banks * 0.64 + wisps * 0.36 + circulation)
         * mix(0.70, 1.14, lowAir);
}

vec3 reconstructWorld(float depth) {
    float z = CameraData.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(texCoord * 2.0 - 1.0, z, 1.0);
    float safeW = abs(p.w) < 0.00001 ? 0.00001 : p.w;
    return CameraPos + p.xyz / safeW;
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    vec3 direction = worldRay(texCoord);
    float geometryDistance = length(reconstructWorld(depth) - CameraPos);
    float travel = depth >= 0.9999 ? 112.0 : min(geometryDistance, 112.0);
    float stepLength = travel / 10.0;

    float opticalDepth = 0.0;
    vec3 scattering = vec3(0.0);

    for (int i = 0; i < 10; ++i) {
        float distanceAlongRay = (float(i) + 0.5) * stepLength;
        vec3 sampleWorld = CameraPos + direction * distanceAlongRay;
        float density = densityAt(sampleWorld) * Settings.x;
        float extinction = density * stepLength * 0.019 * Settings.y;
        float visibility = exp(-opticalDepth);
        opticalDepth += extinction;

        float heightLight = smoothstep(20.0, 112.0, sampleWorld.y);
        float dustMix = 0.24 + heightLight * 0.18;
        vec3 neutralDust = mix(DustTint, vec3(dot(DustTint, vec3(0.299, 0.587, 0.114))), 0.16);
        vec3 scatterColor = mix(FogTint, neutralDust, dustMix);
        scattering += visibility * extinction * scatterColor;
    }

    fragColor = vec4(scattering, exp(-opticalDepth));
}
