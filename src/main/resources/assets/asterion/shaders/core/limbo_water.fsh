#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <asterion:limbo_wake.glsl>
#moj_import <asterion:limbo_hull.glsl>
uniform sampler2D Sampler0;
in vec3 surfacePosition;
in vec3 surfaceNormal;
in vec2 ripplePosition;
in float foam;
in float detailQuality;
in vec3 hullPosition;
in float hullActive;
in float wakeStrength;
in float shoreExposure;
in vec2 worldSurface;
in float waterTime;
in float waterLight;
out vec4 fragColor;

#define TAU 6.28318530718
#define MAX_ITER 3

float foamHash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * .1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}
vec3 surfaceNoise(vec2 p) {
    vec2 i=floor(p), f=fract(p), u=f*f*(3.0-2.0*f);
    float a=foamHash(i), b=foamHash(i+vec2(1,0)), c=foamHash(i+vec2(0,1)), d=foamHash(i+vec2(1));
    return vec3(a+(b-a)*u.x+(c-a)*u.y+(a-b-c+d)*u.x*u.y,
        mix(b-a,d-c,u.y)*6.0*f.x*(1.0-f.x), mix(c-a,d-b,u.x)*6.0*f.y*(1.0-f.y));
}

float waterTexture(vec2 p) {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 scale = vec2(1.0, size.x / size.y);
    vec2 uv = (floor(fract(p) * size.x) + .5) / size;
    return textureGrad(Sampler0, uv, dFdx(p) * scale, dFdy(p) * scale).r;
}

float ghostCurrent(vec2 uv, float time) {
    vec2 p = mod(uv * TAU, TAU) - 250.0;
    vec2 q = p;
    float c = 1.0;
    const float intensity = .005;
    for (int n = 0; n < MAX_ITER; ++n) {
        float t = time * (1.0 - 3.5 / float(n + 1));
        q = p + vec2(cos(t - q.x) + sin(t + q.y),
                     sin(t - q.y) + cos(t + q.x));
        vec2 divisor = vec2(sin(q.x + t), cos(q.y + t));
        divisor.x = divisor.x < 0.0 ? min(divisor.x, -.025) : max(divisor.x, .025);
        divisor.y = divisor.y < 0.0 ? min(divisor.y, -.025) : max(divisor.y, .025);
        c += 1.0 / max(length(p / (divisor / intensity)), .001);
    }
    c /= float(MAX_ITER);
    return clamp(pow(abs(1.17 - pow(max(c, 0.0), 1.4)), 8.0), 0.0, 1.0);
}
void main() {
    float hullEdge = hullDistance(hullPosition);
    if (hullActive > .5 && hullEdge < -.035 && hullPosition.z > 3.0 / 16.0) discard;
    float distance = length(surfacePosition);
    float nearDetail = 1.0 - smoothstep(20.0, 48.0, distance);
    vec2 p = ripplePosition;
    float grain = .5;
    float crossing = .5;
    if (nearDetail > .01) {
        grain = waterTexture(p * .22);
        if (detailQuality > .5) crossing = waterTexture(p.yx * .37 + vec2(.31, .17));
    }
    float textureDetail = (grain * .65 + crossing * .35 - .5) * nearDetail;
    vec3 n = normalize(surfaceNormal);
    float detail = nearDetail * (1.0 - smoothstep(.3, 1.3, max(fwidth(p.x), fwidth(p.y)) * 1.8));
    vec3 ripples = vec3(.5, 0, 0);
    if (detail > .01) ripples = surfaceNoise(p * 1.8 + vec2(crossing, grain) * .6);
    n = normalize(n + vec3(-ripples.y, 0, -ripples.z) * .085 * detail);
    vec3 view = normalize(-surfacePosition);
    if (!gl_FrontFacing) n = -n;
    float facing = max(dot(n, view), 0.0);
    float fresnel = .025 + .975 * pow(1.0 - facing, 5.0);
    vec3 reflection = reflect(-view, n);
    float ceiling = smoothstep(-.3, .85, reflection.y);
    vec3 reflected = mix(vec3(.006, .007, .008), vec3(.075, .082, .09), ceiling);
    float sheen = pow(max(dot(reflection, normalize(vec3(-.4, .8, .3))), 0.0), 34.0);
    float slopeLight = clamp(dot(n, normalize(vec3(-.5, 1, .35))), 0.0, 1.0);
    vec3 body = vec3(.0045, .0052, .0058) * (.68 + .52 * slopeLight + textureDetail * .58);
    vec3 water = mix(body, reflected, fresnel);
    water += vec3(.19, .205, .215) * sheen * (.34 + fresnel) * (1.15 + textureDetail);
    const float causticTexel = .25;
    vec2 causticWorld = (floor(worldSurface / causticTexel) + .5) * causticTexel;
    float spectralBase = 0.0;
    float pulse = 1.0;
    float opacityNoise = 1.0;
    if (nearDetail > .01 && shoreExposure > .05) {
        float ghostLarge = ghostCurrent(causticWorld * .017, waterTime * .0072);
        spectralBase = ghostLarge;
        if (detailQuality > .5 && nearDetail > .3)
            spectralBase = mix(ghostLarge, ghostCurrent(causticWorld.yx * .028
                    + vec2(.17, -.31), -waterTime * .0054), .27);
        pulse = .84 + .16 * sin(waterTime * .026 + ghostLarge * TAU * 1.15);
        opacityNoise = .58 + .42 * surfaceNoise(causticWorld * .052
                + vec2(waterTime * .00085, -waterTime * .00055)).x;
    }
    float shoreFade = smoothstep(.12, .82, shoreExposure);
    // Tight silver-grey caustics over an otherwise pitch-black body.
    float spectral = pow(smoothstep(.22, .64, spectralBase), 2.8)
            * (.48 + .52 * fresnel) * nearDetail * pulse * opacityNoise * shoreFade;
    water = mix(water, vec3(.0012, .00135, .0015), .20 + fresnel * .10);
    float silverCaustic = clamp(spectral * (.82 + .30 * detailQuality), 0.0, .88);
    water = mix(water, vec3(.24, .26, .275), silverCaustic);
    water += vec3(.035, .039, .043) * pow(max(0.0, 1.0 - facing), 2.8) * .32;
    // A small lantern glint, separate from the deck light. Keep the sea dark.
    // hullPosition uses local x/z/y ordering; use radial attenuation without mixing coordinate frames.
    vec3 lanternDelta = vec3(0.0, -2.75, 2.46) - hullPosition;
    float lanternRange = 1.0 - smoothstep(1.0, 7.0, length(lanternDelta));
    float lanternPool = hullActive * lanternRange * lanternRange;
    float lanternGlint = pow(max(dot(reflection, normalize(vec3(-.25,.9,.3))),0.0),24.0);
    water += vec3(.09, .07, .042) * lanternPool * (.30 + .55*lanternGlint + .25*fresnel);
    // Match nearby block and Amnetic point lights instead of leaving the
    // replacement mesh as a dark cutout against lit shore geometry.
    float localLight = waterLight * waterLight;
    water += vec3(.075, .105, .12) * localLight * (.42 + .58 * slopeLight);
    water += vec3(.12, .15, .16) * localLight * pow(max(dot(reflection,
            normalize(vec3(-.3, .88, .36))), 0.0), 18.0);
    float breakup = nearDetail > .01 ? surfaceNoise(p * .43 + ripples.yz * .16).x : .5;
    float patches = smoothstep(.25, .70, breakup + (grain - .5) * .25);
    float whitecap = foam * mix(.7, .16 + .84 * patches, nearDetail);
    float contact = hullActive * (1.0 - smoothstep(.025, .22, abs(hullEdge)))
            * (1.0 - smoothstep(.8, 1.6, abs(hullPosition.z - .2)));
    float wake = distance < 56.0 ? persistentWake(causticWorld).x * shoreExposure : 0.0;
    float smoothWake = smoothstep(.01, .42, wake);
    whitecap = max(whitecap, max(contact * (.12 + .65 * wakeStrength) * (.72 + .28 * breakup), smoothWake * .62));
    float fleck = smoothstep(.53, .72, grain) * nearDetail;
    whitecap = max(whitecap * mix(.80, 1.0, fleck), smoothWake * .62);
    water = mix(water, vec3(.28, .30, .31), whitecap);
    fragColor = apply_fog(vec4(water, 1.0), fog_spherical_distance(surfacePosition),
        fog_cylindrical_distance(surfacePosition), FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
