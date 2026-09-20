#version 330
#moj_import <asterion:limbo_waves.glsl>
uniform sampler2D DepthSampler;
layout(std140) uniform SamplerInfo{vec2 OutSize;vec2 InSize;};
layout(std140) uniform WorldData{mat4 InvViewProj;vec4 CameraData;vec4 CameraForward;};
layout(std140) uniform UnderworldTime{float Time;};
layout(std140) uniform Intensity{float Value;};
layout(std140) uniform RiverData{vec4 River;};
layout(std140) uniform PresenceData{vec4 Presence;};
layout(std140) uniform PresenceMotion{vec4 Motion;};
in vec2 texCoord;out vec4 fragColor;
float h13(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float h12(vec2 p){vec3 q=fract(vec3(p.xyx)*.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z);}
vec3 h33(vec3 p){return vec3(h13(p+1.7),h13(p+9.2),h13(p+21.4));}
float ball(vec3 c,vec3 l,vec3 o){vec3 id=c+o,center=o+.14+h33(id)*.72;float s=mix(.36,.59,h13(id+31.0));return 1.0-smoothstep(s*.45,s,length(l-center));}
float balls(vec3 p){vec3 q=p*.13,c=floor(q),l=fract(q);float f=ball(c,l,vec3(0));f=max(f,ball(c,l,vec3(1,0,0)));f=max(f,ball(c,l,vec3(-1,0,0)));f=max(f,ball(c,l,vec3(0,1,0)));f=max(f,ball(c,l,vec3(0,-1,0)));f=max(f,ball(c,l,vec3(0,0,1)));return max(f,ball(c,l,vec3(0,0,-1)));}
float noise3(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(mix(h13(i),h13(i+vec3(1,0,0)),f.x),mix(h13(i+vec3(0,1,0)),h13(i+vec3(1,1,0)),f.x),f.y),mix(mix(h13(i+vec3(0,0,1)),h13(i+vec3(1,0,1)),f.x),mix(h13(i+vec3(0,1,1)),h13(i+vec3(1)),f.x),f.y),f.z);}
vec3 unproject(float d){float z=CameraData.w>.5?d:d*2.0-1.0;vec4 p=InvViewProj*vec4(texCoord*2.0-1.0,z,1);return p.xyz/max(abs(p.w),.00001);}
float hanging(vec3 p,float bottom,float top){float h=clamp((p.y-bottom)/max(top-bottom,.001),0.0,1.0);float broad=noise3(p*vec3(.052,.085,.052)+vec3(Time*.0007,7.0,-Time*.0005));float ridge=1.0-smoothstep(.08,.25,abs(noise3(p*vec3(.095,.12,.095)+vec3(-Time*.001,3.0,Time*.0008))-.5));float bank=smoothstep(.38,.66,broad)*smoothstep(.08,.35,h)*(1.0-smoothstep(.78,1.0,h));float curtains=ridge*smoothstep(.46,.72,broad)*(1.0-smoothstep(.12,.64,h));return bank*.62+curtains*.28;}
float middleBlobs(vec3 p,float bottom,float top){float h=(p.y-bottom)/max(top-bottom,.001);float fade=smoothstep(-.30,.12,h)*(1.0-smoothstep(.88,1.34,h));vec3 q=vec3(p.x*.78,p.y*1.25,p.z*.78)+vec3(Time*.01,0,-Time*.007);float mass=balls(q);float breakup=noise3(p*vec3(.19,.31,.19)+vec3(-Time*.001,8.0,Time*.0008));float oval=1.0-smoothstep(.34,.78,abs(h-.5));return mass*oval*fade*mix(.78,1.25,breakup);}
void main(){
 float strength=clamp(Value,0.0,1.0);if(strength<.001||CameraData.y<River.x+CameraForward.w-.25){fragColor=vec4(0,0,0,1);return;}
 float depth=texture(DepthSampler,texCoord).r;vec3 end=unproject(depth),ray=normalize(unproject(.9999)-unproject(.0001));if(dot(ray,CameraForward.xyz)<0)ray=-ray;
 float travel=depth>=.9999?144.0:min(length(end),144.0),haze=1.0-exp(-max(0.0,travel-23.0)*.088*River.z);vec3 color=vec3(.025,.042,.031)*haze;float transmission=1.0-haze;
 float cb=River.x+10.5,ct=River.x+29.0,ce=5.0,cl=min(travel,42.0);if(abs(ray.y)<.0001){if(CameraData.y<cb||CameraData.y>ct)cl=0.0;}else{float a=(cb-CameraData.y)/ray.y,b=(ct-CameraData.y)/ray.y;ce=max(5.0,min(a,b));cl=min(cl,max(a,b));}float cs=max(0.0,cl-ce),co=0.0;if(cs>.001&&River.w>.001){float st=cs/3.0,j=h12(floor(texCoord*OutSize)+19.0)*.7+.15;for(int c=0;c<3;++c){float d=ce+(float(c)+j)*st;float den=hanging(CameraData.xyz+ray*d,cb,ct);float contact=smoothstep(0.0,2.5,travel-d)*smoothstep(5.0,9.0,d)*exp(-pow(d/31.0,3.0));co+=den*contact*st*.05*River.w;}float cloud=1.0-exp(-min(co,.62));color=mix(color,vec3(.052,.083,.057),cloud);transmission*=1.0-cloud;}
 float mb=River.x+3.35,mt=River.x+12.25,mvb=mb-2.65,mvt=mt+3.05,me=4.0,ml=min(travel,39.0);if(abs(ray.y)<.0001){if(CameraData.y<mvb||CameraData.y>mvt)ml=0.0;}else{float a=(mvb-CameraData.y)/ray.y,b=(mvt-CameraData.y)/ray.y;me=max(4.0,min(a,b));ml=min(ml,max(a,b));}float ms=max(0.0,ml-me),mo=0.0;if(ms>.001&&River.w>.001){float st=ms/4.0,j=h12(floor(texCoord*OutSize)+61.0)*.7+.15;for(int m=0;m<4;++m){float d=me+(float(m)+j)*st;float den=middleBlobs(CameraData.xyz+ray*d,mb,mt);float contact=smoothstep(0.0,2.2,travel-d)*smoothstep(4.0,7.5,d)*exp(-pow(d/29.0,3.1));mo+=den*contact*st*.115*River.w;}float cloud=1.0-exp(-min(mo,1.12));color=mix(color,vec3(.052,.091,.059),cloud);transmission*=1.0-cloud;}
 float bottom=River.x-1.0,top=River.x+River.y+.7,enter=1.5,leave=min(travel,34.0);if(abs(ray.y)<.0001){if(CameraData.y<bottom||CameraData.y>top)leave=0.0;}else{float a=(bottom-CameraData.y)/ray.y,b=(top-CameraData.y)/ray.y;enter=max(1.5,min(a,b));leave=min(leave,max(a,b));}
 float span=max(0.0,leave-enter);if(span<.001||River.w<=.001){fragColor=vec4(color*strength,mix(1.0,transmission,strength));return;}
 float sa=sampleWave(CameraData.xz+ray.xz*enter,Time).x,sb=sampleWave(CameraData.xz+ray.xz*(enter+span*.5),Time).x,sc=sampleWave(CameraData.xz+ray.xz*leave,Time).x;
 float stepLength=span/11.0,jitter=h12(floor(texCoord*OutSize))*.7+.15,optical=0.0;
 for(int i=0;i<11;++i){float d=enter+(float(i)+jitter)*stepLength;vec3 p=CameraData.xyz+ray*d;float along=(d-enter)/max(span,.001);float surface=River.x+(along<.5?mix(sa,sb,along*2):mix(sb,sc,along*2-1));float h=(p.y-surface-.1)/River.y;float profile=smoothstep(-.05,.08,h)*(1.0-smoothstep(.55,1.03,h));vec3 q=p+vec3(Time*.009,0,-Time*.006);float mass=balls(q),detail=noise3(p*vec3(.58,.9,.58)+vec3(-Time*.0012,0,Time*.0015));float den=mass*profile*mix(.55,1.12,detail);vec2 pd=p.xz-CameraData.xz,fd=p.xz-Presence.xz;den*=1.0-clamp(exp(-dot(pd,pd)/5.0)*.2+Presence.w*exp(-dot(fd,fd)/34.0)*.52,0.0,.66);float contact=smoothstep(0.0,2.0,travel-d)*smoothstep(1.5,4.5,d)*exp(-pow(d/18.0,3.2));optical+=den*contact*stepLength*.18*River.w;}
 float mist=1.0-exp(-min(optical,1.08));color=mix(color,mix(vec3(.07,.11,.078),vec3(.26,.335,.25),mist*.74),mist);transmission*=1.0-mist;fragColor=vec4(color*strength,mix(1.0,transmission,strength));
}
