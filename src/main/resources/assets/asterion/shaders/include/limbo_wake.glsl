uniform sampler2D Sampler1;
vec2 persistentWake(vec2 world) {
    vec4 header = floor(texelFetch(Sampler1, ivec2(0,128),0)*255.0+.5);
    vec2 origin = vec2(header.r*256.0+header.g,header.b*256.0+header.a)/16.0-2048.0;
    vec2 p=(world-origin)*2.0-.5;
    if(any(lessThan(p,vec2(0))) || any(greaterThan(p,vec2(126))))return vec2(0);
    ivec2 i=ivec2(floor(p));vec2 f=fract(p);
    vec2 a=mix(texelFetch(Sampler1,i,0).rg,texelFetch(Sampler1,i+ivec2(1,0),0).rg,f.x);
    vec2 b=mix(texelFetch(Sampler1,i+ivec2(0,1),0).rg,texelFetch(Sampler1,i+ivec2(1),0).rg,f.x);
    vec2 field=mix(a,b,f.y);
    return vec2(field.x,(field.y*255.0-128.0)*.001);
}
