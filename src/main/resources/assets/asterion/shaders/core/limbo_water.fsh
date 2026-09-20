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
out vec4 fragColor;

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
    // Vanilla/resource-pack water strip: use one tile, scrolled in world space.
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 scale = vec2(1.0, size.x / size.y);
    vec2 uv = (floor(fract(p) * size.x) + .5) / size;
    return textureGrad(Sampler0, uv, dFdx(p) * scale, dFdy(p) * scale).r;
}
void main() {
    float hullEdge = hullDistance(hullPosition);
    if (hullActive > .5 && hullEdge < -.035 && hullPosition.z > 3.0 / 16.0) discard;
    float distance = length(surfacePosition);
    float nearDetail = 1.0 - smoothstep(24.0, 88.0, distance);
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
    vec3 reflected = mix(vec3(.085, .105, .09), vec3(.25, .29, .25), ceiling);
    float sheen = pow(max(dot(reflection, normalize(vec3(-.4, .8, .3))), 0.0), 22.0);
    float slopeLight = clamp(dot(n, normalize(vec3(-.5, 1, .35))), 0.0, 1.0);
    vec3 body = vec3(.115, .145, .122) * (.8 + .35 * slopeLight + textureDetail * .45);
    vec3 water = mix(body, reflected, fresnel);
    water += vec3(.025, .029, .024) * sheen * (.25 + fresnel) * (1.0 + textureDetail);
    // Broken patches of froth, rather than continuous luminous contour lines.
    float breakup = surfaceNoise(p * .43 + ripples.yz * .16).x;
    float patches = smoothstep(.25, .70, breakup + (grain - .5) * .25);
    float whitecap = foam * mix(.7, .16 + .84 * patches, nearDetail);
    float contact = hullActive * (1.0 - smoothstep(.025, .22, abs(hullEdge)))
            * (1.0 - smoothstep(.8, 1.6, abs(hullPosition.z - .2)));
    float wake = persistentWake(worldSurface).x * shoreExposure;
    whitecap = max(whitecap, max(contact * (.12 + .65 * wakeStrength) * (.4 + .6 * patches), wake * patches * .85));
    // World-space texture steps keep the flecks pixelated without a block grid.
    float fleck = smoothstep(.53, .72, grain) * nearDetail;
    whitecap *= mix(.65, 1.0, fleck);
    water = mix(water, vec3(.57, .62, .54), whitecap);
    // Fully opaque: the increased surface detail never reveals the seabed.
    fragColor = apply_fog(vec4(water, 1.0), fog_spherical_distance(surfacePosition),
        fog_cylindrical_distance(surfacePosition), FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
