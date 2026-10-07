#ifndef ASTERION_LIMBO_DEPTHS
#define ASTERION_LIMBO_DEPTHS
#moj_import <asterion:limbo_lethe.glsl>

vec3 limboAcheronSurface(vec2 p,float time,float height,vec3 normal,vec3 view,float light) {
    vec2 flow=p*.055-vec2(time*.002,time*.0007);
    float sediment=limboFireFbm(vec3(flow,time*.0015));
    float churn=limboFireNoise(vec3(flow*2.1+sediment*1.8,time*.003));
    vec3 n=normalize(normal);
    float crest=smoothstep(-1.1,1.9,height);
    float slope=clamp(dot(n,normalize(vec3(-.55,.9,.35))),0.0,1.0);
    float rim=pow(1.0-abs(dot(n,view)),3.0);
    // Deep bottle green troughs, olive sediment and luminous moss-green ridges.
    // Broad bands accent the actual mesh peaks instead of painting flat stripes.
    vec3 body=mix(vec3(.009,.028,.013),vec3(.038,.125,.042),sediment);
    body*=.52+crest*.95+slope*.35;
    float ridge=crest*smoothstep(.28,.72,churn)*(.25+.75*rim);
    body=mix(body,vec3(.24,.32,.095),ridge*.62);
    body+=vec3(.055,.12,.035)*rim*crest+vec3(.035,.065,.018)*light*light;
    return body;
}

vec3 limboCocytusSurface(vec2 p,float time,float height,vec3 normal,vec3 view,float light) {
    vec3 n=normalize(normal);
    float crest=smoothstep(-1.6,1.7,height);
    float fresnel=pow(1.0-abs(dot(n,view)),4.0);
    float grain=limboFireFbm(vec3(p*.11-vec2(time*.001,time*.0017),time*.001));
    vec3 reflected=reflect(-view,n);
    float streak=pow(max(dot(reflected,normalize(vec3(-.3,.8,.42))),0.0),22.0);
    vec3 body=mix(vec3(.025,.032,.04),vec3(.15,.18,.205),crest*(.6+grain*.4));
    body=mix(body,vec3(.47,.52,.55),fresnel*.58);
    // Silver tear-like threads run down a restless cold surface; no solid ice.
    body+=vec3(.38,.41,.43)*streak*(.3+.7*crest);
    body+=vec3(.085,.095,.105)*light*light;
    return body;
}

// Regional low banks: murky green for Acheron, thin cold vapour for Cocytus.
// Pure Phlegethon rejects this pass before evaluating noise or wave samples.
vec4 limboDepthMist(vec3 camera,vec3 ray,float travel,float base,float time,int count) {
    float enter=0.0,leave=min(travel,144.0);
    vec2 heights=limboVolumeHeightRange(camera,ray,leave,base);
    if(abs(ray.y)<.0001) {
        if(camera.y<heights.x-5.0||camera.y>heights.y+16.0)return vec4(0,0,0,1);
    } else {
        float a=(heights.x-5.0-camera.y)/ray.y,b=(heights.y+16.0-camera.y)/ray.y;
        enter=max(0.0,min(a,b));leave=min(leave,max(a,b));
    }
    if(leave<=enter)return vec4(0,0,0,1);
    LimboSeaStyle first=limboSeaStyle((camera+ray*enter).xz);
    LimboSeaStyle last=limboSeaStyle((camera+ray*leave).xz);
    LimboSeaStyle middle=limboSeaStyle((camera+ray*(enter+leave)*.5).xz);
    if(max(first.grief+first.tears,max(last.grief+last.tears,middle.grief+middle.tears))<.001)
        return vec4(0,0,0,1);
    int steps=min(count,12);
    float transmission=1.0;
    vec3 scattering=vec3(0);
    for(int i=0;i<12;i++) {
        if(i>=steps||transmission<.025)break;
        float stride=(leave-enter)/float(steps);
        vec3 world=camera+ray*(enter+(float(i)+.5)*stride);
        LimboSeaStyle sea=limboSeaStyle(world.xz);
        float weight=sea.grief+sea.tears;
        if(weight<.001)continue;
        float height=world.y-limboLocalSurface(world.xz,camera.xz,base)-sampleWave(world.xz,time).x;
        float bank=limboFireFbm(vec3(world.xz*.035-vec2(time*.001,time*.0004),height*.12));
        float profile=smoothstep(-1.2,.4,height)*exp(-max(height,0.0)*mix(.21,.42,sea.tears))
            *(1.0-smoothstep(11.0,16.0,height));
        float density=profile*(.010+.042*smoothstep(.24,.76,bank))
            *(sea.grief+sea.tears*.42);
        float opacity=1.0-exp(-density*stride);
        vec3 green=mix(vec3(.035,.09,.028),vec3(.12,.255,.085),bank);
        vec3 cold=mix(vec3(.24,.275,.30),vec3(.53,.57,.60),bank);
        scattering+=transmission*opacity*mix(green,cold,sea.tears/max(weight,.001));
        transmission*=1.0-opacity;
    }
    return vec4(scattering,transmission);
}

vec4 limboSeaVolume(vec3 camera,vec3 ray,float travel,float base,float time,int count) {
    vec4 fire=limboFireVolume(camera,ray,travel,base,time,count);
    vec4 lethe=limboLetheVolume(camera,ray,travel,base,time,count);
    vec4 depths=limboDepthMist(camera,ray,travel,base,time,count);
    return vec4(depths.rgb+(lethe.rgb+fire.rgb*lethe.a)*depths.a,fire.a*lethe.a*depths.a);
}
#endif
