// node tools/blockbench/cutscene.test.cjs [path/to/three.cjs]
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {sampleCamera, validateKeys,normalizeReference,meshReference,readNBT,playerParts,reidentify,convertAnimation} = require('./asterion_cutscene.js');
(async()=>{
const key = (time, x, ease = 'linear') => ({time, position: [x, 2, 4], target: [0, 1, 0], fov: 60 + x, roll: x, ease});
const keys = [key(0, 0, 'smooth'), key(1, 8, 'hold'), key(2, 16)];
assert.equal(sampleCamera(keys, .25).position[0], 1.25);
assert.equal(sampleCamera(keys, 1.99).position[0], 8);
assert.equal(sampleCamera(keys, 2).position[0], 16);
assert.equal(sampleCamera(keys, -1).position[0], 0);
assert.throws(() => validateKeys([key(0, 0), key(0, 1)], 2));
assert.throws(() => validateKeys([{...key(0, 0), fov: NaN}], 2));
assert.throws(() => validateKeys([{...key(0, 0), target: [0,2,4]}], 2));
const hourKeys=Array.from({length:72001},(_,i)=>({time:i/20,position:[i/20,2,4],target:[0,1,0],fov:70,roll:0,ease:'linear'}));
assert.doesNotThrow(()=>validateKeys(hourKeys,3600));
assert.equal(sampleCamera(hourKeys,3599.95).time,3599.95);

// Exercise the actual plugin registration, persistence, export and cleanup with real Three.js geometry.
const THREE = require(path.resolve(process.argv[2] || 'build/cutscene-reference/three.cjs'));
let plugin, exported, lastDialog, panelConfig, panelVue, interval, savedProject;
const actions = new Map(), events = new Map(), messages = [];
const scene = new THREE.Scene(), mesh = new THREE.Mesh(new THREE.BoxGeometry(16, 16, 16), new THREE.MeshBasicMaterial());
mesh.position.set(16, 8, 0); scene.add(mesh);
const project = {name: 'parity', view_mode: 'textured', asterion_cutscene: JSON.stringify({duration: 2, fps: 20, aspect: 16 / 9, fullbright: false, keys})};
const context = {
    THREE, console: {...console, error() {}}, Project: project, Format: {animation_mode: true}, ModelProject: class {},
    Plugin: {register(id, config) { plugin = config; }},
    Action: class { constructor(id, config) { this.id = id; Object.assign(this, config); actions.set(id, this); } delete() { actions.delete(this.id); } },
    Property: class { delete() {} },
    ModelFormat:class{constructor(id,config){this.id=id;Object.assign(this,config);}delete(){}},
    Mode:class{constructor(id,config){this.id=id;Object.assign(this,config);}delete(){}},
    setInterval(fn){interval=fn;return 1;},clearInterval(){interval=null;},
    Panel: class { constructor(id, config) { panelConfig=config.component;this.vue = panelVue=config.component.data();for(const [name,fn] of Object.entries(config.component.methods))this.vue[name]=fn.bind(this.vue); } delete() {} },
    Dialog: class { constructor(config) { Object.assign(this, config); lastDialog = this; } show() {} hide() {} },
    MenuBar: {addAction() {}},
    Codecs: {project: {export(){const model={};events.get('codec:compile')({model});savedProject=JSON.parse(JSON.stringify(model));},on(name, fn) { events.set('codec:' + name, fn); }, removeListener(name) { events.delete('codec:' + name); }}},
    Blockbench: {addCSS(){return{delete(){}};},on(name, fn) { events.set(name, fn); }, removeListener(name) { events.delete(name); }, export(value) { exported = JSON.parse(value.content); }, showMessageBox(v) { messages.push(v.message); }, showQuickMessage() {}},
    Timeline: {time: .3, playing: false, pause() {}, start() {}, setTime(t) { this.time = t; }},
    Animator: {preview() { mesh.position.x = 16 + context.Timeline.time * 16; events.get('display_animation_frame')?.(); }},
    Outliner: {elements: [{name: 'cube', mesh, type: 'cube'}]},
    Texture: {all: [{uuid: 'test_texture', name: 'test.png', getMaterial: () => mesh.material, getBase64: () => 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg=='}]}, Preview: {selected: null}, scene
};
vm.createContext(context); vm.runInContext(fs.readFileSync(path.join(__dirname, 'asterion_cutscene.js'), 'utf8'), context);
plugin.onload();
actions.get('asterion_cs_export').click();
assert.deepEqual(messages, []);
assert.equal(exported.objects[0].parts.length, 6);
assert.equal(exported.textures.length, 1);
assert.ok(exported.objects[0].parts.every(part => part.texture === 0));
assert.equal(exported.objects[0].parts[0].vertices.length, 6);
assert.equal(exported.objects[0].samples.length, 41);
assert.equal(exported.objects[0].samples[0][12], 1);
assert.equal(exported.objects[0].samples[40][12], 3);
assert.equal(exported.objects[0].samples[0][0], 1 / 16);
assert.equal(context.Timeline.time, .3);
assert.equal(mesh.position.x, 20.8);
actions.get('asterion_cs_key').click();
lastDialog.onConfirm.call(lastDialog, key(.5, 4));
assert.deepEqual(messages, []);
assert.equal(JSON.parse(project.asterion_cutscene).keys.length, 4);
// Manual camera pose opens at the playhead and accepts an authored pose without a viewport.
context.Timeline.time=1.25;panelVue.manualPose();assert.equal(lastDialog.form.time.value,1.25);
lastDialog.onConfirm.call(lastDialog,{...key(1.25,7,'hold'),position:[7,3,2],target:[1,2,0],fov:82,roll:15});
assert.equal(JSON.parse(project.asterion_cutscene).keys.find(k=>k.time===1.25).roll,15);
context.Timeline.time=1.5;panelVue.duplicatePose();assert.equal(JSON.parse(project.asterion_cutscene).keys.find(k=>k.time===1.5).fov,82);
actions.get('asterion_cs_undo_camera').click();
assert.equal(JSON.parse(project.asterion_cutscene).keys.length, 5);
actions.get('asterion_cs_undo_camera').click();actions.get('asterion_cs_undo_camera').click();
assert.equal(JSON.parse(project.asterion_cutscene).keys.length, 3);
context.Timeline.time=.3;
// A failed bake restores the timeline and pose and reports the error.
const preview = context.Animator.preview;
context.Animator.preview = () => { if (context.Timeline.time === 0) throw Error('bad animation'); preview(); };
actions.get('asterion_cs_export').click();
assert.equal(context.Timeline.time, .3);
assert.equal(messages.pop(), 'bad animation');
context.Animator.preview=preview;
assert.deepEqual(messages, []);
// Pilot controls remain movable, recording restores the saved view, guides toggle independently.
const camera=new THREE.PerspectiveCamera(70,16/9,.1,1000);camera.position.set(48,32,64);camera.lookAt(0,16,0);
const viewport={camera,controls:{target:new THREE.Vector3(0,16,0),unlinked:false},isOrtho:false,setFOV(v){camera.fov=v;camera.updateProjectionMatrix();},setProjectionMode(v){this.isOrtho=v;}};
context.Preview.selected=viewport;const original=camera.position.clone();
panelVue.pilot();assert.equal(panelVue.cameraMode,'pilot');assert.equal(viewport.controls.unlinked,false);
const pilotPosition=camera.position.clone();panelVue.moveCamera('right');assert.ok(camera.position.distanceTo(pilotPosition)>0);
panelVue.record();assert.equal(panelVue.recording,true);panelVue.cancelRecord();assert.equal(panelVue.recording,false);
panelVue.pilot();assert.ok(camera.position.distanceTo(original)<1e-8);
panelVue.toggleGuide('showPath');assert.equal(JSON.parse(project.asterion_cutscene).showPath,false);
panelVue.selectKey(0);panelVue.draft.fov=95;panelVue.inspect(true);assert.equal(JSON.parse(project.asterion_cutscene).keys[0].fov,95);
assert.deepEqual(messages, []);
const unanchored=exported,sceneData=JSON.parse(project.asterion_cutscene);
project.asterion_cutscene=JSON.stringify({...sceneData,anchorWorld:true,worldOrigin:[100,64,-200],reference:{worldOrigin:[100,64,-200]},referencePosition:[1,0,0],referenceYaw:90});
actions.get('asterion_cs_export').click();assert.deepEqual(messages,[]);
assert.deepEqual(Array.from(exported.world_origin),[100,64,-200]);
assert.ok(Math.abs(exported.camera[0].position[0]+4)<1e-8);assert.ok(Math.abs(exported.camera[0].position[2]+1)<1e-8);
assert.ok(Math.abs(exported.objects[0].samples[40][14]-2)<1e-8);
exported=unanchored;
const embedded={format:'asterion_reference',size:[2,1,1],palette:['minecraft:stone'],blocks:[[0,0,0,0],[1,0,0,0]],id:'saved-world',name:'test.schem'};
project.asterion_cutscene=JSON.stringify({...sceneData,reference:embedded,referencePosition:[-1,0,-.5]});
panelVue.saveProject();const reopened=JSON.parse(savedProject.asterion_cutscene);assert.deepEqual(reopened.reference,embedded);assert.deepEqual(reopened.referencePosition,[-1,0,-.5]);
// Desktop skin imports must request bytes, not mistake a filesystem path for image contents.
let importedSkin,skinTask;
context.Cube=class{constructor(){this.faces={north:{texture:'old'}};}};
const skinCube=new context.Cube(),skinRoot={uuid:'player',name:'player',children:[skinCube]};
context.Group={all:[skinRoot]};context.Blob=Blob;
context.FileReader=class{readAsDataURL(blob){blob.arrayBuffer().then(bytes=>{this.result='data:image/png;base64,'+Buffer.from(bytes).toString('base64');this.onload();});}};
context.Image=class{set src(value){assert.ok(value.startsWith('data:image/png;base64,'));this.width=64;this.height=64;queueMicrotask(()=>this.onload());}};
context.Texture=class{constructor(options){Object.assign(this,options);this.uuid='loaded-skin';}fromDataURL(source){this.source=source;importedSkin=this;return this;}add(){return this;}};
context.Undo={initEdit(){},finishEdit(){}};context.Canvas={updateAll(){}};
context.Blockbench.import=(options,callback)=>{assert.equal(options.readtype,'buffer');skinTask=callback([{name:'skin.png',path:'C:/skins/skin.png',content:Buffer.from([137,80,78,71])}]);};
project.asterion_cutscene=JSON.stringify({...reopened,actors:[{id:'player',name:'player',type:'player'}]});
panelVue.skin('player');await skinTask;
assert.equal(importedSkin.source,'data:image/png;base64,iVBORw==');assert.equal(skinCube.faces.north.texture,'loaded-skin');assert.deepEqual(messages,[]);
panelVue.saveProject();assert.equal(JSON.parse(savedProject.asterion_cutscene).actors[0].skin,'loaded-skin');
plugin.onunload(); assert.equal(actions.size, 0); assert.equal(events.size, 0); assert.equal(scene.children.length, 1);
const folder = path.resolve('build/cutscene-tests'); fs.mkdirSync(folder, {recursive: true});
fs.writeFileSync(path.join(folder, 'fixture.json'), JSON.stringify(exported));
fs.writeFileSync(path.join(folder, 'camera-expected.json'), JSON.stringify(Array.from({length: 81}, (_, i) => sampleCamera(keys, i / 40))));
console.log('PASS: camera interpolation, validation, real geometry export, scale, restore-on-error, persistence, undo and unload.');

// NBT decoding and each supported storage layout use the same normalized reference contract.
const nbt=Buffer.from([10,0,0,3,0,1,120,0,0,0,42,7,0,1,98,0,0,0,2,3,4,0]);
assert.equal(readNBT(nbt).x,42);assert.deepEqual(Array.from(readNBT(nbt).b),[3,4]);
assert.throws(()=>readNBT(nbt.subarray(0,nbt.length-2)),/Truncated/);
const ref=normalizeReference({format:'asterion_reference',size:[2,1,1],palette:['minecraft:stone'],blocks:[[0,0,0,0],[1,0,0,0]],worldOrigin:[100,64,-200]});
assert.deepEqual(ref.worldOrigin,[100,64,-200]);
assert.equal(meshReference(ref).exposedFaces,10);assert.equal(meshReference(ref).quads.length,6);
const sponge=normalizeReference({Schematic:{Width:2,Height:1,Length:1,Blocks:{Palette:{'minecraft:air':0,'minecraft:stone':1},Data:Uint8Array.from([1,0])}}});
assert.deepEqual(sponge.blocks,[[0,0,0,1]]);
assert.equal(normalizeReference({Width:1,Height:1,Length:1,Blocks:Uint8Array.from([1])}).blocks.length,1);
assert.equal(normalizeReference({size:[1,1,1],palette:[{Name:'minecraft:stone'}],blocks:[{pos:[0,0,0],state:0}]}).blocks.length,1);
assert.throws(()=>normalizeReference({...ref,format:'asterion_reference',worldOrigin:[NaN,0,0]}),/origin/);
assert.throws(()=>normalizeReference({...ref,format:'asterion_reference',blocks:[[2,0,0,0]]}),/block/);
assert.equal(playerParts(false).length,6);assert.equal(playerParts(true)[2][2][0]-playerParts(true)[2][1][0],3);
assert.ok(playerParts(false).every(p=>p[5].length===2&&p[6]>0));
let uuid=0;const model={elements:[{uuid:'cube'}],outliner:[{uuid:'bone',children:['cube']}],animations:[{animators:{bone:{keyframes:[{uuid:'key'}]}}}],textures:[{uuid:'tex',source:'data:image/png;base64,test',path:'old.png'}]};
reidentify(model,()=>String(++uuid));assert.equal(model.outliner[0].children[0],model.elements[0].uuid);assert.ok(model.animations[0].animators[model.outliner[0].uuid]);assert.equal(model.textures[0].path,undefined);
const anim=convertAnimation({animation_length:2,bones:{arm:{rotation:{'0':[0,0,0],'1':[30,45,60]},position:[2,3,4]}}},new Map([['arm','actor-arm']]));
assert.deepEqual(anim.animators['actor-arm'].keyframes.find(k=>k.time===1).data_points,[{x:-30,y:-45,z:60}]);
assert.throws(()=>convertAnimation({bones:{missing:{}}},new Map()),/matching bone/);
assert.throws(()=>convertAnimation({sound_effects:{}},new Map()),/effects/);
console.log('PASS: NBT bounds, schematic/structure formats, greedy terrain meshing, world anchors, layered rigs, isolated model UUIDs and targeted animation conversion.');
const vuePath=path.resolve('build/cutscene-reference/vue.cjs');
if(fs.existsSync(vuePath)){
    global.document={createElement(){return {set innerHTML(v){this.textContent=v.replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&');}};}};
    const Vue=require(vuePath),warnings=[];Vue.config.warnHandler=message=>warnings.push(message);
    const compiled=Vue.compile(panelConfig.template);assert.equal(typeof compiled.render,'function');assert.deepEqual(warnings,[]);
    const instance=new Vue({...panelConfig,render:compiled.render,staticRenderFns:compiled.staticRenderFns});
    for(const tab of ['director','actors','world','clips']){instance.tab=tab;instance._render();}
    instance.tab='director';instance.draft={...key(0,0)};instance.selectedKey=0;instance._render();assert.deepEqual(warnings,[]);
    delete global.document;console.log('PASS: Vue panel template compilation and all four tabs render without warnings.');
}
console.log('PASS: embedded reference save round-trip and desktop skin-byte loading/assignment.');
})().catch(error=>{console.error(error);process.exitCode=1;});
