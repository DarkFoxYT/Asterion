#version 330
#moj_import <asterion:limbo_waves.glsl>

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
layout(std140) uniform UnderworldTime { float Time; };
layout(std140) uniform Intensity { float Value; };
layout(std140) uniform RiverData { vec4 River; };
layout(std140) uniform PresenceData { vec4 Presence; };
layout(std140) uniform PresenceMotion { vec4 Motion; };
in vec2 texCoord;
out vec4 fragColor;

const float TAU=6.28318530718;

float hash12(vec2 p){vec3 q=fract(vec3(p.xyx)*.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z);}
float hash13(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
vec3 hash33(vec3 p){return vec3(hash13(p+1.7),hash13(p+9.2),hash13(p+21.4));}
float puffBall(vec3 cell,vec3 local,vec3 offset){vec3 id=cell+offset,center=offset+.12+hash33(id)*.76;float radius=mix(.38,.68,hash13(id+31.0));vec3 d=(local-center)*vec3(1.0,1.34,1.0);return 1.0-smoothstep(radius*.52,radius,length(d));}
float puffBalls(vec3 p){vec3 q=p*.105,cell=floor(q),local=fract(q);float f=puffBall(cell,local,vec3(0));f=max(f,puffBall(cell,local,vec3(1,0,0)));f=max(f,puffBall(cell,local,vec3(-1,0,0)));f=max(f,puffBall(cell,local,vec3(0,1,0)));f=max(f,puffBall(cell,local,vec3(0,-1,0)));f=max(f,puffBall(cell,local,vec3(0,0,1)));return max(f,puffBall(cell,local,vec3(0,0,-1)));}
float atlasNoise(vec3 p){
    vec2 uv=p.xz+vec2(p.y*.071,-p.y*.053);
    return texture(NoiseSampler,fract(uv)).r;
}

vec3 unproject(float d){float z=CameraData.w>.5?d:d*2.0-1.0;vec4 p=InvViewProj*vec4(texCoord*2.0-1.0,z,1);return p.xyz/max(abs(p.w),.00001);}

vec3 disturb(vec3 p,out float clearing,out float churn){
    vec2 pd=p.xz-CameraData.xz;float pl=length(pd),pf=exp(-pl*pl/5.0);vec2 pt=vec2(-pd.y,pd.x)/max(pl,.5);
    vec2 fd=p.xz-Presence.xz;float fl=length(fd),ff=Presence.w*exp(-fl*fl/36.0);vec2 ft=vec2(-fd.y,fd.x)/max(fl,.7);
    float ps=min(length(Motion.xy)*5.0,1.0),fs=min(length(Motion.zw)*8.0,1.0);
    vec2 pdir=length(Motion.xy)>.001?normalize(Motion.xy):vec2(0,1);
    float palong=dot(pd,pdir),pside=dot(pd,vec2(-pdir.y,pdir.x));
    float ptrail=ps*(1.0-smoothstep(0.0,3.2,abs(pside)))*smoothstep(-13.0,-2.0,palong)*(1.0-smoothstep(-2.0,1.5,palong));
    vec2 fdir=length(Motion.zw)>.001?normalize(Motion.zw):vec2(0,1);
    float falong=dot(fd,fdir),fside=dot(fd,vec2(-fdir.y,fdir.x));
    float ftrail=Presence.w*fs*(1.0-smoothstep(0.0,5.2,abs(fside)))*smoothstep(-24.0,-3.0,falong)*(1.0-smoothstep(-3.0,2.0,falong));
    float verticalReach=exp(-abs(p.y-CameraData.y)*.075);
    p.xz+=pt*pf*(.24+ps*.62)-Motion.xy*pf*1.35+ft*ff*(.38+fs*.82)-Motion.zw*ff*2.55;
    p.xz+=vec2(-pdir.y,pdir.x)*sign(pside)*ptrail*.72+vec2(-fdir.y,fdir.x)*sign(fside)*ftrail*1.35;
    p.xz-=(pdir*ptrail*.48+fdir*ftrail*.92)*verticalReach;
    p.y+=pf*sin(atan(pd.y,pd.x)*2.0+Time*.045)*(.08+ps*.24);
    clearing=clamp(pf*.06+ff*.29+ptrail*.08+ftrail*.16,0.0,.38);
    churn=clamp((pf*(.38+ps)+ff*(.35+fs)+ptrail*.72+ftrail)*verticalReach,0.0,1.0);return p;
}

vec2 advectedFlow(vec3 p){
    vec2 q=p.xz*.115;
    float t=Time*.012;
    vec2 v=vec2(sin(q.y*1.7+t)+cos(q.x*.83-t*.71),
                -cos(q.x*1.43-t*.84)+sin(q.y*.91+t*.63));
    vec2 r=mat2(.643,-.766,.766,.643)*q*2.17;
    v+=vec2(sin(r.y-t*1.09),-cos(r.x+t*.77))*.43;
    return v*.34;
}

vec2 livingVortex(vec3 p){
    const float cellSize=11.0;
    vec2 id=floor(p.xz/cellSize),rnd=vec2(hash12(id+4.7),hash12(id+19.3));
    vec2 center=(id+.28+rnd*.44)*cellSize;
    vec2 d=p.xz-center;float radius=length(d);
    float envelope=1.0-smoothstep(1.1,4.8,radius);
    float direction=hash12(id+31.8)>.5?1.0:-1.0;
    float phase=Time*(.018+hash12(id+8.2)*.018)+hash12(id)*TAU;
    vec2 tangent=vec2(-d.y,d.x)/max(radius,.35);
    vec2 breathe=d/max(radius,.35)*sin(phase+radius*1.35)*.28;
    return (tangent*direction*(.46+.24*sin(phase*.7))+breathe)*envelope;
}

float densityAt(vec3 p,float surface,out float lighting){
    float h=(p.y-surface-.1)/River.y;
    float vertical=smoothstep(-.06,.07,h)*(1.0-smoothstep(.57,.96,h));
    vec2 flow=advectedFlow(p);
    vec2 vortex=livingVortex(p);
    p.xz+=flow*mix(.34,.88,smoothstep(.05,.82,h));
    p.xz+=vortex*mix(.32,1.45,smoothstep(.18,1.22,h));
    p.y+=sin(length(vortex)*4.0+h*9.0-Time*.022)*length(vortex)*.17;
    vec3 windA=p*vec3(.018,.08,.018)+vec3(Time*.00035,0,-Time*.00024);
    vec3 windB=p*vec3(.041,.13,.041)+vec3(-Time*.00021,2.7,Time*.00031);
    windB.xz=mat2(.766,.643,-.643,.766)*windB.xz;
    float broad=atlasNoise(windA);
    float shape=atlasNoise(windB);
    float detail=atlasNoise(p*vec3(.083,.19,.083)+vec3(Time*.00042,5.1,-Time*.00037));
    float micro=atlasNoise(p*vec3(.19,.31,.19)+vec3(-Time*.0007,9.3,Time*.00058));
    float mass=smoothstep(.27,.67,broad*.62+shape*.48);
    float layered=shape*.49+detail*.34+micro*.17;
    float erosion=mix(.52,1.12,smoothstep(.28,.74,layered));
    float lifeNoise=atlasNoise(p*vec3(.012,.04,.012)+vec3(Time*.00012,13.1,-Time*.00009));
    float breathing=.88+.20*smoothstep(.2,.8,lifeNoise);
    float topHeight=.54+(broad-.5)*.14+(shape-.5)*.10;
    float blanket=smoothstep(-.05,.07,h)*(1.0-smoothstep(topHeight-.11,topHeight+.06,h));
    float body=blanket*mix(.68,1.08,smoothstep(.24,.68,mass+layered*.22));
    float crestBand=smoothstep(topHeight-.10,topHeight+.02,h)*(1.0-smoothstep(topHeight+.03,topHeight+.34,h));
    float crests=mass*crestBand*smoothstep(.47,.72,layered)*.58;
    float wisps=smoothstep(.48,.65,h)*(1.0-smoothstep(.96,1.38,h))*smoothstep(.58,.80,layered);
    vec2 curlOffset=vec2(sin(h*8.2+Time*.014),cos(h*6.7-Time*.011))*mix(.25,1.7,smoothstep(.42,1.75,h));
    vec3 tendrilPos=vec3(p.xz*.047+curlOffset*.035+flow*.04,p.y*.09+Time*.0003);
    float ridgeA=1.0-smoothstep(.06,.22,abs(atlasNoise(tendrilPos)-.5));
    tendrilPos.xz=mat2(.342,-.94,.94,.342)*tendrilPos.xz*1.37-curlOffset*.018;
    float ridgeB=1.0-smoothstep(.06,.21,abs(atlasNoise(tendrilPos+vec3(7.2,-Time*.0005,3.1))-.5));
    float rise=smoothstep(.34,.62,h)*(1.0-smoothstep(1.06,1.52,h));
    float tendrils=max(ridgeA,ridgeB*.78)*rise*smoothstep(.43,.69,shape)*.28;
    float particles=smoothstep(.75,.91,detail*.58+micro*.52)*mass
            * (vertical+crestBand*.7)*.22;
    float shedding=smoothstep(.77,.91,micro)*smoothstep(topHeight-.02,topHeight+.18,h)
            *(1.0-smoothstep(topHeight+.22,topHeight+.62,h))*(.08+.22*lifeNoise);
    float crownFade=(1.0-smoothstep(.72,1.42,h))*smoothstep(.52,.76,layered);
    float crowns=mass*crownFade*smoothstep(.57,.78,h)*.27;
    float directionalLight=clamp(.13+(shape-detail)*.27+(micro-.5)*.12,0.0,.48);
    lighting=clamp(directionalLight+wisps*.34+crowns*.18+tendrils*.60+particles*.92
            +shedding*.75-crests*.16,0.0,1.0);
    return (body*erosion+crests)*breathing+wisps*.13+crowns*.72+tendrils*.76+particles+shedding;
}

// A second atmosphere hangs well above the river mist.  Long, sparse curtains
// descend from its underside so looking across the cavern has moving depth in
// front of the camera, while the first few blocks around the player stay clear.
float hangingDensity(vec3 p,float bottom,float top,out float glow){
    float h=clamp((p.y-bottom)/max(top-bottom,.001),0.0,1.0);
    vec3 driftA=p*vec3(.014,.035,.014)+vec3(Time*.00018,17.3,-Time*.00013);
    vec3 driftB=p*vec3(.037,.074,.037)+vec3(-Time*.00031,4.7,Time*.00022);
    float broad=atlasNoise(driftA),fold=atlasNoise(driftB);
    float torn=atlasNoise(p*vec3(.081,.12,.081)+vec3(Time*.00042,11.1,-Time*.00035));
    float ceiling=smoothstep(.12,.42,h)*(1.0-smoothstep(.78,1.0,h));
    float bank=smoothstep(.34,.66,broad*.68+fold*.46)*ceiling;
    float underside=1.0-smoothstep(.18,.58,h);
    float ridges=1.0-smoothstep(.055,.19,abs(fold-.5));
    float curtainLength=mix(.18,.74,smoothstep(.38,.78,broad));
    float curtains=ridges*smoothstep(.27,.57,torn)*underside
            *(1.0-smoothstep(curtainLength,curtainLength+.16,h));
    float pulse=.88+.12*sin(Time*.006+broad*6.283+p.x*.025-p.z*.019);
    glow=clamp((fold-torn)*.35+curtains*.28+.12,0.0,.58);
    return (bank*.58+curtains*.42)*pulse;
}

// Dense rounded islands bridge the water mist into the hanging canopy while
// retaining their own slower motion and large, readable silhouettes.
float middleBlobDensity(vec3 p,float bottom,float top,out float glow){
    float h=(p.y-bottom)/max(top-bottom,.001);
    float verticalFade=smoothstep(-.30,.12,h)*(1.0-smoothstep(.88,1.34,h));
    vec3 drift=vec3(Time*.00055,0.0,-Time*.00038);
    float broad=atlasNoise(p*vec3(.024,.075,.024)+drift+vec3(5.2,1.7,9.4));
    float shape=atlasNoise(p*vec3(.057,.14,.057)-drift*.73+vec3(13.1,4.6,2.8));
    float detail=atlasNoise(p*vec3(.115,.23,.115)+drift*1.31+vec3(1.4,8.3,16.7));
    vec3 puffPosition=p+vec3(Time*.010,0.0,-Time*.0065);
    puffPosition.xz+=vec2(shape-.5,broad-.5)*1.7;
    float spheres=puffBalls(puffPosition);
    float center=.50+(broad-.5)*.13;
    float oval=1.0-smoothstep(.36,.70,abs(h-center));
    float softFill=smoothstep(.34,.62,broad*.61+shape*.54+detail*.10);
    float joined=max(spheres,softFill*.58);
    float puffy=smoothstep(.08,.76,joined)*oval;
    float scallops=smoothstep(.24,.82,spheres+.22*detail);
    float edge=clamp(puffy*mix(.78,1.25,scallops)*verticalFade,0.0,1.18);
    glow=clamp(.08+(shape-detail)*.22+scallops*.34,0.0,.58);
    return edge*(1.02+.34*smoothstep(.28,.78,broad));
}

void main(){
    float strength=clamp(Value,0.0,1.0);
    if(strength<.001||CameraData.y<River.x+CameraForward.w-.25){fragColor=vec4(0,0,0,1);return;}
    float depth=texture(DepthSampler,texCoord).r;vec3 end=unproject(depth),ray=normalize(unproject(.9999)-unproject(.0001));
    if(dot(ray,CameraForward.xyz)<0.0)ray=-ray;
    float travel=depth>=.9999?160.0:min(length(end),160.0),haze=1.0-exp(-max(0.0,travel-23.0)*.088*River.z);
    vec3 color=vec3(.004,.0045,.005)*haze;float transmission=1.0-haze;
    float canopyBottom=River.x+10.5,canopyTop=River.x+29.0;
    float canopyEnter=5.0,canopyLeave=min(travel,96.0);
    if(abs(ray.y)<.0001){if(CameraData.y<canopyBottom||CameraData.y>canopyTop)canopyLeave=0.0;}
    else{float ca=(canopyBottom-CameraData.y)/ray.y,cb=(canopyTop-CameraData.y)/ray.y;canopyEnter=max(5.0,min(ca,cb));canopyLeave=min(canopyLeave,max(ca,cb));}
    float canopySpan=max(0.0,canopyLeave-canopyEnter),canopyOptical=0.0,canopyLight=0.0,canopyChurn=0.0;
    if(canopySpan>.001&&River.w>.001){
        float canopyStep=canopySpan/7.0,canopyJitter=hash12(floor(texCoord*OutSize)+37.0)*.72+.14;
        for(int c=0;c<7;++c){
            float d=canopyEnter+(float(c)+canopyJitter)*canopyStep;vec3 p=CameraData.xyz+ray*d;float lit,clearing,churn;p=disturb(p,clearing,churn);
            float den=hangingDensity(p,canopyBottom,canopyTop,lit);
            den*=1.0-clearing*.55;
            float contact=smoothstep(0.0,2.5,travel-d)*smoothstep(5.0,9.0,d);
            canopyOptical+=den*contact*canopyStep*.052*River.w;
            canopyLight+=den*contact*(.12+lit)*canopyStep*.035;
            canopyChurn+=den*contact*churn*canopyStep*.018;
        }
        float canopy=1.0-exp(-min(canopyOptical,.72));
        vec3 canopyColor=mix(vec3(.018,.019,.021),vec3(.29,.305,.315),1.0-exp(-canopyLight));
        canopyColor=mix(canopyColor,vec3(.72,.73,.74),clamp(1.0-exp(-canopyChurn),0.0,.16));
        color=mix(color,canopyColor,canopy);transmission*=1.0-canopy;
    }
    float middleBottom=River.x+3.35,middleTop=River.x+12.25;
    float middleVolumeBottom=middleBottom-2.65,middleVolumeTop=middleTop+3.05;
    float middleEnter=4.0,middleLeave=min(travel,88.0);
    if(abs(ray.y)<.0001){if(CameraData.y<middleVolumeBottom||CameraData.y>middleVolumeTop)middleLeave=0.0;}
    else{float ma=(middleVolumeBottom-CameraData.y)/ray.y,mb=(middleVolumeTop-CameraData.y)/ray.y;middleEnter=max(4.0,min(ma,mb));middleLeave=min(middleLeave,max(ma,mb));}
    float middleSpan=max(0.0,middleLeave-middleEnter),middleOptical=0.0,middleLight=0.0,middleChurn=0.0;
    if(middleSpan>.001&&River.w>.001){
        // Stable midpoint sampling keeps the puffs in world space. Screen-pixel
        // jitter made this particular band look like a texture following the camera.
        float middleStep=middleSpan/8.0,middleJitter=.5;
        for(int m=0;m<8;++m){
            float d=middleEnter+(float(m)+middleJitter)*middleStep;vec3 p=CameraData.xyz+ray*d;float lit,clearing,churn;p=disturb(p,clearing,churn);
            float den=middleBlobDensity(p,middleBottom,middleTop,lit);
            den*=1.0-clearing*.72;
            float contact=smoothstep(0.0,2.2,travel-d)*smoothstep(4.0,7.5,d);
            middleOptical+=den*contact*middleStep*.128*River.w;
            middleLight+=den*contact*(.12+lit)*middleStep*.052;
            middleChurn+=den*contact*churn*middleStep*.026;
        }
        float middleFog=1.0-exp(-min(middleOptical,1.28));
        vec3 middleColor=mix(vec3(.016,.017,.019),vec3(.35,.365,.375),1.0-exp(-middleLight));
        middleColor=mix(middleColor,vec3(.78,.79,.80),clamp(1.0-exp(-middleChurn),0.0,.22));
        color=mix(color,middleColor,middleFog);transmission*=1.0-middleFog;
    }
    float bottom=River.x-1.0,top=River.x+River.y*1.55,enter=1.5,leave=min(travel,72.0);
    if(abs(ray.y)<.0001){if(CameraData.y<bottom||CameraData.y>top)leave=0.0;}else{float a=(bottom-CameraData.y)/ray.y,b=(top-CameraData.y)/ray.y;enter=max(1.5,min(a,b));leave=min(leave,max(a,b));}
    float span=max(0.0,leave-enter);if(span<.001||River.w<=.001){fragColor=vec4(color*strength,mix(1.0,transmission,strength));return;}
    float sa=sampleWave(CameraData.xz+ray.xz*enter,Time).x,sb=sampleWave(CameraData.xz+ray.xz*(enter+span*.5),Time).x,sc=sampleWave(CameraData.xz+ray.xz*leave,Time).x;
    // Looking down crosses the shallowest part of the volume. Compensate for that
    // shorter path so the rounded cloud piles stay readable from cliffs and flight.
    float overhead=smoothstep(.18,.92,-ray.y);
    float viewDensity=mix(1.0,1.68,overhead);
    float stepLength=span/16.0,jitter=hash12(floor(texCoord*OutSize))*.74+.13,optical=0.0,light=0.0,lowChurn=0.0;
    for(int i=0;i<16;++i){
        float d=enter+(float(i)+jitter)*stepLength;vec3 p=CameraData.xyz+ray*d;float along=(d-enter)/max(span,.001);
        float surface=River.x+(along<.5?mix(sa,sb,along*2.0):mix(sb,sc,along*2.0-1.0));
        float clearing,churn;p=disturb(p,clearing,churn);float lit;
        float den=densityAt(p,surface,lit)*(1.0-clearing)*mix(1.0,1.32,churn*(.35+lit));
        float contact=smoothstep(0.0,2.0,travel-d)*smoothstep(1.5,4.5,d);
        float sliceDensity=den*contact*stepLength*.245*River.w*viewDensity;
        // Front-to-back extinction makes deep lobes shade the slices behind them,
        // matching the reference's sliced volumetric self-shadowing model.
        float sliceTransmittance=exp(-optical*1.45);
        light+=den*contact*(.12+lit+overhead*.16)*stepLength*.09*viewDensity*sliceTransmittance;
        lowChurn+=den*contact*churn*stepLength*.034*sliceTransmittance;
        optical+=sliceDensity;
        if(optical>1.48)break;
    }
    // Beer-Lambert extinction plus a compact Henyey-Greenstein phase term gives
    // the volume a real lighting direction instead of a uniform brightness wash.
    float mist=1.0-exp(-min(optical,1.55)),inner=1.0-exp(-light);
    vec3 atmosphericDir=normalize(vec3(-.46,.76,.31));
    float mu=dot(ray,atmosphericDir),g=.38;
    float phase=(1.0-g*g)/pow(max(.12,1.0+g*g-2.0*g*mu),1.5);
    phase=clamp(phase*.23,.08,.72);
    float deepAbsorption=smoothstep(.18,1.18,optical);
    float multipleScatter=(1.0-exp(-optical*.42))*(1.0-exp(-light*.55));
    vec3 shadowColor=vec3(.012,.013,.015);
    vec3 bodyColor=vec3(.052,.055,.059);
    vec3 scatterColor=vec3(.48,.50,.52);
    vec3 fog=mix(bodyColor,scatterColor,clamp(inner*.56+phase*.34,0.0,1.0));
    fog=mix(fog,shadowColor,deepAbsorption*.48);
    fog+=vec3(.055,.058,.062)*(inner+multipleScatter*.7);
    // A faint far-field veil connects the local volume to the dimension's air.
    fog=mix(fog,vec3(.026,.028,.031),haze*.24);
    // A restrained pearl mist marks the moving pressure front around the player
    // and ferry wake without bleaching the underlying green volume.
    fog=mix(fog,vec3(.78,.79,.80),clamp(1.0-exp(-lowChurn),0.0,.26));
    color=mix(color,fog,mist);transmission*=1.0-mist;fragColor=vec4(color*strength,mix(1.0,transmission,strength));
}
