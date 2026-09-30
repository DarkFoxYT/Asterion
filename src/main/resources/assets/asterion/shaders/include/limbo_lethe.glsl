#ifndef ASTERION_LIMBO_LETHE
#define ASTERION_LIMBO_LETHE
#moj_import <asterion:limbo_fire.glsl>

// Lethe: opaque ivory silt and drifting veils, rather than reflective water.
// World-space advection keeps the surface and low mist coherent while sailing.
vec3 limboLetheSurface(vec2 p,float time,vec3 normal,vec3 view,float light) {
    vec2 flow=p*vec2(.032,.065)-vec2(time*.0009,time*.0016);
    float warp=limboFireNoise(vec3(flow*.65,time*.0008));
    float silt=limboFireFbm(vec3(flow+vec2(warp*1.8,warp*.45),time*.0012));
    float ribbon=.5+.5*sin(flow.y*2.6+warp*4.0+silt*2.0);
    vec3 color=mix(vec3(.32,.295,.25),vec3(.66,.625,.55),silt*.65+ribbon*.35);
    float diffuse=clamp(dot(normalize(normal),normalize(vec3(-.4,1,.3))),0.0,1.0);
    color*=.85+.15*diffuse;
    float horizon=pow(1.0-abs(dot(normalize(normal),view)),3.0);
    color=mix(color,vec3(.72,.69,.62),horizon*.32);
    return color+vec3(.08,.065,.04)*light*light;
}

// Premultiplied scattering and transmission, like the fire volume. Bounded
// slab and sample budget keep all three atmosphere quality modes affordable.
vec4 limboLetheVolume(vec3 camera,vec3 ray,float travel,float base,float time,int count) {
    float enter=0.0,leave=min(travel,160.0);
    if(abs(ray.y)<.0001) {
        if(camera.y<base-5.0||camera.y>base+9.0)return vec4(0,0,0,1);
    } else {
        float a=(base-5.0-camera.y)/ray.y,b=(base+9.0-camera.y)/ray.y;
        enter=max(0.0,min(a,b));leave=min(leave,max(a,b));
    }
    if(leave<=enter)return vec4(0,0,0,1);
    float region=max(limboSeaStyle((camera+ray*enter).xz).oblivion,
        max(limboSeaStyle((camera+ray*leave).xz).oblivion,
            limboSeaStyle((camera+ray*(enter+leave)*.5).xz).oblivion));
    if(region<.001)return vec4(0,0,0,1);
    vec3 scattering=vec3(0);
    float transmission=1.0;
    int steps=min(count,16);
    for(int i=0;i<16;i++) {
        if(i>=steps||transmission<.025)break;
        float stride=(leave-enter)/float(steps);
        vec3 world=camera+ray*(enter+(float(i)+.5)*stride);
        float weight=limboSeaStyle(world.xz).oblivion;
        if(weight<.001)continue;
        float height=world.y-base-sampleWave(world.xz,time).x;
        float veil=limboFireFbm(vec3(world.xz*.055-vec2(time*.0012,time*.0006),height*.18));
        float density=exp(-max(height,0.0)*.48)*smoothstep(-1.2,.3,height)
            *(1.0-smoothstep(5.0,9.0,height))*(.018+.075*smoothstep(.25,.75,veil));
        float opacity=1.0-exp(-density*weight*stride);
        vec3 tint=mix(vec3(.48,.455,.40),vec3(.76,.73,.66),veil);
        scattering+=transmission*opacity*tint;
        transmission*=1.0-opacity;
    }
    return vec4(scattering,transmission);
}
#endif
