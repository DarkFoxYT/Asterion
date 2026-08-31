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

void main() {
    vec4 scene=texture(SceneSampler,texCoord);
    float depth=texture(DepthSampler,texCoord).r;
    float z=CameraData.w>.5 ? depth : depth*2.0-1.0;
    vec4 world=InvViewProj*vec4(texCoord*2.0-1.0,z,1.0);
    world.xyz/=max(abs(world.w),.00001);
    float travel=depth>=.9999 ? 112.0 : min(length(world.xyz-CameraData.xyz),112.0);
    // One stable world-space sample replaces the ten-step ray march when under load.
    vec2 cell=floor((world.xz+CameraData.xz)*.035+Time*.002);
    float variation=.82+hash(cell)*.28;
    float optical=clamp(travel*.0065*Settings.x*Settings.y*variation,0.0,.88);
    float transmission=exp(-optical);
    float luma=dot(scene.rgb,vec3(.2126,.7152,.0722));
    float light=smoothstep(.12,.72,luma);
    transmission=mix(transmission,1.0,light*.45);
    vec3 fog=mix(FogTint,DustTint,.28+hash(cell+17.0)*.12);
    vec3 result=scene.rgb*transmission+fog*(1.0-transmission);
    fragColor=vec4(mix(scene.rgb,result,clamp(Value*EffectStrength,0.0,1.0)),scene.a);
}
