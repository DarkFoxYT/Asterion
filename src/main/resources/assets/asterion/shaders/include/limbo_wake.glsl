uniform sampler2D Sampler1;
vec2 wakeTexel(ivec2 p) {
    return texelFetch(Sampler1, clamp(p, ivec2(0), ivec2(127)), 0).rg;
}
vec2 wakeCubic(vec2 a, vec2 b, vec2 c, vec2 d, float t) {
    // Catmull-Rom reconstruction removes the half-block stair steps while
    // preserving the thin twin trails better than a broad blur would.
    return b + .5 * t * (c - a + t * (2.0*a - 5.0*b + 4.0*c - d
            + t * (3.0*(b-c) + d-a)));
}
vec2 persistentWake(vec2 world) {
    vec4 header = floor(texelFetch(Sampler1, ivec2(0,128),0)*255.0+.5);
    vec2 origin = vec2(header.r*256.0+header.g,header.b*256.0+header.a)/16.0-2048.0;
    vec2 p=(world-origin)*2.0-.5;
    if(any(lessThan(p,vec2(0))) || any(greaterThan(p,vec2(126))))return vec2(0);
    ivec2 i=ivec2(floor(p));vec2 f=fract(p);
    vec2 rows[4];
    for(int y=-1;y<=2;y++) {
        rows[y+1]=wakeCubic(wakeTexel(i+ivec2(-1,y)),wakeTexel(i+ivec2(0,y)),
                wakeTexel(i+ivec2(1,y)),wakeTexel(i+ivec2(2,y)),f.x);
    }
    vec2 field=clamp(wakeCubic(rows[0],rows[1],rows[2],rows[3],f.y),vec2(0),vec2(1));
    return vec2(field.x,(field.y*255.0-128.0)*.001);
}
