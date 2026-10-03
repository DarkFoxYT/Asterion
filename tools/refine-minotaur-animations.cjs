// Rebuild derived clips from the authored 24 fps grapple, preserving other animations verbatim.
const fs = require('fs');
const path = 'src/main/resources/assets/asterion/geckolib/animations/entity/minotaur.animation.json';
let text = fs.readFileSync(path, 'utf8');
const data = JSON.parse(text);
function value(frame) { return Array.isArray(frame) ? frame : frame.post ?? frame.pre; }
function sample(track, t) {
  const keys = Object.keys(track).map(Number).sort((a,b)=>a-b);
  const i = keys.findIndex(k=>k>=t);
  if(i===0)return value(track[String(keys[0])] ?? Object.values(track)[0]);
  const at = k => value(track[Object.keys(track).find(s=>Number(s)===k)]);
  if(i<0)return at(keys.at(-1));
  const a=at(keys[i-1]), b=at(keys[i]), u=(t-keys[i-1])/(keys[i]-keys[i-1]);
  const p=at(keys[Math.max(0,i-2)]), n=at(keys[Math.min(keys.length-1,i+1)]);
  const frame=track[Object.keys(track).find(s=>Number(s)===keys[i-1])];
  return a.map((v,axis)=>frame.lerp_mode==='catmullrom'
    ? .5*((2*v)+(-p[axis]+b[axis])*u+(2*p[axis]-5*v+4*b[axis]-n[axis])*u*u+(-p[axis]+3*v-3*b[axis]+n[axis])*u*u*u)
    : v+(b[axis]-v)*u);
}
const reel = {animation_length:13/24,bones:{}};
for(const [bone,channels] of Object.entries(data.animations.chain_grapple.bones)) {
  reel.bones[bone]={};
  for(const [channel,track] of Object.entries(channels)) {
    const result={};
    for(let f=0;f<=13;f++) result[(f/24).toFixed(6)]={post:sample(track,(30+f)/24).map(v=>+v.toFixed(6)),lerp_mode:'catmullrom'};
    reel.bones[bone][channel]=result;
  }
}
// The same loaded grip and yank on the left arm, instead of the grapple's resting arm.
for(const bone of ['rightshoulder','rightarm','lowerrightarm']) {
  const mirror=bone.replace('right','left');
  reel.bones[mirror]=structuredClone(reel.bones[bone]);
  for(const [channel,track] of Object.entries(reel.bones[mirror]))for(const frame of Object.values(track))
    frame.post=frame.post.map((v,i)=>v*(channel==='rotation'?(i===0?1:-1):(i===0?-1:1)));
}
const land={animation_length:1.4,bones:{}};
const model=JSON.parse(fs.readFileSync('src/main/resources/assets/asterion/geckolib/models/entity/minotaur.geo.json','utf8'));
const modelBones=Object.fromEntries(model['minecraft:geometry'][0].bones.map(b=>[b.name,b]));
function track(bone,channel,keys) {
  land.bones[bone]??={};land.bones[bone][channel]={};
  for(const [time,values] of keys)land.bones[bone][channel][time]={post:values,lerp_mode:'catmullrom'};
}
const phases=[0,.12,.3,.68,1.15,1.4];
function rotate(bone,poses){track(bone,'rotation',phases.map((t,i)=>[t,poses[i]]));}
rotate('lowerbody',[[8,0,0],[16,0,0],[13,0,0],[6,0,0],[0,0,0],[0,0,0]]);
rotate('body',[[20,0,0],[32,0,0],[24,0,0],[10,0,0],[0,0,0],[0,0,0]]);
rotate('head',[[-10,0,0],[-18,0,0],[-12,0,0],[-5,0,0],[0,0,0],[0,0,0]]);
for(const side of ['left','right']) {
  const sign=side==='left'?1:-1;
  rotate(side+'shoulder',[[-35,0,sign*12],[-48,0,sign*16],[-30,0,sign*10],[-12,0,sign*5],[0,0,0],[0,0,0]]);
  rotate(side+'arm',[[0,sign*8,0],[0,sign*12,0],[0,sign*8,0],[0,sign*3,0],[0,0,0],[0,0,0]]);
  rotate('lower'+side+'arm',[[-22,0,0],[-35,0,0],[-24,0,0],[-10,0,0],[0,0,0],[0,0,0]]);
  const thigh=[-12,-22,-16,-7,0,0], knee=[23,38,29,13,0,0];
  rotate(side+'leg',thigh.map(x=>[x,0,0]));
  rotate('lower'+side+'leg',knee.map(x=>[x,0,0]));
  rotate(side+'foot',thigh.map((x,i)=>[-x-knee[i],0,0]));
  // Preserve each foot's rest pivot when the thigh and knee compress.
  const rotateVector=(v,r)=>{
    let [x,y,z]=v;let a=-r[0]*Math.PI/180;[y,z]=[y*Math.cos(a)-z*Math.sin(a),y*Math.sin(a)+z*Math.cos(a)];
    a=-r[1]*Math.PI/180;[x,z]=[x*Math.cos(a)+z*Math.sin(a),-x*Math.sin(a)+z*Math.cos(a)];
    a=r[2]*Math.PI/180;[x,y]=[x*Math.cos(a)-y*Math.sin(a),x*Math.sin(a)+y*Math.cos(a)];return [x,y,z];
  };
  const hip=modelBones[side+'leg'], lower=modelBones['lower'+side+'leg'], foot=modelBones[side+'foot'];
  const difference=(a,b)=>a.map((v,j)=>(v-b[j])*(j===0?-1:1));
  const hipToKnee=difference(lower.pivot,hip.pivot), kneeToFoot=difference(foot.pivot,lower.pivot);
  const base=hip.rotation??[0,0,0];
  const rest=rotateVector(hipToKnee.map((v,j)=>v+kneeToFoot[j]),base);
  track(side+'leg','position',phases.map((t,i)=>{
    const k=rotateVector(kneeToFoot,[knee[i],0,0]);const f=rotateVector(k.map((v,j)=>v+hipToKnee[j]),[base[0]+thigh[i],base[1],base[2]]);
    return [t,rest.map((v,j)=>+((v-f[j])*(j===0?-1:1)).toFixed(5))];
  }));
}
land.bones.lowerbody.position=structuredClone(land.bones.leftleg.position);
for(const frame of Object.values(land.bones.lowerbody.position))frame.post[0]=0;
function replaceClip(name,clip) {
  const start=text.indexOf('"'+name+'":');if(start<0)throw Error('Missing clip '+name);
  const open=text.indexOf('{',start);let depth=0,end=open;
  for(;end<text.length;end++){if(text[end]==='{')depth++;if(text[end]==='}' && --depth===0)break;}
  const indent=text.slice(text.lastIndexOf('\n',start)+1,start);
  const json=JSON.stringify(clip,null,'\t').replace(/\n/g,'\n'+indent);
  text=text.slice(0,open)+json+text.slice(end+1);
}
replaceClip('weapon_recall',reel);replaceClip('asterion_leap_land',land);
JSON.parse(text);fs.writeFileSync(path,text);
console.log('Generated two-arm grapple frames 30–43 and planted-foot landing');
