#ifndef ASTERION_LIMBO_FIRE
#define ASTERION_LIMBO_FIRE
#moj_import <asterion:limbo_seas.glsl>
#moj_import <asterion:limbo_waves.glsl>

float limboFireHash(vec3 p) {
    p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);
}
float limboFireNoise(vec3 p) {
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(limboFireHash(i),limboFireHash(i+vec3(1,0,0)),f.x),
                   mix(limboFireHash(i+vec3(0,1,0)),limboFireHash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(limboFireHash(i+vec3(0,0,1)),limboFireHash(i+vec3(1,0,1)),f.x),
                   mix(limboFireHash(i+vec3(0,1,1)),limboFireHash(i+vec3(1)),f.x),f.y),f.z);
}
vec3 limboFireColor(float heat) {
    // HSV shaping follows the supplied reference: saturated red edges, gold cores.
    vec3 hsv=vec3(mix(.015,.14,smoothstep(.25,.95,heat)),
                  mix(.99,.42,smoothstep(.70,1.0,heat)),.24+heat*1.45);
    vec3 p=abs(fract(hsv.xxx+vec3(0,2.0/3.0,1.0/3.0))*6.0-3.0);
    return hsv.z*mix(vec3(1),clamp(p-1.0,0.0,1.0),hsv.y);
}
float limboFireFbm(vec3 p) {
    // The reference's layered, domain-warped noise extended to 3D. Two octaves
    // avoid its nested five-octave cost at every volume sample.
    return limboFireNoise(p)*.68+limboFireNoise(p*2.7+vec3(13,7,-9))*.32;
}
vec3 limboFireSurface(vec2 p,float time) {
    // Broad continuous burning currents, without isolated bright flecks/dots.
    float n=limboFireFbm(vec3(p*.095-vec2(time*.008,time*.003),time*.006));
    float current=.5+.5*sin(dot(p,vec2(.12,.30))-time*.018+n*4.0);
    return limboFireColor(.18+current*.24+n*.10)*(.65+n*.35);
}
vec3 limboFireSurfaceLit(vec2 p,float time,float height,vec3 normal,vec3 view) {
    vec3 n=normalize(normal);
    float slope=clamp(dot(n,normalize(vec3(-.65,.8,.3))),0.0,1.0);
    float crest=smoothstep(-1.4,1.8,height);
    float rim=pow(1.0-clamp(dot(n,view),0.0,1.0),3.0);
    vec3 color=limboFireSurface(p,time)*mix(.38,1.4,crest)*(.5+.65*slope);
    color+=vec3(.32,.10,.005)*rim*crest;
    return color;
}
// Adapted from the supplied fire/smoke map: vertically advected FBM density,
// height-dependent flame emission and cooler smoke, all in world-space 3D.
vec4 limboFireField(vec3 world,float base,float time) {
    float height=world.y-base;
    if(height<-.35||height>7.5)return vec4(0);
    vec2 wind=vec2(.94,.34);
    float gust=sin(world.z*.21-world.x*.08-time*.017)*.28;
    vec2 flow=world.xz-wind*height*(.48+gust);
    // Elongate the advected noise vertically so the field produces flame tongues,
    // while the finer octaves split and tear their tips.
    vec3 p=vec3(flow.x*.85,height*.35,flow.y*.85);
    vec3 rise=vec3(time*.007,time*.052,time*.002);
    vec3 q=p-rise;
    float f=.5*limboFireNoise(q);q=q*2.02-rise;
    f+=.25*limboFireNoise(q);q=q*2.03-rise;
    f+=.125*limboFireNoise(q);
    float raw=clamp(.2-height*.92+4.5*f,0.0,1.0);
    float density=smoothstep(.10,.82,raw)*smoothstep(-.35,.1,height);
    float heat=clamp(density*.67+f*.42,0.0,1.0);
    vec3 color=mix(vec3(1.05,.045,.001),vec3(1.5,.95,.23),smoothstep(.25,.85,heat));
    color=mix(color,vec3(1.7,1.45,.85),smoothstep(.90,1.0,heat));
    // Tiny coherent turbulence creates split tongues rather than round emitters.
    float smoke=smoothstep(2.1,3.1,height)*(1.0-smoothstep(4.8,7.5,height))
            *smoothstep(.34,.60,f)*.06;
    color=mix(vec3(.045,.037,.032),max(color,vec3(0)),clamp(density*4.0,0.0,1.0));
    return vec4(color,density+smoke);
}
// Returns premultiplied emission and transmission, matching river_composite.
// Slab intersection and sea rejection happen before noise; fixed sample budget.
vec4 limboFireVolume(vec3 camera,vec3 ray,float travel,float base,float time,int count) {
    // Detailed tongues end at 96 blocks; the emissive surface carries distant fire.
    float enter=0.0,leave=min(travel,96.0);
    if(abs(ray.y)<.0001) {
        if(camera.y<base-6.0||camera.y>base+12.0)return vec4(0,0,0,1);
    } else {
        float a=(base-6.0-camera.y)/ray.y,b=(base+12.0-camera.y)/ray.y;
        enter=max(0.0,min(a,b));leave=min(leave,max(a,b));
    }
    if(leave<=enter)return vec4(0,0,0,1);
    vec2 middle=(camera+ray*(enter+leave)*.5).xz;
    if(max(limboSeaStyle(middle).fire,max(limboSeaStyle((camera+ray*enter).xz).fire,
            limboSeaStyle((camera+ray*leave).xz).fire))<.001)return vec4(0,0,0,1);
    float transmission=1.0;
    vec3 emission=vec3(0);
    for(int i=0;i<28;i++) {
        if(i>=count||transmission<.025)break;
        // More samples near the viewer resolve individual tongues. Fixed strata
        // keep their edges clean without speckled screen-space dithering.
        float u0=float(i)/float(count),u1=float(i+1)/float(count);
        float a=mix(enter,leave,u0*u0),b=mix(enter,leave,u1*u1);
        float stepLength=b-a,along=(a+b)*.5;
        vec3 world=camera+ray*along;
        float region=limboSeaStyle(world.xz).fire;
        if(region<.001)continue;
        float surface=base+sampleWave(world.xz,time).x;
        vec4 field=limboFireField(world,surface,time);
        field.a*=1.0-smoothstep(40.0,96.0,along);
        float opacity=1.0-exp(-field.a*region*min(stepLength,3.0)*1.4);
        emission+=transmission*opacity*field.rgb;
        transmission*=1.0-opacity;
    }
    return vec4(emission,transmission);
}
#endif
