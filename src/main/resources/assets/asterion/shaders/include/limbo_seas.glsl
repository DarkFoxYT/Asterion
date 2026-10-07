#ifndef ASTERION_LIMBO_SEAS
#define ASTERION_LIMBO_SEAS
// Keep layout and quintic transitions in sync with LimboSeaRegions.java.
struct LimboSeaStyle { vec3 water; vec3 reflection; vec3 fog; float density; float fire; float oblivion; float tears; float grief; };
float limboSeaDistance(vec2 p) {
    return max(0.0,16000.0-length(p-vec2(0,16018)));
}
float limboSeaWaterY(vec2 p) {
    float tier=clamp(floor(limboSeaDistance(p)/3200.0),0.0,4.0);
    return 47.0-tier*18.0-tier*(tier-1.0)+8.0/9.0;
}
// Preserve the measured camera surface offset while following each local terrace.
float limboLocalSurface(vec2 world,vec2 camera,float base) {
    return base+limboSeaWaterY(world)-limboSeaWaterY(camera);
}
vec2 limboVolumeHeightRange(vec3 camera,vec3 ray,float travel,float base) {
    float end=limboLocalSurface((camera+ray*travel).xz,camera.xz,base);
    float middle=limboLocalSurface((camera+ray*travel*.5).xz,camera.xz,base);
    return vec2(min(base,min(end,middle)),max(base,max(end,middle)));
}
float limboSeaTransition(float d,float edge) {
    float t=clamp((d-(edge-320.0))/640.0,0.0,1.0);
    return t*t*t*(t*(t*6.0-15.0)+10.0);
}
float limboCascadeFoam(vec2 p,float ticks) {
    float d=limboSeaDistance(p);
    float boundary=clamp(floor(d/3200.0+.5),1.0,4.0)*3200.0;
    float edge=d-boundary;
    if(abs(edge)>18.0)return 0.0;
    float band=edge<0.0 ? 1.0-smoothstep(0.0,4.0,-edge) : 1.0-smoothstep(2.0,18.0,edge);
    float rings=.5+.5*sin(edge*2.4-ticks*.19+sin(dot(p,vec2(.73,.41)))*1.1);
    return band*(.68+.30*rings);
}
LimboSeaStyle limboSeaStyle(vec2 p) {
    float d=limboSeaDistance(p);
    LimboSeaStyle s=LimboSeaStyle(vec3(.0045,.0052,.0058),vec3(.24,.26,.275),vec3(.034,.038,.044),1.0,0.0,0.0,0.0,0.0);
    float f=limboSeaTransition(d,3200.0),l=limboSeaTransition(d,6400.0),a=limboSeaTransition(d,9600.0),c=limboSeaTransition(d,12800.0);
    s.water=mix(s.water,vec3(.14,.014,.004),f);s.reflection=mix(s.reflection,vec3(.9,.24,.035),f);s.fog=mix(s.fog,vec3(.14,.035,.018),f);s.density=mix(s.density,.8,f);s.fire=f;
    s.water=mix(s.water,vec3(.24,.205,.15),l);s.reflection=mix(s.reflection,vec3(.64,.58,.44),l);s.fog=mix(s.fog,vec3(.29,.265,.215),l);s.density=mix(s.density,1.35,l);s.oblivion=l;s.fire*=1.0-l;
    s.water=mix(s.water,vec3(.018,.095,.031),a);s.reflection=mix(s.reflection,vec3(.32,.43,.13),a);s.fog=mix(s.fog,vec3(.09,.16,.075),a);s.density=mix(s.density,1.12,a);s.oblivion*=1.0-a;s.grief=a;
    s.water=mix(s.water,vec3(.075,.095,.115),c);s.reflection=mix(s.reflection,vec3(.58,.64,.69),c);s.fog=mix(s.fog,vec3(.23,.265,.29),c);s.density=mix(s.density,.85,c);s.tears=c;s.grief*=1.0-c;
    return s;
}
// Sample along the view ray, including distant ocean sky. This is not a
// fullscreen tint chosen by the biome underneath the player.
vec3 limboSeaHorizon(vec3 camera,vec3 ray) {
    vec3 sum=vec3(0);
    for(int i=0;i<4;i++)sum+=limboSeaStyle((camera+ray*(180.0+float(i)*420.0)).xz).fog;
    return sum*.25;
}
#endif
