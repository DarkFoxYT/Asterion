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
float cascadeHash(vec2 p) {
    vec3 q=fract(vec3(p.xyx)*.1031);q+=dot(q,q.yzx+33.33);
    return fract((q.x+q.y)*q.z);
}
float cascadeNoise(vec2 p) {
    vec2 i=floor(p),f=fract(p),u=f*f*(3.0-2.0*f);
    return mix(mix(cascadeHash(i),cascadeHash(i+vec2(1,0)),u.x),
        mix(cascadeHash(i+vec2(0,1)),cascadeHash(i+vec2(1)),u.x),u.y);
}
float cascadeTexture(vec2 p) {
    vec2 size=vec2(textureSize(Sampler0,0)),scale=vec2(1.0,size.x/size.y);
    vec2 uv=(floor(fract(p)*size.x)+.5)/size;
    return textureGrad(Sampler0,uv,dFdx(p)*scale,dFdy(p)*scale).r;
}
void main() {
    LimboSeaStyle sea=limboSeaStyle(cascadeWorld);
    vec2 tangent=normalize(vec2(-cascadeNormal.z,cascadeNormal.x));
    float across=dot(cascadeWorld,tangent);
    float t=cascadeAlong,drop=t*cascadeDrop;
    // Travel-time coordinates stretch the falling water naturally as it accelerates.
    float fall=sqrt(drop+.45)-cascadeTime*.047;
    float broad=cascadeNoise(vec2(across*.55,fall*.8));
    float folds=cascadeNoise(vec2(across*1.8+broad*.9,fall*1.5));
    vec2 flow=vec2(across*.19+(folds-.5)*.14,fall*.38);
    float grain=cascadeTexture(flow),fine=cascadeTexture(flow*vec2(2.3,.61)+vec2(.27,1.3));
    // The foot of the falling sheet tears into separate streams before meeting the spray.
    // Native flat rim faces are hidden, so these gaps reveal the real landing pool/mist.
    float breakup=smoothstep(.78,1.0,t);
    if(folds<breakup*.28 && grain<.52)discard;
    // Broken, broad white ribbons replace the repeated thin vertical stripes.
    float ribbons=smoothstep(.24,.68,folds*.62+grain*.38);
    float aeration=smoothstep(.06,.72,t);
    float impact=smoothstep(.64,1.0,t);
    float lip=1.0-smoothstep(.015,.15,t);
    vec3 foam=mix(vec3(.78,.90,.95),vec3(1.0,.87,.66),sea.fire);
    float white=clamp(.18+ribbons*.50+aeration*.22+impact*(.10+.15*fine)+lip*grain*.35,.12,.90);
    vec3 body=sea.water*.8+sea.reflection*(.24+.12*grain);
    body=mix(body,foam,white);
    float relief=clamp(.75+.34*(folds-.5)+.22*(fine-.5),.55,1.05);
    body*=relief;
    body+=foam*pow(fine,5.0)*(.14+.14*impact);
    vec3 molten=mix(vec3(.22,.018,.003),vec3(1.0,.34,.035),grain*.5+ribbons*.4);
    body=mix(body,mix(molten,foam,white*.37),sea.fire);
    body=mix(body,foam,smoothstep(.65,.90,white)*sea.fire*.7);
    fragColor=apply_fog(vec4(body,1),fog_spherical_distance(cascadePosition),
        fog_cylindrical_distance(cascadePosition),FogEnvironmentalStart,FogEnvironmentalEnd,
        FogRenderDistanceStart,FogRenderDistanceEnd,vec4(sea.fog,1));
}
