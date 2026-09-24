#version 330

uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform WorldData { mat4 InvViewProj; vec4 CameraData; vec4 CameraForward; };
layout(std140) uniform Intensity { float Value; };
layout(std140) uniform MistQuality { vec4 MarchSteps; };
layout(std140) uniform RiverData { vec4 River; };
layout(std140) uniform UnderworldTime { float Time; };
layout(std140) uniform Submersion { vec4 Underwater; };
in vec2 texCoord;
out vec4 fragColor;

float atlasNoise(vec3 p){
    vec2 uv=p.xz+vec2(p.y*.071,-p.y*.053);
    return texture(NoiseSampler,fract(uv)).r;
}

vec3 cloudPosition(vec3 world){
    // One shared world-space advection keeps every octave moving together.
    // The tiny vertical oscillation gives the banks lift without following the camera.
    float lift=sin(Time*.006+world.x*.027+world.z*.019)*.35;
    return world+vec3(Time*.004,lift,-Time*.0025);
}

vec3 unproject(float d){float z=CameraData.w>.5?d:d*2.0-1.0;vec4 p=InvViewProj*vec4(texCoord*2.0-1.0,z,1);return p.xyz/(abs(p.w)<.00001?.00001:p.w);}

float sceneDepth(){
    float depth=texture(DepthSampler,texCoord).r;
    // At reduced resolution one fog pixel covers several scene pixels. The
    // nearest solid surface wins; otherwise background fog bleeds over walls.
    if(textureSize(DepthSampler,0).x <= OutSize.x*1.15) return depth;
    vec2 footprint=.45/max(OutSize,vec2(1));
    depth=min(depth,texture(DepthSampler,texCoord+vec2(-footprint.x,-footprint.y)).r);
    depth=min(depth,texture(DepthSampler,texCoord+vec2( footprint.x,-footprint.y)).r);
    depth=min(depth,texture(DepthSampler,texCoord+vec2(-footprint.x, footprint.y)).r);
    return min(depth,texture(DepthSampler,texCoord+footprint).r);
}

float densityAt(vec3 p,float surface,out float lighting){
    float h=(p.y-surface-.1)/River.y;
    p=cloudPosition(p);
    float vertical=smoothstep(-.06,.07,h)*(1.0-smoothstep(.57,.96,h));
    vec3 windA=p*vec3(.018,.08,.018);
    vec3 windB=p*vec3(.041,.13,.041)+vec3(0,2.7,0);
    windB.xz=mat2(.766,.643,-.643,.766)*windB.xz;
    float broad=atlasNoise(windA);
    float shape=atlasNoise(windB);
    float detail=atlasNoise(p*vec3(.083,.19,.083)+vec3(0,5.1,0));
    float micro=atlasNoise(p*vec3(.19,.31,.19)+vec3(0,9.3,0));
    float mass=smoothstep(.27,.67,broad*.62+shape*.48);
    float layered=shape*.49+detail*.34+micro*.17;
    float erosion=mix(.52,1.12,smoothstep(.28,.74,layered));
    float lifeNoise=atlasNoise(p*vec3(.012,.04,.012)+vec3(0,13.1,0));
    float breathing=.88+.20*smoothstep(.2,.8,lifeNoise);
    float topHeight=.54+(broad-.5)*.14+(shape-.5)*.10;
    float blanket=smoothstep(-.05,.07,h)*(1.0-smoothstep(topHeight-.11,topHeight+.06,h));
    float body=blanket*mix(.68,1.08,smoothstep(.24,.68,mass+layered*.22));
    float crestBand=smoothstep(topHeight-.10,topHeight+.02,h)*(1.0-smoothstep(topHeight+.03,topHeight+.34,h));
    float crests=mass*crestBand*smoothstep(.47,.72,layered)*.58;
    float wisps=smoothstep(.48,.65,h)*(1.0-smoothstep(.96,1.38,h))*smoothstep(.58,.80,layered);
    vec2 curlOffset=vec2(sin(h*8.2+p.x*.012),cos(h*6.7+p.z*.011))*mix(.25,1.7,smoothstep(.42,1.75,h));
    vec3 tendrilPos=vec3(p.xz*.047+curlOffset*.035,p.y*.09);
    float ridgeA=1.0-smoothstep(.12,.32,abs(atlasNoise(tendrilPos)-.5));
    tendrilPos.xz=mat2(.342,-.94,.94,.342)*tendrilPos.xz*1.37-curlOffset*.018;
    float ridgeB=1.0-smoothstep(.12,.31,abs(atlasNoise(tendrilPos+vec3(7.2,0,3.1))-.5));
    float rise=smoothstep(.34,.62,h)*(1.0-smoothstep(1.06,1.52,h));
    float tendrils=max(ridgeA,ridgeB*.78)*rise*smoothstep(.43,.69,shape)*.16;
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
    p=cloudPosition(p);
    vec3 driftA=p*vec3(.014,.035,.014)+vec3(0,17.3,0);
    vec3 driftB=p*vec3(.037,.074,.037)+vec3(0,4.7,0);
    float broad=atlasNoise(driftA),fold=atlasNoise(driftB);
    float torn=atlasNoise(p*vec3(.081,.12,.081)+vec3(0,11.1,0));
    float ceiling=smoothstep(.12,.42,h)*(1.0-smoothstep(.78,1.0,h));
    float bank=smoothstep(.34,.66,broad*.68+fold*.46)*ceiling;
    float underside=1.0-smoothstep(.18,.58,h);
    float ridges=1.0-smoothstep(.055,.19,abs(fold-.5));
    float curtainLength=mix(.18,.74,smoothstep(.38,.78,broad));
    float curtains=ridges*smoothstep(.27,.57,torn)*underside
            *(1.0-smoothstep(curtainLength,curtainLength+.16,h));
    float pulse=.94+.06*sin(broad*6.283+p.x*.025-p.z*.019);
    glow=clamp((fold-torn)*.35+curtains*.28+.12,0.0,.58);
    return (bank*.58+curtains*.42)*pulse;
}

// Broad, world-fixed wisps bridge the water mist into the hanging canopy.
float middleBlobDensity(vec3 p,float bottom,float top,out float glow){
    float h=(p.y-bottom)/max(top-bottom,.001);
    // Reach zero well inside the marched volume, including at grazing angles.
    float verticalFade=smoothstep(-.48,.08,h)*(1.0-smoothstep(.88,1.48,h));
    if(verticalFade<.001){glow=0.0;return 0.0;}
    p=cloudPosition(p);
    // The shape lives in the cave's coordinates, never in view or screen space.
    float broad=atlasNoise(p*vec3(.024,.075,.024)+vec3(5.2,1.7,9.4));
    float shape=atlasNoise(p*vec3(.057,.14,.057)+vec3(13.1,4.6,2.8));
    float detail=atlasNoise(p*vec3(.115,.23,.115)+vec3(1.4,8.3,16.7));
    float ribbon=atlasNoise(vec3(p.x*.035+p.z*.012,p.y*.085,p.z*.048)+vec3(7.3,2.1,3.9));
    float folded=smoothstep(.34,.65,broad*.36+shape*.37+ribbon*.24+detail*.08);
    float height=1.0-smoothstep(.28,.58,abs(h-(.46+(broad-.5)*.12)));
    glow=clamp(.10+shape*.18+detail*.08,0.0,.38);
    return folded*height*verticalFade*(.52+.34*ribbon);
}

vec4 finishVolume(vec3 color, float transmission, float strength, float travel) {
    float submerged = clamp(Underwater.x, 0.0, 1.0);
    float waterAbsorption = exp(-travel * .16);
    transmission *= mix(1.0, .075 * waterAbsorption, submerged);
    color = mix(color, vec3(.0012,.0018,.0032) * (1.0 - exp(-travel * .12)), submerged);
    return vec4(color * strength, mix(1.0, transmission, strength));
}

void main(){
    int canopySamples=int(clamp(MarchSteps.x,4.0,8.0));
    int middleSamples=int(clamp(MarchSteps.y,5.0,10.0));
    int waterSamples=int(clamp(MarchSteps.z,10.0,20.0));
    float strength=clamp(Value,0.0,1.0);
    if(strength<.001){fragColor=vec4(0,0,0,1);return;}
    float depth=sceneDepth();vec3 end=unproject(depth),ray=normalize(unproject(.9999)-unproject(.0001));
    if(dot(ray,CameraForward.xyz)<0.0)ray=-ray;
    float travel=depth>=.9999?160.0:max(0.0,min(length(end-CameraData.xyz)-.12,160.0)),haze=1.0-exp(-max(0.0,travel-18.0)*.030*River.z);
    vec3 color=vec3(.004,.0045,.005)*haze;float transmission=1.0-haze;
    float canopyBottom=River.x+10.5,canopyTop=River.x+29.0;
    float canopyEnter=5.0,canopyLeave=min(travel,138.0);
    if(abs(ray.y)<.0001){if(CameraData.y<canopyBottom||CameraData.y>canopyTop)canopyLeave=0.0;}
    else{float ca=(canopyBottom-CameraData.y)/ray.y,cb=(canopyTop-CameraData.y)/ray.y;canopyEnter=max(5.0,min(ca,cb));canopyLeave=min(canopyLeave,max(ca,cb));}
    float canopySpan=max(0.0,canopyLeave-canopyEnter),canopyOptical=0.0,canopyLight=0.0;
    if(canopySpan>.001&&River.w>.001){
        float canopyStep=canopySpan/float(canopySamples),canopyJitter=.5;
        for(int c=0;c<8;++c){
            if(c>=canopySamples)break;
            float d=canopyEnter+(float(c)+canopyJitter)*canopyStep;vec3 p=CameraData.xyz+ray*d;float lit;
            float den=hangingDensity(p,canopyBottom,canopyTop,lit);
            float contact=smoothstep(0.0,2.5,travel-d)*smoothstep(5.0,9.0,d)
                    *(1.0-smoothstep(112.0,138.0,d));
            canopyOptical+=den*contact*canopyStep*.052*River.w;
            canopyLight+=den*contact*(.12+lit)*canopyStep*.035;
        }
        float canopy=1.0-exp(-min(canopyOptical,.72));
        vec3 canopyColor=mix(vec3(.016,.019,.023),vec3(.17,.20,.22),1.0-exp(-canopyLight));
        color=mix(color,canopyColor,canopy);transmission*=1.0-canopy;
    }
    float middleBottom=River.x+3.35,middleTop=River.x+12.25;
    float middleVolumeBottom=middleBottom-5.0,middleVolumeTop=middleTop+5.0;
    float middleEnter=4.0,middleLeave=min(travel,126.0);
    if(abs(ray.y)<.0001){if(CameraData.y<middleVolumeBottom||CameraData.y>middleVolumeTop)middleLeave=0.0;}
    else{float ma=(middleVolumeBottom-CameraData.y)/ray.y,mb=(middleVolumeTop-CameraData.y)/ray.y;middleEnter=max(4.0,min(ma,mb));middleLeave=min(middleLeave,max(ma,mb));}
    float middleSpan=max(0.0,middleLeave-middleEnter),middleOptical=0.0,middleLight=0.0;
    if(middleSpan>.001&&River.w>.001){
        // Stable midpoint sampling keeps the puffs in world space. Screen-pixel
        // jitter made this particular band look like a texture following the camera.
        float middleStep=middleSpan/float(middleSamples),middleJitter=.5;
        for(int m=0;m<10;++m){
            if(m>=middleSamples)break;
            float d=middleEnter+(float(m)+middleJitter)*middleStep;vec3 p=CameraData.xyz+ray*d;float lit;
            float den=middleBlobDensity(p,middleBottom,middleTop,lit);
            float contact=smoothstep(0.0,2.2,travel-d)*smoothstep(4.0,7.5,d)
                    *(1.0-smoothstep(104.0,126.0,d));
            middleOptical+=den*contact*middleStep*.128*River.w;
            middleLight+=den*contact*(.12+lit)*middleStep*.052;
        }
        float middleFog=1.0-exp(-min(middleOptical,1.28));
        vec3 middleColor=mix(vec3(.018,.021,.026),vec3(.21,.245,.27),1.0-exp(-middleLight));
        color=mix(color,middleColor,middleFog);transmission*=1.0-middleFog;
    }
    float bottom=River.x-1.0,top=River.x+River.y*1.55,enter=1.5,leave=min(travel,100.0);
    if(abs(ray.y)<.0001){if(CameraData.y<bottom||CameraData.y>top)leave=0.0;}else{float a=(bottom-CameraData.y)/ray.y,b=(top-CameraData.y)/ray.y;enter=max(1.5,min(a,b));leave=min(leave,max(a,b));}
    float span=max(0.0,leave-enter);if(span<.001||River.w<=.001){fragColor=finishVolume(color,transmission,strength,travel);return;}
    // The fog is tied to a fixed world-height band, not the animated water mesh.
    float overhead=smoothstep(.18,.92,-ray.y);
    float viewDensity=mix(1.0,1.68,overhead);
    float stepLength=span/float(waterSamples),jitter=.5,optical=0.0,light=0.0;
    for(int i=0;i<20;++i){
        if(i>=waterSamples)break;
        float d=enter+(float(i)+jitter)*stepLength;vec3 p=CameraData.xyz+ray*d;float lit;
        float den=densityAt(p,River.x,lit);
        float contact=smoothstep(0.0,2.0,travel-d)*smoothstep(1.5,4.5,d)
                *(1.0-smoothstep(82.0,100.0,d));
        float sliceDensity=den*contact*stepLength*.245*River.w*viewDensity;
        // Front-to-back extinction makes deep lobes shade the slices behind them,
        // matching the reference's sliced volumetric self-shadowing model.
        float sliceTransmittance=exp(-optical*1.45);
        light+=den*contact*(.12+lit+overhead*.16)*stepLength*.09*viewDensity*sliceTransmittance;
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
    vec3 shadowColor=vec3(.010,.013,.018);
    vec3 bodyColor=vec3(.038,.047,.055);
    vec3 scatterColor=vec3(.24,.29,.32);
    vec3 fog=mix(bodyColor,scatterColor,clamp(inner*.56+phase*.34,0.0,1.0));
    fog=mix(fog,shadowColor,deepAbsorption*.48);
    fog+=vec3(.028,.035,.041)*(inner+multipleScatter*.7);
    // A faint far-field veil connects the local volume to the dimension's air.
    fog=mix(fog,vec3(.026,.028,.031),haze*.24);
    color=mix(color,fog,mist);transmission*=1.0-mist;fragColor=finishVolume(color,transmission,strength,travel);
}
