#version 330
#moj_import <asterion:limbo_seas.glsl>
#moj_import <minecraft:fog.glsl>
uniform sampler2D Sampler0;
in vec3 cascadePosition;
in vec2 cascadeWorld;
in float cascadeTime;
in float cascadeDrop;
in float cascadeAlong;
in vec3 cascadeNormal;
out vec4 fragColor;
float cascadeTexture(vec2 p) {
    vec2 size=vec2(textureSize(Sampler0,0));
    vec2 scale=vec2(1.0,size.x/size.y);
    vec2 uv=(floor(fract(p)*size.x)+.5)/size;
    return textureGrad(Sampler0,uv,dFdx(p)*scale,dFdy(p)*scale).r;
}
void main() {
    LimboSeaStyle sea=limboSeaStyle(cascadeWorld);
    float across=dot(cascadeWorld,vec2(-cascadeNormal.z,cascadeNormal.x));
    float drop=cascadeAlong*cascadeDrop;
    // Accelerating texture flow, two texture samples, and derivative-filtered threads.
    float speed=.09+sqrt(max(drop,0.0))*.033;
    float parcel=fract(sin(floor(across*.65)*127.1)*43758.5453);
    float meander=sin(drop*(.22+parcel*.33)-cascadeTime*.031+parcel*6.28);
    vec2 flow=vec2(across*.17+meander*.04,drop*.09-cascadeTime*speed*.065);
    float grain=cascadeTexture(flow), fine=cascadeTexture(flow*vec2(2.1,.42)+vec2(.37,cascadeTime*.004));
    float threadPhase=across*6.0+meander*(.25+parcel*.75)+grain*2.0;
    float width=clamp(fwidth(threadPhase),.08,.7);
    float threads=(1.0-smoothstep(.05,.12+width,abs(sin(threadPhase))))*(.35+.65*fine);
    float churn=smoothstep(.68,1.0,cascadeAlong)*(.38+.62*fine);
    float lip=1.0-smoothstep(0.0,.12,cascadeAlong);
    float sheen=pow(1.0-abs(dot(normalize(cascadeNormal),normalize(-cascadePosition))),3.0);
    vec3 body=sea.water*(.7+grain*.55)+sea.reflection*(.05+sheen*.22);
    body=mix(body,sea.reflection*.92,clamp(threads*(.16+.28*grain)+churn*.52+lip*.13,0.0,.85));
    // Fiery ribbons/embers, pale Lethe veils, green Acheron foam, silver Cocytus tears.
    vec3 molten=mix(vec3(.16,.012,.002),vec3(.95,.27,.018),grain*.6+threads*.35);
    molten+=vec3(1.0,.48,.05)*pow(fine,5.0)*(.2+churn*.5);
    body=mix(body,molten,sea.fire);
    body+=sea.reflection*churn*(sea.oblivion*.08+sea.grief*.05+sea.tears*.12);
    vec3 foamTint=mix(vec3(.84,.93,.98),vec3(1.0,.87,.61),sea.fire);
    body=mix(body,foamTint,clamp(churn*.65+lip*(.16+.24*grain)+threads*fine*.2,0.0,.86));
    fragColor=apply_fog(vec4(body,1.0),fog_spherical_distance(cascadePosition),
        fog_cylindrical_distance(cascadePosition),FogEnvironmentalStart,FogEnvironmentalEnd,
        FogRenderDistanceStart,FogRenderDistanceEnd,vec4(sea.fog,1.0));
}
