#version 330

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
layout(std140) uniform DustTime { float Time; };
layout(std140) uniform Intensity { float Value; };
layout(std140) uniform AsterionStrength { float EffectStrength; };
layout(std140) uniform AtmosphereSettings { vec3 Settings; };
layout(std140) uniform DustColor { vec3 DustTint; };
layout(std140) uniform FogColor { vec3 FogTint; };
in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3=fract(vec3(p.xyx)*.1031);
    p3+=dot(p3,p3.yzx+33.33);
    return fract((p3.x+p3.y)*p3.z);
}

float noise(vec2 p) {
    vec2 cell=floor(p), f=fract(p);
    f=f*f*(3.0-2.0*f);
    return mix(mix(hash(cell),hash(cell+vec2(1,0)),f.x),
               mix(hash(cell+vec2(0,1)),hash(cell+vec2(1)),f.x),f.y);
}

void main() {
    vec4 scene=texture(SceneSampler,texCoord);
    float depth=texture(DepthSampler,texCoord).r;
    float z=CameraData.w>.5 ? depth : depth*2.0-1.0;
    vec4 world=InvViewProj*vec4(texCoord*2.0-1.0,z,1.0);
    world.xyz/=abs(world.w)<.00001 ? .00001 : world.w;
    // The inverse matrix reconstructs a camera-relative position. Limit sky rays
    // to the same distance as the volumetric path, then sample in world space.
    float travel=depth>=.9999 ? 112.0 : min(length(world.xyz),112.0);
    vec3 sampleWorld=CameraData.xyz+normalize(world.xyz)*travel*.5;
    vec2 cell=sampleWorld.xz*.035+Time*.002*Settings.z;
    float variation=.82+noise(cell)*.28;
    float optical=max(travel*.0065*Settings.x*Settings.y*variation,0.0);
    float transmission=exp(-optical);
    float luma=dot(scene.rgb,vec3(.2126,.7152,.0722));
    float light=smoothstep(.12,.72,luma);
    transmission=mix(transmission,1.0,light*.45);
    vec3 fog=mix(FogTint,DustTint,.28+noise(cell+17.0)*.12);
    float grey=dot(fog,vec3(.2126,.7152,.0722));
    // Match the ash-grey distant atmosphere of the volumetric qualities.
    // Very dark biome/eclipse tints remain dark.
    float openAir=smoothstep(.025,.10,grey);
    fog=mix(fog,vec3(max(grey,.24*openAir)),smoothstep(64.0,112.0,travel));
    vec3 result=scene.rgb*transmission+fog*(1.0-transmission);
    fragColor=vec4(mix(scene.rgb,result,clamp(Value*EffectStrength,0.0,1.0)),scene.a);
}
