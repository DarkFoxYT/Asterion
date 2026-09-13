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
uniform float DeadSunDensity;
uniform vec3 DeadSunCoreColor;
uniform vec3 DeadSunCoronaColor;
uniform vec4 AnimationData;

in vec2 texCoord;
out vec4 fragColor;

#define Time AnimationData.x
#define Quality AnimationData.z
#define Eclipse AnimationData.w

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

float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * ProjectionData.z * ProjectionData.w)
        / max(0.0001, ProjectionData.w + ProjectionData.z
        - z * (ProjectionData.w - ProjectionData.z));
}

vec3 worldRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    return normalize(CameraForward
        + CameraRight * (ndc.x * ProjectionData.x * ProjectionData.y)
        + CameraUp * (ndc.y * ProjectionData.x));
}

float sceneDistance(float depth, vec3 direction) {
    if (depth >= 0.999999) return 100000.0;
    float forwardAmount = max(0.002, dot(direction, CameraForward));
    return linearDepth(depth) / forwardAmount;
}

float densityAt(vec3 p, vec3 wind) {
    float banks = noise3((p + wind) * vec3(0.032, 0.052, 0.032));
    float wisps = noise3((p - wind * 1.4) * vec3(0.080, 0.024, 0.080)
        + vec3(17.0, 3.0, -9.0));
    float circulation = sin(atan(p.z, p.x) * 4.0 + length(p.xz) * 0.034
        - Time * 0.012 * AnimationData.y) * 0.045;
    float lowAir = 1.0 - smoothstep(28.0, 112.0, p.y);
    return smoothstep(0.30, 0.75, banks * 0.64 + wisps * 0.36 + circulation)
        * mix(0.70, 1.14, lowAir);
}

vec3 filmicCurve(vec3 color) {
    color = max(color - 0.003, 0.0);
    return (color * (6.2 * color + 0.5))
        / (color * (6.2 * color + 1.7) + 0.06);
}

bool raySphere(vec3 origin, vec3 direction, out float nearHit, out float farHit) {
    vec3 relative = origin - DeadSunPosition;
    float b = dot(direction, relative);
    float c = dot(relative, relative) - DeadSunData.x * DeadSunData.x;
    float discriminant = b * b - c;
    if (discriminant < 0.0) return false;
    float root = sqrt(discriminant);
    nearHit = max(-b - root, 0.0);
    farHit = -b + root;
    return farHit > nearHit;
}

float remnantDensity(vec3 p) {
    float radius = length(p);
    float shell = exp(-abs(radius - 0.72) * 20.0);
    float ringDistance = length(vec2(length(p.xz) - 0.62, p.y * 1.45));
    float equatorialRing = exp(-ringDistance * 9.0);
    float broad = noise3(p * 4.2 + vec3(8.0, -3.0, 13.0));
    float filament = noise3(p * 11.5 + vec3(-5.0, 17.0, 2.0));
    float structure = smoothstep(0.34, 0.78, broad * 0.68 + filament * 0.32);
    float outerFade = 1.0 - smoothstep(0.92, 1.0, radius);
    return (shell * 0.78 + equatorialRing * 0.48) * structure * outerFade * DeadSunDensity;
}

vec4 renderDeadSun(vec3 direction, float geometryDistance) {
    vec3 toSun = DeadSunPosition - CameraPosition;
    float centerDistance = length(toSun);
    float nearHit;
    float farHit;
    bool intersects = raySphere(CameraPosition, direction, nearHit, farHit)
        && nearHit < geometryDistance;
    vec3 activeCore = mix(DeadSunCoreColor, vec3(1.0, 0.003, 0.008), Eclipse);
    vec3 activeCorona = mix(DeadSunCoronaColor, vec3(1.0, 0.018, 0.055), Eclipse);
    vec3 accumulated = vec3(0.0);
    float alpha = 0.0;

    float glitchRow = floor(texCoord.y * float(textureSize(DiffuseSampler0, 0).y) * 0.12);
    float glitchFrame = floor(Time * 0.22);
    float rowNoise = hash31(vec3(glitchRow, glitchFrame, 19.0));
    float fineNoise = noise3(vec3(texCoord * vec2(18.0, 9.0), Time * 0.060));
    float rowSlip = step(0.94, rowNoise) * (rowNoise - 0.94) * 0.22;
    float eclipseJitter = Eclipse * ((fineNoise - 0.5) * 0.010 + rowSlip);

    if (intersects) {
        float endHit = min(farHit, geometryDistance);
        int sampleCount = Quality < 0.5 ? 6 : (Quality < 1.5 ? 10 : 16);
        float stepLength = max(0.0, endHit - nearHit) / float(sampleCount);
        for (int i = 0; i < 16; ++i) {
            if (i >= sampleCount) break;
            float along = nearHit + (float(i) + 0.5) * stepLength;
            vec3 local = (CameraPosition + direction * along - DeadSunPosition) / DeadSunData.x;
            float density = clamp(remnantDensity(local) * stepLength
                / max(DeadSunData.x, 0.001) * 0.72, 0.0, 0.42);
            vec3 hot = mix(activeCore * 1.55, activeCorona * 0.92,
                smoothstep(0.42, 0.96, length(local)));
            float contribution = (1.0 - alpha) * density;
            accumulated += hot * contribution;
            alpha += contribution;
        }

        float impact = length(cross(direction, toSun)) / max(DeadSunData.x, 0.001);
        float core = 1.0 - smoothstep(0.10, 0.19, impact);
        float eclipseDisc = 1.0 - smoothstep(mix(0.12, 0.88, Eclipse) + eclipseJitter,
            mix(0.20, 0.94, Eclipse) + eclipseJitter, impact);
        accumulated += activeCore * core * 0.34 * (1.0 - Eclipse * 0.72);
        accumulated *= 1.0 - eclipseDisc * Eclipse;
        alpha = max(alpha, max(core * 0.52, eclipseDisc * Eclipse));
    }

    float sinAngle = length(cross(direction, normalize(toSun)));
    float angularRadius = DeadSunData.x / max(centerDistance, DeadSunData.x + 0.001);
    float radial = sinAngle / max(angularRadius, 0.00001);
    float visible = step(centerDistance - DeadSunData.x * 1.20, geometryDistance)
        * step(0.0, dot(direction, toSun));
    float halo = exp(-max(radial - 0.82, 0.0) * (6.5 / max(DeadSunData.z, 0.08)));
    halo *= 1.0 - smoothstep(1.75 + DeadSunData.z * 0.3,
        2.05 + DeadSunData.z * 0.3, radial);
    halo *= visible;
    float eclipseRing = exp(-abs(radial - mix(0.20, 0.94, Eclipse)) * 24.0)
        * Eclipse * visible;
    float eclipseDisc = 1.0 - smoothstep(mix(0.12, 0.88, Eclipse) + eclipseJitter,
        mix(0.20, 0.94, Eclipse) + eclipseJitter, radial);
    alpha = max(alpha, eclipseDisc * Eclipse * visible);
    float pulse = 0.82 + 0.18 * sin(Time * 0.020);
    float emission = min(DeadSunData.y, 5.0) * 0.48 * pulse * mix(1.0, 1.75, Eclipse);
    vec3 color = (accumulated + activeCorona
        * (halo * mix(0.085, 0.14, Eclipse) + eclipseRing * mix(0.20, 0.72, Eclipse)))
        * emission * DeadSunData.w * EffectData.y * (1.0 - eclipseDisc * Eclipse);
    return vec4(color, clamp(alpha * DeadSunData.w * EffectData.y, 0.0, 1.0));
}

void main() {
    vec4 scene = texture(DiffuseSampler0, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec3 direction = worldRay(texCoord);
    float geometryDistance = sceneDistance(depth, direction);
    vec3 colour = scene.rgb;

    if (EffectData.x > 0.001 && EffectData.z > 0.0 && EffectData.w > 0.0) {
        float travel = min(geometryDistance, 112.0);
        int samples = Quality < 0.5 ? 3 : (Quality < 1.5 ? 5 : 7);
        float stepLength = travel / float(samples);
        float opticalDepth = 0.0;
        vec3 scattering = vec3(0.0);
        vec3 wind = vec3(Time * 0.006, Time * 0.0015, -Time * 0.004) * AnimationData.y;
        vec3 neutralDust = mix(DustColor,
            vec3(dot(DustColor, vec3(0.299, 0.587, 0.114))), 0.16);
        for (int i = 0; i < 7; ++i) {
            if (i >= samples) break;
            float along = (float(i) + 0.5) * stepLength;
            vec3 sampleWorld = CameraPosition + direction * along;
            float stalkingBand = smoothstep(12.0, 30.0, along)
                * (1.0 - smoothstep(58.0, 92.0, along));
            float density = densityAt(sampleWorld, wind) * EffectData.z
                * mix(1.0, 1.34, stalkingBand * Eclipse);
            float extinction = density * stepLength * 0.019 * EffectData.w;
            float visibility = exp(-opticalDepth);
            opticalDepth += extinction;
            float heightLight = smoothstep(20.0, 112.0, sampleWorld.y);
            vec3 scatterColor = mix(FogColor, neutralDust, 0.24 + heightLight * 0.18);
            scattering += visibility * extinction * scatterColor;
        }

        float transmission = exp(-opticalDepth);
        float sceneLuminance = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
        float sceneChroma = max(scene.r, max(scene.g, scene.b)) - min(scene.r, min(scene.g, scene.b));
        float localBlockLight = clamp(smoothstep(0.10, 0.72, sceneLuminance)
            * (0.72 + sceneChroma * 0.55), 0.0, 1.0);
        float protectedTransmission = mix(transmission,
            mix(transmission, 1.0, 0.58), localBlockLight);
        vec3 atmospheric = scene.rgb * protectedTransmission
            + scattering * (1.0 - localBlockLight * 0.28);
        float luminance = dot(atmospheric, vec3(0.2126, 0.7152, 0.0722));
        vec3 gradeTint = mix(vec3(0.90, 0.94, 1.02), vec3(1.075, 0.965, 0.82),
            smoothstep(0.055, 0.42, luminance));
        gradeTint = mix(gradeTint, vec3(1.10, 0.93, 0.72),
            smoothstep(0.42, 1.05, luminance));
        vec3 graded = mix(vec3(luminance), atmospheric, 0.88) * gradeTint;
        graded += scattering * vec3(0.20, 0.14, 0.075);
        graded = mix(graded, filmicCurve(graded), 0.32);
        graded = (graded - 0.5) * 1.035 + 0.5;
        colour = mix(scene.rgb, max(graded, vec3(0.0)), clamp(EffectData.x, 0.0, 1.0));
    }

    if (Eclipse > 0.001) {
        float darkness = Eclipse * Eclipse * (3.0 - 2.0 * Eclipse);
        vec3 darkScene = colour * mix(1.0, 0.30, darkness);
        float luma = dot(darkScene, vec3(0.2126, 0.7152, 0.0722));
        colour = mix(darkScene, vec3(luma), darkness * 0.42);
    }

    if (EffectData.y > 0.001) {
        vec4 sun = renderDeadSun(direction, geometryDistance);
        colour = colour * (1.0 - sun.a) + sun.rgb;
    }

    fragColor = vec4(colour, scene.a);
    gl_FragDepth = depth;
}
