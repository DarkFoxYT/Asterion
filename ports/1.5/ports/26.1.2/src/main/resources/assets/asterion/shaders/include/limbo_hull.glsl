// The same baked three-piece geometry as FerryHull.java and charons_ferry.geo.json.
float hullBox(vec2 p, vec2 center, vec2 halfSize, bool rotated) {
    p-=center;
    if(rotated)p=vec2(p.x-p.y,p.x+p.y)*.70710678118;
    vec2 q=abs(p)-halfSize;
    return length(max(q,0.0))+min(max(q.x,q.y),0.0);
}
float hullDistance(vec3 p) {
    bool keel=p.z < 11.0/16.0;
    float center=hullBox(p.xy,vec2(0,.4375),keel?vec2(.78125,2.375):vec2(1.0625,2.625),false);
    float bow=hullBox(p.xy,keel?vec2(0,-1.9267766953):vec2(0,-2.1919417382),vec2(keel?.5625:.75),true);
    float stern=hullBox(p.xy,keel?vec2(0,2.8232233047):vec2(.3535533906,2.9116116524),vec2(keel?.5625:.75),true);
    return min(center,min(bow,stern));
}
