// Integer-hashed, continuously interpolated noise, mirrored by UnderworldWaves on the server.
float seaTempestStrength = -1.0;
float seaWhirlpoolStrength = -1.0;
vec2 seaWhirlpoolCenter = vec2(16.0, 352.0);
float limboTempest(float ticks) {
    if (seaTempestStrength >= 0.0) return seaTempestStrength;
    float phase = mod(ticks - 12000.0 + 24000.0, 24000.0);
    return (1.0 - step(3600.0, phase)) * smoothstep(0.0, 320.0, phase)
            * (1.0 - smoothstep(3120.0, 3600.0, phase));
}
float limboWhirlpool(float ticks) {
    if (seaWhirlpoolStrength >= 0.0) return seaWhirlpoolStrength;
    float phase = mod(ticks - 20000.0 + 48000.0, 48000.0);
    return (1.0 - step(28000.0, phase)) * smoothstep(0.0, 1200.0, phase)
            * (1.0 - smoothstep(26400.0, 28000.0, phase));
}
float whirlFunnel(vec2 p, float ticks) {
    float r = length(p - seaWhirlpoolCenter);
    if (r >= 120.0) return 0.0;
    float edge = 1.0 - smoothstep(24.0, 120.0, r);
    return -10.0 * limboWhirlpool(ticks) * exp(-r / 55.0) * edge;
}
float waveHash(ivec2 p) {
    uint h = uint(p.x) * 0x1f123bb5u ^ uint(p.y) * 0x5f356495u;
    h ^= h >> 16; h *= 0x45d9f3bu; h ^= h >> 16;
    return float(h & 65535u) / 65535.0;
}
vec3 waveNoise(vec2 p) {
    ivec2 i = ivec2(floor(p)); vec2 f = fract(p), u = f*f*(3.0-2.0*f);
    float a=waveHash(i), b=waveHash(i+ivec2(1,0)), c=waveHash(i+ivec2(0,1)), d=waveHash(i+ivec2(1));
    return vec3(a+(b-a)*u.x+(c-a)*u.y+(a-b-c+d)*u.x*u.y,
        mix(b-a,d-c,u.y)*6.0*f.x*(1.0-f.x), mix(c-a,d-b,u.x)*6.0*f.y*(1.0-f.y));
}
vec4 swell(vec2 p, float ticks, vec2 k, float baseAmplitude, float offset, vec3 phase, float warp, vec3 packet) {
    float amplitude=baseAmplitude*(.8+.4*packet.x);
    float lengthK = length(k);
    float angle = dot(k,p)-sqrt(9.81*lengthK)*ticks/20.0+offset+(phase.x-.5)*warp;
    float c=cos(angle), s=sin(angle), c2=2.0*c*c-1.0, s2=2.0*s*c;
    float height=amplitude*(c+.5*lengthK*amplitude*c2);
    vec2 slope=-amplitude*(s+lengthK*amplitude*s2)*(k+phase.yz*.055*warp);
    slope+=baseAmplitude*.4*.055*(c+lengthK*amplitude*c2)*packet.yz;
    float curvature=amplitude*lengthK*lengthK*(c+2.0*lengthK*amplitude*c2);
    return vec4(height,slope,curvature);
}
vec4 sampleWave(vec2 p,float ticks) {
    float storm = limboTempest(ticks);
    float funnel = whirlFunnel(p, ticks);
    float seaZ = p.y;
    p *= .65;
    ticks *= .42;
    vec3 phase=waveNoise(p*.055+vec2(ticks*.0009,-ticks*.0006));
    vec3 phaseB=waveNoise(p*.055+vec2(-ticks*.0007+71.0,ticks*.0008-43.0));
    vec3 phaseC=waveNoise(p*.055+vec2(ticks*.0003+137.0,-ticks*.0005+89.0));
    vec3 phaseD=(phase+phaseB)*.5;
    vec3 energy=waveNoise(p*.011+vec2(-ticks*.00035+31.0,ticks*.0005+19.0));
    float t=clamp((seaZ-55.0)/125.0,0.0,1.0);
    float exposure=.12+.88*t*t*(3.0-2.0*t), group=.55+.45*energy.x;
    float boost = 1.0 + storm * .85;
    vec4 w=swell(p,ticks,vec2(.065,.042),1.75*boost,.20,phase,8.0,phaseB)
          +swell(p,ticks,vec2(-.088,.052),1.22*boost,1.8,phaseB,-9.0,phaseC)
          +swell(p,ticks,vec2(.039,-.148),.78*boost,3.1,phaseC,7.0,phase)
          +swell(p,ticks,vec2(-.19,-.083),.36*boost,.7,phaseD,-8.0,phaseB);
    vec2 envelopeSlope=exposure*.45*energy.yz*.011*.65;
    envelopeSlope.y+=group*.88*6.0*t*(1.0-t)/125.0;
    vec4 result=vec4(w.x*exposure*group,w.yz*exposure*group*.65+w.x*envelopeSlope,w.w*exposure*group*.4225);
    float limit = 2.8 + storm * 1.7, softness = 1.1 + storm * .9;
    if(abs(result.x)>limit) {
        float bend=tanh((abs(result.x)-limit)/softness);
        result.x=sign(result.x)*(limit+softness*bend);result.yzw*=1.0-bend*bend;
    }
    result.x += funnel;
    return result;
}
