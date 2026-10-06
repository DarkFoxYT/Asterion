#version 330
#moj_import <asterion:limbo_seas.glsl>
#moj_import <minecraft:fog.glsl>
in vec3 mistPosition;
in vec2 mistWorld;
in float mistTime;
in float mistHeight;
in float mistStrength;
in float mistAcross;
out vec4 fragColor;
float mistHash(vec2 p) {
    vec3 q=fract(vec3(p.xyx)*.1031);
    q+=dot(q,q.yzx+33.33);
    return fract((q.x+q.y)*q.z);
}
float mistNoise(vec2 p) {
    vec2 i=floor(p),f=fract(p),u=f*f*(3.0-2.0*f);
    return mix(mix(mistHash(i),mistHash(i+vec2(1,0)),u.x),
        mix(mistHash(i+vec2(0,1)),mistHash(i+vec2(1)),u.x),u.y);
}
void main() {
    float edge=smoothstep(0.0,.09,mistHeight)*(1.0-smoothstep(.38,1.0,mistHeight));
    edge*=smoothstep(0.0,.22,mistAcross)*(1.0-smoothstep(.78,1.0,mistAcross));
    if(edge*mistStrength<.002)discard;
    vec2 p=vec2(dot(mistWorld,vec2(.17,.23)),mistHeight*2.6-mistTime*.012);
    float cloud=mistNoise(p)+.35*mistNoise(p*2.07+vec2(mistTime*.008,17.0));
    float opacity=edge*smoothstep(.23,.79,cloud)*mistStrength*.12;
    // Cheap aerosol flecks share the plume geometry; physical splash droplets remain nearby.
    vec2 sprayGrid=p*vec2(14,18)+vec2(mistTime*.006,-mistTime*.015);
    float spray=smoothstep(.92,.98,mistHash(floor(sprayGrid)))
            *(1.0-smoothstep(.10,.24,length(fract(sprayGrid)-.5)));
    opacity=max(opacity,edge*spray*mistStrength*.28);
    if(opacity<.003)discard;
    LimboSeaStyle sea=limboSeaStyle(mistWorld);
    vec3 tint=mix(vec3(.85,.92,.96),sea.reflection*.65+vec3(.31),.16);
    tint=mix(tint,vec3(.76,.41,.20),sea.fire*.7);
    tint+=spray*.10;
    fragColor=apply_fog(vec4(tint,opacity),fog_spherical_distance(mistPosition),
        fog_cylindrical_distance(mistPosition),FogEnvironmentalStart,FogEnvironmentalEnd,
        FogRenderDistanceStart,FogRenderDistanceEnd,vec4(sea.fog,1));
}
