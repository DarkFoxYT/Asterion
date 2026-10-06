// Depth-clipped world-space impact spray. Reuses the atmosphere framebuffer.
bool cascadeSlab(float origin,float velocity,float low,float high,inout vec2 interval) {
    if(abs(velocity)<.0001)return origin>=low && origin<=high;
    vec2 hit=vec2(low-origin,high-origin)/velocity;
    interval=vec2(max(interval.x,min(hit.x,hit.y)),min(interval.y,max(hit.x,hit.y)));
    return interval.y>interval.x;
}
vec4 limboCascadeVolume(vec3 camera,vec3 ray,float travel,float ticks,int quality) {
    float progress=16000.0-length(camera.xz-vec2(0,16018));
    float tier=clamp(floor(progress/3200.0+.5),1.0,4.0);
    float rim=tier*3200.0;
    if(abs(progress-rim)>min(travel,136.0)+24.0)return vec4(0,0,0,1);
    float lower=47.0-tier*18.0-tier*(tier-1.0)+8.0/9.0;
    vec2 inward=normalize(vec2(0,16018)-camera.xz);
    vec2 interval=vec2(0,min(travel,136.0));
    if(!cascadeSlab(camera.y,ray.y,lower-.4,lower+13.0,interval)
        || !cascadeSlab(progress-rim,dot(ray.xz,inward),-4.0,24.0,interval))return vec4(0,0,0,1);
    int count=clamp(quality*2,8,16);
    float stepLength=(interval.y-interval.x)/float(count);
    vec3 light=vec3(0);float transmission=1.0;
    for(int i=0;i<16;i++) {
        if(i>=count || transmission<.025)break;
        float along=interval.x+(float(i)+.5)*stepLength;
        vec3 p=camera+ray*along;
        float down=16000.0-length(p.xz-vec2(0,16018))-rim;
        float height=p.y-lower;
        float billow=limboAtmosphereNoise(p*vec3(.24,.32,.24)-vec3(ticks*.009,ticks*.015,-ticks*.006));
        float plume=exp(-pow((down-6.0-height*.22)/8.0,2.0))
                *(1.0-smoothstep(3.0+billow*5.0,12.0,height))*smoothstep(-.4,.8,height);
        // A shallow 3D layer of turbulent foam above the impact pool, independent
        // of view direction: visible side-on and when the camera moves inside it.
        float bubbles=limboAtmosphereNoise(p*vec3(2.8,3.6,2.8)+vec3(ticks*.035,-ticks*.06,0));
        float foam=(1.0-smoothstep(.15+bubbles*.6,1.1,height))*smoothstep(-.25,.12,height)
                *exp(-pow((down-5.0)/5.0,2.0))*smoothstep(.35,.65,bubbles);
        float density=plume*smoothstep(.24,.78,billow)*.16+foam*.85;
        float attenuation=exp(-density*stepLength);
        LimboSeaStyle sea=limboSeaStyle(p.xz);
        vec3 tint=mix(vec3(.76,.86,.93),vec3(.9,.54,.27),sea.fire*.55);
        tint*=.82+billow*.18+foam*.18;
        light+=transmission*(1.0-attenuation)*tint;
        transmission*=attenuation;
    }
    return vec4(light,transmission);
}
