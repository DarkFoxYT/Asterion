// World-space adaptation of the supplied hashed-cell expanding rain rings.
// Hash family: https://www.shadertoy.com/view/4djSRW (Dave Hoskins).
vec2 rainHash22(vec2 p) {
    vec3 q=fract(vec3(p.xyx)*vec3(.1031,.1030,.0973));
    q+=dot(q,q.yzx+19.19);
    return fract((q.xx+q.yz)*q.zy);
}
float rainRing(float d) {
    // Ordered smoothstep edges: reversed GLSL smoothstep is undefined.
    return sin(31.0*d)*smoothstep(-.6,-.3,d)*(1.0-smoothstep(-.3,0.0,d));
}
vec2 rainRipples(vec2 world, float ticks, float quality) {
    vec2 uv=world*1.7, cell=floor(uv), normal=vec2(0);
    int radius=quality>.5?2:1;
    for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++) {
        if(abs(x)>radius||abs(y)>radius)continue;
        vec2 c=cell+vec2(x,y), h=rainHash22(c);
        float t=fract(ticks*.027+h.x*.73+h.y*.27);
        vec2 v=c+h-uv;
        float len=length(v), d=len-3.0*t;
        float slope=(rainRing(d+.001)-rainRing(d-.001))/.002;
        normal+=v/max(len,.0001)*slope*(1.0-t)*(1.0-t)*.5;
    }
    return clamp(normal/25.0,vec2(-1.5),vec2(1.5));
}
