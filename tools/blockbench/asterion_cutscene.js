// Generated standalone plugin; edit src/ and run node tools/blockbench/build.cjs.
/* Pure import/optimization helpers. No network requests or project mutations. */
const AsterionStudioCore = (() => {
    const check = (value, message) => { if (!value) throw Error(message); };
    function readNBT(input) {
        const bytes = input instanceof Uint8Array ? input : new Uint8Array(input);
        check(bytes.byteLength <= 64 * 1024 * 1024, 'NBT exceeds 64 MB.');
        const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength), decoder = new TextDecoder(); let offset = 0, nodes = 0;
        const take = n => { check(Number.isInteger(n) && n >= 0 && offset + n <= bytes.length, 'Truncated NBT.'); const at = offset; offset += n; return at; };
        const u8 = () => view.getUint8(take(1)), i32 = () => view.getInt32(take(4));
        const string = () => { const n = view.getUint16(take(2)); return decoder.decode(bytes.subarray(offset, take(n) + n)); };
        function value(type, depth = 0) {
            check(depth < 64 && ++nodes < 4000000, 'NBT nesting/count limit exceeded.');
            if (type === 1) return view.getInt8(take(1));
            if (type === 2) return view.getInt16(take(2));
            if (type === 3) return i32();
            if (type === 4) return view.getBigInt64(take(8));
            if (type === 5) return view.getFloat32(take(4));
            if (type === 6) return view.getFloat64(take(8));
            if (type === 8) return string();
            if (type === 10) { const result = Object.create(null); for (let t; (t = u8()) !== 0;) { const name = string(); result[name] = value(t, depth + 1); } return result; }
            if ([7,9,11,12].includes(type)) {
                const child = type === 9 ? u8() : 0, n = i32();
                check(n >= 0 && n <= (type===7?64*1024*1024:4000000), 'NBT array limit exceeded.');
                if (type === 7) return bytes.slice(take(n), offset);
                if (type === 9) { check(child > 0 || n === 0, 'Invalid NBT list.'); return Array.from({length:n}, () => value(child, depth + 1)); }
                return Array.from({length:n}, () => type === 11 ? i32() : view.getBigInt64(take(8)));
            }
            throw Error('Unsupported NBT tag ' + type);
        }
        check(u8() === 10, 'Expected Java-edition big-endian NBT compound.'); string(); return value(10);
    }
    const air = name => /^(?:minecraft:)?(?:air|cave_air|void_air|structure_void)$/.test(String(name).trim().toLowerCase().split('[')[0].trim());
    const maxReferenceCells=16*1024*1024;
    const volumeOf=size=>{
        check(Array.isArray(size)&&size.length===3&&size.every(n=>Number.isInteger(n)&&n>0&&n<=2048),'Invalid reference dimensions.');
        const volume=size.reduce((a,b)=>a*b,1);
        check(volume<=maxReferenceCells,`Selection is ${size.join(' × ')} (${volume.toLocaleString()} cells including air). The scan limit is ${maxReferenceCells.toLocaleString()} cells; this is not the number of solid blocks.`);
        return volume;
    };
    function normalizeReference(root) {
        root = root.Schematic || root;
        let palette, blocks = [], size, sourceOffset = root.Offset || [0,0,0];
        if (root.format === 'asterion_reference') {
            palette = root.palette; blocks = root.blocks; size = root.size;
        } else if (root.palette && root.blocks && root.size) {
            palette = root.palette.map(p => p.Name); size = root.size;
            blocks = root.blocks.map(b => [...b.pos, b.state]);
        } else if (root.Width !== undefined && root.Height !== undefined && root.Length !== undefined) {
            size = [root.Width & 65535, root.Height & 65535, root.Length & 65535];
            const count = volumeOf(size);
            if (root.Palette || root.Blocks?.Palette) {
                const names = root.Palette || root.Blocks.Palette;
                palette = []; for (const [name,id] of Object.entries(names)) { check(Number.isInteger(id) && id >= 0 && id < 65536, 'Invalid palette index.'); palette[id] = name; }
                const encoded = root.BlockData || root.Blocks.Data; check(encoded && encoded.length <= 64*1024*1024, 'Missing or oversized block data.');
                const empty=new Set(palette.flatMap((name,id)=>air(name)?[id]:[]));
                let at = 0;
                for (let i = 0; i < count; i++) {
                    let id = 0, shift = 0, byte;
                    do { check(at < encoded.length && shift < 35, 'Invalid schematic varint.'); byte = encoded[at++]; id += (byte & 127) * 2 ** shift; shift += 7; } while (byte & 128);
                    check(palette[id] !== undefined, 'Unknown palette index.');
                    if (!empty.has(id)) {check(blocks.length<2000000,`Selection ${size.join(' × ')} contains more than 2,000,000 non-air blocks; import a smaller region to stay within the memory budget.`);blocks.push([i % size[0], Math.floor(i / (size[0]*size[2])), Math.floor(i / size[0]) % size[2], id]);}
                }
                check(at===encoded.length,'Schematic block data has trailing entries that do not match its dimensions.');
            } else if (root.Blocks instanceof Uint8Array) {
                palette = ['minecraft:air']; const ids = new Map(); check(root.Blocks.length === count, 'Invalid legacy block array.');
                for (let i = 0; i < count; i++) {
                    const high = root.AddBlocks ? ((root.AddBlocks[i>>1] >> ((i&1)*4)) & 15) : 0;
                    const id = root.Blocks[i] | high << 8; if (!id || id===217) continue;
                    const state = 'legacy:' + id + ':' + (root.Data?.[i] || 0);
                    if (!ids.has(state)) { ids.set(state, palette.length); palette.push(state); }
                    check(blocks.length<2000000,'Legacy schematic contains more than 2,000,000 non-air blocks; import a smaller region.');
                    blocks.push([i % size[0], Math.floor(i / (size[0]*size[2])), Math.floor(i / size[0]) % size[2], ids.get(state)]);
                }
            } else throw Error('Unsupported schematic block storage.');
        } else throw Error('Use Sponge .schem v2/v3, MCEdit .schematic, Java structure .nbt, or Asterion reference JSON. Region/world folders are not imported directly.');
        const selectionCells=volumeOf(size);
        check(Array.isArray(palette) && palette.length <= 65536 && palette.every(p=>typeof p==='string'), 'Invalid reference palette.');
        check(Array.isArray(blocks) && blocks.length <= 2000000, 'Too many reference cells.');
        const unique=new Map();
        for(const b of blocks){check(Array.isArray(b)&&b.length===4&&b.every(Number.isInteger)&&b.slice(0,3).every((n,i)=>n>=0&&n<size[i])&&palette[b[3]]!==undefined,'Invalid reference block.');const key=b[0]+size[0]*(b[2]+size[2]*b[1]);if(air(palette[b[3]]))unique.delete(key);else unique.set(key,b);}
        blocks=Array.from(unique.values());
        if(blocks.length>2000000){
            const counts=new Map();for(const b of blocks)counts.set(palette[b[3]],(counts.get(palette[b[3]])||0)+1);
            const top=[...counts].sort((a,b)=>b[1]-a[1]).slice(0,4).map(([name,n])=>`${name}: ${n.toLocaleString()}`).join(', ');
            throw Error(`Decoded ${blocks.length.toLocaleString()} unique non-air blocks in a ${size.join(' × ')} selection (${selectionCells.toLocaleString()} cells including air). Largest block counts: ${top}. The import memory budget is 2,000,000 non-air blocks. If this disagrees with your build, share this diagnostic and the schematic so its palette can be checked.`);
        }
        for(const v of [sourceOffset,root.worldOrigin||[0,0,0]])check(Array.isArray(v)&&v.length===3&&v.every(n=>Number.isFinite(n)&&Math.abs(n)<=30000000),'Invalid reference origin.');
        return {size, palette, blocks, selectionCells, sourceOffset: Array.from(sourceOffset), worldOrigin: root.worldOrigin || null};
    }
    // Greedy surface meshing: hidden faces disappear; coplanar equal-palette faces become one rectangle.
    function meshReference(ref) {
        const [width,height,length]=ref.size,cell=(x,y,z)=>x+width*(z+length*y);
        const occupied = new Map(), planes = new Map(); let faceCount = 0;
        for(const b of ref.blocks)occupied.set(cell(b[0],b[1],b[2]),b[3]);
        for (const b of ref.blocks) for (let axis=0; axis<3; axis++) for (const sign of [-1,1]) {
            const next0=b[0]+(axis===0?sign:0),next1=b[1]+(axis===1?sign:0),next2=b[2]+(axis===2?sign:0);
            if(next0>=0&&next0<width&&next1>=0&&next1<height&&next2>=0&&next2<length&&occupied.has(cell(next0,next1,next2))) continue;
            check(++faceCount<=4000000, 'Reference has over 4,000,000 exposed faces before merging. Import a smaller region.');
            const u=(axis+1)%3,v=(axis+2)%3, plane=b[axis]+(sign>0?1:0), id=[axis,sign,plane].join(',');
            if(!planes.has(id)) planes.set(id,new Map()); planes.get(id).set(b[u]+','+b[v],b[3]);
        }
        const quads=[];
        for(const [id,cells] of planes) {
            const [axis,sign,plane]=id.split(',').map(Number),u=(axis+1)%3,v=(axis+2)%3;
            for(const [key,palette] of cells) {
                const [x,y]=key.split(',').map(Number); let w=1,h=1;
                while(cells.get((x+w)+','+y)===palette)w++;
                outer: while(true) { for(let i=0;i<w;i++)if(cells.get((x+i)+','+(y+h))!==palette)break outer; h++; }
                for(let j=0;j<h;j++)for(let i=0;i<w;i++)cells.delete((x+i)+','+(y+j));
                const corners=[[x,y],[x+w,y],[x+w,y+h],[x,y+h]].map(([a,b])=>{const p=[0,0,0];p[axis]=plane;p[u]=a;p[v]=b;return p;});
                if(sign<0)corners.reverse(); quads.push({corners,palette});
                check(quads.length<=250000,'Reference still has over 250,000 visible quads after surface merging. Import a smaller region to keep the viewport responsive.');
            }
        }
        return {quads, exposedFaces:faceCount};
    }
    function playerParts(slim) {
        const arm=slim?3:4;
        return [
            ['body',[-4,12,-2],[4,24,2],[0,24,0],[16,16],[16,32],.25],
            ['head',[-4,24,-4],[4,32,4],[0,24,0],[0,0],[32,0],.5],
            ['right_arm',[4,12,-2],[4+arm,24,2],[5,22,0],[40,16],[40,32],.25],
            ['left_arm',[-4-arm,12,-2],[-4,24,2],[-5,22,0],[32,48],[48,48],.25],
            ['right_leg',[0,0,-2],[4,12,2],[2,12,0],[0,16],[0,32],.25],
            ['left_leg',[-4,0,-2],[0,12,2],[-2,12,0],[16,48],[0,48],.25]
        ];
    }
    function reidentify(model, makeId) {
        const map=new Map(), register=id=>{if(id&&!map.has(id))map.set(id,makeId());};
        function scan(nodes) { for(const n of nodes||[])if(typeof n==='object'){register(n.uuid);scan(n.children);} }
        (model.elements||[]).forEach(e=>register(e.uuid)); (model.groups||[]).forEach(g=>register(g.uuid)); scan(model.outliner);
        function tree(nodes){return (nodes||[]).map(n=>typeof n==='string'?(map.get(n)||n):{...n,uuid:map.get(n.uuid)||makeId(),children:tree(n.children)});}
        model.elements?.forEach(e=>{e.uuid=map.get(e.uuid)||makeId();}); model.groups?.forEach(g=>{g.uuid=map.get(g.uuid)||makeId();}); model.outliner=tree(model.outliner);
        model.animations?.forEach(a=>{a.uuid=makeId();a.animators=Object.fromEntries(Object.entries(a.animators||{}).map(([id,b])=>[map.get(id)||id,{...b,keyframes:(b.keyframes||[]).map(k=>({...k,uuid:makeId()}))}]));});
        model.textures?.forEach(t=>{t.uuid=makeId();if(t.source?.startsWith('data:')){delete t.path;delete t.relative_path;}});
        return model;
    }
    function convertAnimation(source,bones){
        const animators={};let length=source.animation_length||0;
        check(!source.particle_effects&&!source.sound_effects&&!source.timeline,'Animation effects/events cannot be baked as actor geometry. Import a bone-only clip.');
        const inverse=v=>typeof v==='number'?-v:`-(${v})`;
        function points(v,channel){
            if(v&&typeof v==='object'&&!Array.isArray(v)){
                check(!v.easing||v.easing==='linear','Custom GeckoLib easing requires the original GeckoLib project.');
                const result=[];if(v.pre!==undefined)result.push(...points(v.pre,channel));if(v.post!==undefined)result.push(...points(v.post,channel));check(result.length>0&&result.length<=2,'Unsupported animation keyframe.');return result;
            }
            const vector=Array.isArray(v)?v:[v,v,v];check(vector.length===3&&vector.every(n=>typeof n==='string'||Number.isFinite(n)),'Invalid animation vector.');
            return [{x:channel==='position'||channel==='rotation'?inverse(vector[0]):vector[0],y:channel==='rotation'?inverse(vector[1]):vector[1],z:vector[2]}];
        }
        for(const [name,channels] of Object.entries(source.bones||{})){
            const id=bones.get(name.toLowerCase());check(id,'No matching bone in selected actor: '+name);
            const keyframes=[];
            for(const channel of ['position','rotation','scale']){
                const value=channels[channel];if(value===undefined)continue;
                const timed=value&&typeof value==='object'&&!Array.isArray(value)&&value.pre===undefined&&value.post===undefined;
                for(const [stamp,v] of timed?Object.entries(value):[['0',value]]){
                    const time=Number(stamp);check(Number.isFinite(time)&&time>=0&&time<=300,'Invalid animation timestamp.');length=Math.max(length,time);
                    const interpolation=v?.lerp_mode||'linear';check(['linear','catmullrom','step'].includes(interpolation),'Unsupported interpolation '+interpolation);
                    keyframes.push({channel,time,interpolation,data_points:points(v,channel)});
                }
            }
            animators[id]={name,type:'bone',rotation_global:channels.relative_to?.rotation==='entity',keyframes};
        }
        check(Number.isFinite(length)&&length>=0&&length<=300,'Invalid animation length.');
        return {length,loop:source.loop===true?'loop':source.loop==='hold_on_last_frame'?'hold':'once',animators};
    }
    function centerReference(ref){
        if(!ref.blocks?.length)return ref.size?[-ref.size[0]/2,0,-ref.size[2]/2]:[-ref.width/2,0,ref.plane==='floor'?-ref.height/2:0];
        const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];
        for(const b of ref.blocks)for(let i=0;i<3;i++){min[i]=Math.min(min[i],b[i]);max[i]=Math.max(max[i],b[i]+1);}
        return [-(min[0]+max[0])/2,-min[1],-(min[2]+max[2])/2];
    }
    return {readNBT, normalizeReference, meshReference, playerParts, reidentify,convertAnimation,centerReference};
})();

/* Asterion Cutscene Studio. Load this file with Blockbench > Plugins > Load Plugin from File. */
(function () {
    'use strict';
    const Core = AsterionStudioCore;
    const lerp = (a, b, t) => a + (b - a) * t;
    function validateKeys(keys, duration) {
        if (!Array.isArray(keys) || !keys.length || keys.length > 100000) throw Error('Add at least one camera key (100,000 maximum).');
        let previous = -1;
        for (const k of keys) {
            if (!Number.isFinite(k.time) || k.time < 0 || k.time > duration || k.time <= previous) throw Error('Camera times must be unique, increasing, and inside the scene.');
            for (const field of ['position', 'target']) if (!Array.isArray(k[field]) || k[field].length !== 3 || !k[field].every(Number.isFinite)) throw Error('Camera vectors must contain three finite numbers.');
            if (Math.hypot(...k.target.map((v, i) => v - k.position[i])) < 1e-6) throw Error('Camera target must differ from its position.');
            if (!Number.isFinite(k.fov) || k.fov < 1 || k.fov > 160 || !Number.isFinite(k.roll)) throw Error('FOV must be 1–160 degrees; roll must be finite.');
            if (!['linear', 'smooth', 'hold'].includes(k.ease)) throw Error('Interpolation must be linear, smooth, or hold.');
            previous = k.time;
        }
        return keys;
    }
    function sampleCamera(keys, time) {
        if (time <= keys[0].time) return keys[0];
        if (time >= keys[keys.length - 1].time) return keys[keys.length - 1];
        let low=0,hi=keys.length-1;
        while(hi-low>1){const mid=(low+hi)>>>1;if(keys[mid].time<=time)low=mid;else hi=mid;}
        const a=keys[low],b=keys[hi];
        let t = (time - a.time) / (b.time - a.time);
        t = a.ease === 'hold' ? 0 : a.ease === 'smooth' ? t * t * (3 - 2 * t) : t;
        return {time, position: a.position.map((v, i) => lerp(v, b.position[i], t)), target: a.target.map((v, i) => lerp(v, b.target[i], t)), fov: lerp(a.fov, b.fov, t), roll: lerp(a.roll, b.roll, t), ease: a.ease};
    }
    // The same pure camera contract is exercised by the Node/Java parity tests.
    if (typeof module !== 'undefined' && module.exports && typeof Plugin === 'undefined') { module.exports = {sampleCamera, validateKeys, ...Core}; return; }
    const actions = [], listeners = [];
    let property, panel, helper, cameraHelper, pathLine, targetLine, grid, linked, baking = false, cameraScratch;
    let studioFormat, studioMode, style, ticker, referenceMesh, referenceKey, evaluating = false, transport = false, recording = null, lastTick = 0;
    const history = new WeakMap();
    let preparedReference=null;
    const cachedData = new WeakMap();
    const MAX_DURATION = 3600, MAX_EXPORT_SAMPLES = 500000;
    const defaults = () => ({duration: 5, fps: 60, aspect: 16 / 9, keys: [], fullbright: false, actors: [], clips: [], reference: null, gizmos: {},
        referencePosition: [0,0,0], referenceYaw: 0, referenceOpacity: .65, referenceVisible: true, worldOrigin: [0,0,0], anchorWorld: false, guides: true, showPath:true,showCamera:true,showTargets:true,recordRate: 20});
    function readData() {
        if(!Project)return defaults();const raw=Project.asterion_cutscene||'{}';let cached=cachedData.get(Project);
        if(cached?.raw!==raw){try{cached={raw,value:JSON.parse(raw)};}catch(_){cached={raw,value:{}};}cachedData.set(Project,cached);}
        return {...defaults(),...cached.value};
    }
    function data() {
        const value=readData();
        // Keep the immutable, potentially large reference shared; copy editable scene controls.
        return {...value,keys:value.keys.map(k=>({...k,position:[...k.position],target:[...k.target]})),actors:value.actors.map(a=>({...a})),clips:value.clips.map(c=>({...c})),worldOrigin:[...value.worldOrigin],referencePosition:[...value.referencePosition]};
    }
    function save(value) {
        const stack = history.get(Project) || []; stack.push(Project.asterion_cutscene || '');
        while (stack.length > 20 || (stack.length > 1 && stack.reduce((n,s)=>n+s.length,0)>8*1024*1024)) stack.shift(); history.set(Project, stack);
        Project.asterion_cutscene = JSON.stringify(value); Project.saved = false; refresh();
    }
    function report(e) { console.error(e); Blockbench.showMessageBox({title: 'Cutscene Studio', message: e.message}); }
    function guard(fn) { return function (...args) { try { const result=fn.apply(this,args); if(result?.catch)result.catch(report); return result; } catch (e) { report(e); } }; }
    function action(id, name, icon, fn) {
        const item = new Action('asterion_cs_' + id, {name, icon, condition: () => !!Project && !!Format.animation_mode, click: guard(fn)});
        actions.push(item); MenuBar.addAction(item, 'tools'); return item.id;
    }
    function clearHelpers() {
        if (!helper) return;
        helper.parent?.remove(helper);
        helper.traverse(o => { o.geometry?.dispose?.(); if (o.material) (Array.isArray(o.material) ? o.material : [o.material]).forEach(m => { if (typeof m?.dispose === 'function') m.dispose(); }); });
        helper = cameraHelper = pathLine = targetLine = grid = null;
    }
    function refresh() {
        if (!Project) { clearHelpers(); return; }
        const d = data();
        // Camera gizmos existed in preview builds. Remove their temporary marker on reload;
        // camera posing now uses viewport capture, pilot, sliders and manual keys only.
        const oldCameraMarker=d.gizmos?.camera&&Outliner.elements.find(e=>e.uuid===d.gizmos.camera);
        if(oldCameraMarker)oldCameraMarker.remove();
        if (panel?.vue) {
            Object.assign(panel.vue, {keys:d.keys.map(k=>({...k})),duration:d.duration,actors:d.actors,clips:d.clips,refName:d.reference?.name||'No reference loaded',
                refInfo:d.reference?.blocks ? d.reference.blocks.length.toLocaleString()+' solid blocks' : 'Image or model reference', origin:d.worldOrigin.join(', '),guides:d.guides,showPath:d.showPath,showCamera:d.showCamera,showTargets:d.showTargets});
            const index=Math.min(panel.vue.selectedKey,d.keys.length-1);panel.vue.selectedKey=index;panel.vue.draft=index>=0?JSON.parse(JSON.stringify(d.keys[index])):null;
        }
        clearHelpers();
        helper = new THREE.Group(); helper.name = 'Asterion cutscene guides'; helper.no_export = true;
        grid = new THREE.GridHelper(256, 16, 0x67c9bc, 0x424955); helper.add(grid);
        const cam = new THREE.PerspectiveCamera(70, d.aspect, 1, 24);
        cameraHelper = new THREE.CameraHelper(cam); helper.add(cameraHelper);
        const line = (points, color) => new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), new THREE.LineBasicMaterial({color, depthTest: false}));
        const points = [];
        // Long recordings can have thousands of keys. Keep the guide responsive while preserving
        // every key in the authored data and in exported playback.
        const guideStep=Math.max(1,Math.ceil(d.keys.length/2000));
        for(let i=0;i<d.keys.length;i+=guideStep)points.push(new THREE.Vector3(...d.keys[i].position).multiplyScalar(16));
        if(d.keys.length>1&&(d.keys.length-1)%guideStep)points.push(new THREE.Vector3(...d.keys.at(-1).position).multiplyScalar(16));
        pathLine = line(points, 0x50dfb1); helper.add(pathLine);
        pathLine.visible=d.showPath;
        if(d.showPath)helper.add(new THREE.Points(new THREE.BufferGeometry().setFromPoints(points),new THREE.PointsMaterial({color:0x50dfb1,size:7,sizeAttenuation:false,depthTest:false})));
        targetLine = line([new THREE.Vector3(), new THREE.Vector3()], 0xfbbf60); helper.add(targetLine);
        targetLine.visible=d.showTargets;cameraHelper.visible=d.showCamera;
        helper.visible=d.guides; helper.add(new THREE.AxesHelper(24));
        scene.add(helper); refreshReference(); updateCamera();
    }
    function poseCamera(camera, k) {
        camera.position.fromArray(k.position).multiplyScalar(16);
        cameraScratch ||= new THREE.Vector3();cameraScratch.fromArray(k.target).multiplyScalar(16);
        camera.up.set(0, 1, 0); camera.lookAt(cameraScratch);
        camera.rotateZ(-k.roll * Math.PI / 180); camera.fov = k.fov; camera.updateProjectionMatrix(); camera.updateMatrixWorld(true);
    }
    function updateCamera() {
        if (!Project || baking) return;
        if (panel?.vue) { panel.vue.time=(Timeline.time||0).toFixed(2); panel.vue.playhead=Timeline.time||0; panel.vue.playing=transport; panel.vue.recording=!!recording; panel.vue.cameraMode=linked?.mode||'free'; }
        const d = readData(); if (!d.keys.length || !cameraHelper) return;
        const k = sampleCamera(d.keys, Timeline.time || 0);
        poseCamera(cameraHelper.camera, k); cameraHelper.update();
        const linePosition=targetLine.geometry.getAttribute('position');
        linePosition.setXYZ(0,k.position[0]*16,k.position[1]*16,k.position[2]*16);linePosition.setXYZ(1,k.target[0]*16,k.target[1]*16,k.target[2]*16);linePosition.needsUpdate=true;
        if (linked && linked.mode === 'preview' && !recording) {
            const p = linked.preview;
            poseCamera(p.camera, k); p.controls.target.fromArray(k.target).multiplyScalar(16);
        }
        if (panel?.vue) { panel.vue.time = (Timeline.time || 0).toFixed(2); panel.vue.playhead=Timeline.time||0; }
    }
    function unlink() {
        if (!linked) return;
        const {preview: p, position, quaternion, target, fov, unlinked, ortho} = linked;
        linked = null; p.controls.unlinked = unlinked; p.camera.position.copy(position); p.camera.quaternion.copy(quaternion); p.controls.target.copy(target); p.setFOV(fov);
        if (ortho) p.setProjectionMode(true);
    }
    function togglePreview(mode='preview') {
        if (linked?.mode===mode) { unlink(); return; }
        if(linked)unlink();
        if (mode==='preview' && !data().keys.length) throw Error('Add a camera key first.');
        const p = Preview.selected; if (!p) throw Error('Select a viewport first.');
        const ortho = p.isOrtho; if (ortho) p.setProjectionMode(false);
        linked = {mode, preview: p, position: p.camera.position.clone(), quaternion: p.camera.quaternion.clone(), target: p.controls.target.clone(), fov: p.camera.fov, unlinked: p.controls.unlinked, ortho};
        p.controls.unlinked = mode==='preview';
        if(data().keys.length){ const k=sampleCamera(data().keys,Timeline.time||0);poseCamera(p.camera,k);p.controls.target.fromArray(k.target).multiplyScalar(16); }
        updateCamera();
        Blockbench.showQuickMessage(mode==='pilot'?'Pilot: move/orbit this viewport, then Capture or Record.':'Preview follows the timeline. Pilot unlocks camera movement.');
    }
    function editKey(index = -1, initialOverride = null) {
        const d = data(), p = Preview.selected;
        const initial = initialOverride || (index >= 0 ? d.keys[index] : {time: Math.min(d.duration, Timeline.time || 0), position: p ? p.camera.position.toArray().map(v => v / 16) : [3, 2, 4], target: p ? p.controls.target.toArray().map(v => v / 16) : [0, 1, 0], fov: p?.camera.fov || 70, roll: 0, ease: 'smooth'});
        new Dialog({id: 'asterion_camera_key', title: 'Camera key • positions in blocks', form: {
            time: {label: 'Time (seconds)', type: 'number', value: initial.time, min: 0, max: d.duration, step: .05},
            position: {label: 'Camera XYZ', type: 'vector', dimensions: 3, value: initial.position, step: .0625},
            target: {label: 'Look at XYZ', type: 'vector', dimensions: 3, value: initial.target, step: .0625},
            fov: {label: 'Vertical FOV', type: 'number', value: initial.fov, min: 1, max: 160},
            roll: {label: 'Roll (degrees)', type: 'number', value: initial.roll},
            ease: {label: 'To next key', type: 'select', options: {smooth: 'Smooth ease', linear: 'Linear', hold: 'Hold / hard cut'}, value: initial.ease}
        }, onConfirm: guard(function (k) {
            const keys = d.keys.slice(); if (index >= 0) keys.splice(index, 1);
            const same = keys.findIndex(v => Math.abs(v.time - k.time) < 1e-6); if (same >= 0) keys.splice(same, 1);
            keys.push(k); keys.sort((a, b) => a.time - b.time); validateKeys(keys, d.duration); save({...d, keys}); this.hide();
            selectCameraKey(data().keys.findIndex(v=>Math.abs(v.time-k.time)<1e-6));
        })}).show();
    }
    function settings() {
        const d = data(); new Dialog({id: 'asterion_cs_settings', title: 'Cutscene settings', form: {
            duration: {label: 'Duration (seconds, up to 3600)', type: 'number', value: d.duration, min: .05, max: MAX_DURATION},
            fps: {label: 'Actor samples / second', type: 'select', options: {20: '20', 30: '30', 60: '60', 120: '120'}, value: String(d.fps)},
            aspect: {label: 'Reference aspect ratio', type: 'number', value: d.aspect, min: .2, max: 5, step: .01},
            fullbright: {label: 'Fullbright actors', type: 'checkbox', value: d.fullbright},
            guides: {label:'Show camera path and block grid',type:'checkbox',value:d.guides}
        }, onConfirm: guard(function (v) {
            v.fps = Number(v.fps); if (!Number.isFinite(v.duration) || v.duration < .05 || v.duration > MAX_DURATION || !Number.isFinite(v.aspect) || v.aspect < .2 || v.aspect > 5) throw Error('Duration must be 0.05–3600 seconds; check the aspect ratio too.');
            if (d.keys.length) validateKeys(d.keys, v.duration); save({...d, ...v}); this.hide();
        })}).show();
    }
    function addPlayer() {
        if (Format.single_texture) throw Error('Add player rigs in a Generic Model scene so each actor can have its own skin. Existing GeckoLib projects can still be exported directly.');
        new Dialog({id: 'asterion_player', title: 'Add animatable player', form: {
            name: {label: 'Actor name', value: 'player'}, slim: {label: 'Slim arms (Alex)', type: 'checkbox', value: false},
            layers: {label:'Hat, jacket, sleeves and trouser layers',type:'checkbox',value:true}
        }, onConfirm(v) {
            this.hide(); const elements = [], groups = [], textures = [];
            Undo.initEdit({elements, groups, textures, outliner: true});
            const canvas = document.createElement('canvas'); canvas.width = canvas.height = 64;
            const ctx = canvas.getContext('2d');
            // Leave overlay UV islands transparent in the placeholder skin.
            ctx.fillStyle='#ba8a68';ctx.fillRect(0,0,32,16);ctx.fillRect(40,16,16,16);ctx.fillRect(32,48,16,16);
            ctx.fillStyle='#397e9c';ctx.fillRect(16,16,24,16);
            ctx.fillStyle='#3c415e';ctx.fillRect(0,16,16,16);ctx.fillRect(16,48,16,16);
            const skin = new Texture({name: (v.name || 'player') + '_skin.png', uv_width: 64, uv_height: 64}).fromDataURL(canvas.toDataURL()).add(false); textures.push(skin);
            const root = new Group({name: v.name || 'player', origin: [0, 0, 0]}).init(); groups.push(root);
            Core.playerParts(v.slim).forEach(([name, from, to, origin, uv_offset, overlayUV, inflate], i) => {
                const bone = new Group({name: root.name + '_' + name, origin}).addTo(root).init(); groups.push(bone);
                const cube = new Cube({name, from, to, origin, box_uv: true, uv_offset, color: i % 8}).addTo(bone).init();
                Object.values(cube.faces).forEach(face => { face.texture = skin.uuid; }); elements.push(cube);
                if(v.layers){
                    const layer=new Cube({name:name+'_layer',from:from.slice(),to:to.slice(),origin:origin.slice(),inflate,box_uv:true,uv_offset:overlayUV}).addTo(bone).init();
                    Object.values(layer.faces).forEach(face=>{face.texture=skin.uuid;});elements.push(layer);
                }
            });
            Undo.finishEdit('Add cutscene player'); root.select(); Canvas.updateAll();
            const d=data();d.actors.push({id:root.uuid,name:root.name,type:'player',skin:skin.uuid,slim:v.slim,rigVersion:2});save(d);
            Blockbench.showQuickMessage('Player + layers ready. Choose Replace skin on its actor card. Paint mode edits that skin directly.');
        }}).show();
    }
    function actorGroup(id) { return Group.all.find(g=>g.uuid===id); }
    function removeActor(id){
        const d=data(),actor=d.actors.find(a=>a.id===id),root=actorGroup(id);if(!actor)throw Error('That actor is already removed.');
        if(!root){d.actors=d.actors.filter(a=>a.id!==id);save(d);return;}
        const children=descendants(root),groups=[root,...children.filter(n=>n instanceof Group)],elements=children.filter(n=>!(n instanceof Group));
        Undo.initEdit({groups,elements,outliner:true});root.remove();Undo.finishEdit('Remove cutscene actor');
        d.actors=d.actors.filter(a=>a.id!==id);d.clips=d.clips.filter(c=>{const animation=Animation.all.find(a=>a.uuid===c.animation);return !!animation;});save(d);Canvas.updateAll();
    }
    function deleteAnimationClip(id){
        const animation=Animation.all.find(a=>a.uuid===id);if(!animation)throw Error('Animation is already removed.');
        Undo.initEdit({animations:[animation]});animation.remove(true);Undo.finishEdit('Remove cutscene animation');
        const d=data();d.clips=d.clips.filter(c=>c.animation!==id);save(d);
    }
    function marker(id){return Outliner.elements.find(e=>e.uuid===id);}
    function makeGizmo(kind){
        const d=data();if(kind==='world'&&!d.reference)throw Error('Import a world or schematic first.');
        const old=marker(d.gizmos?.[kind]);if(old)old.remove();
        let pos;if(kind==='camera'){const k=d.keys[panel?.vue?.selectedKey]||sampleCamera(d.keys,Timeline.time||0);if(!k)throw Error('Capture a camera key first.');pos=k.position;}else pos=d.referencePosition;
        const center=pos.map(v=>v*16),cube=new Cube({name:'[Cutscene '+(kind==='camera'?'Camera Pose':'World Placement')+' Gizmo]',from:center.map(v=>v-4),to:center.map(v=>v+4),origin:center,rotation:[0,0,0],export:false,color:kind==='camera'?2:4}).init();
        Undo.initEdit({elements:[cube],outliner:true});cube.select();Undo.finishEdit('Create '+kind+' placement gizmo');
        d.gizmos={...d.gizmos,[kind]:cube.uuid};save(d);Blockbench.showQuickMessage('Use Blockbench move/rotate gizmos, then click Apply gizmo in Cutscene Studio.');
    }
    function applyGizmo(kind){
        const d=data(),cube=marker(d.gizmos?.[kind]);if(!cube)throw Error('Create the '+kind+' gizmo first.');
        const center=cube.from.map((v,i)=>(v+cube.to[i])/32);
        const turn=(cube.rotation||[0,0,0]).map(Number);if(!turn.every(Number.isFinite))throw Error('The gizmo rotation is invalid.');
        if(kind==='camera'){
            const index=panel?.vue?.selectedKey,k=d.keys[index];if(!k)throw Error('Select a camera key first.');
            const direction=new THREE.Vector3(...k.target).sub(new THREE.Vector3(...k.position));
            direction.applyEuler(new THREE.Euler(...turn.map(v=>v*Math.PI/180),'XYZ'));
            k.position=center;k.target=new THREE.Vector3(...center).add(direction).toArray();validateKeys(d.keys,d.duration);
        }else {d.referencePosition=center;d.referenceYaw=((d.referenceYaw+turn[1])%360+360)%360;}
        cube.remove();d.gizmos={...d.gizmos};delete d.gizmos[kind];save(d);Canvas.updateAll();
    }
    function discardGizmo(kind){const d=data(),cube=marker(d.gizmos?.[kind]);if(cube)cube.remove();d.gizmos={...d.gizmos};delete d.gizmos[kind];save(d);Canvas.updateAll();}
    function repairPlayerSides(id){
        const d=data(),actor=d.actors.find(a=>a.id===id),root=actorGroup(id);if(!root||actor?.type!=='player')throw Error('Choose a player rig.');if(actor.rigVersion===2)return;
        const nodes=descendants(root),groups=nodes.filter(n=>n instanceof Group),elements=nodes.filter(n=>n instanceof Cube),parts=Core.playerParts(actor.slim);
        Undo.initEdit({groups,elements,outliner:true});
        for(const node of [...groups,...elements]){
            node.name=node.name.replace(/(right|left)_(arm|leg)(?=_layer$|$)/,(_,side,part)=>(side==='right'?'left':'right')+'_'+part);
            if(node instanceof Cube){const part=parts.find(p=>node.name===p[0]||node.name===p[0]+'_layer');if(part)node.uv_offset=[...part[node.name.endsWith('_layer')?5:4]];}
        }
        Undo.finishEdit('Correct player limb sides');Canvas.updateAll();actor.rigVersion=2;save(d);
    }
    function saveProject(){stopTransport();Codecs.project.export();}
    function centerWorld(){const d=data();if(!d.reference)return;d.referencePosition=Core.centerReference(d.reference);d.referenceYaw=0;save(d);fitReference();}
    function descendants(group) { const result=[];function visit(node){for(const c of node.children||[]){result.push(c);visit(c);}}visit(group);return result; }
    function replaceSkin(id) {
        const actor=data().actors.find(a=>a.id===id),root=actorGroup(id);if(!root)throw Error('Actor group was removed.');
        const project=Project;
        Blockbench.import({extensions:['png'],type:'64×64 Minecraft skin',readtype:'buffer'},guard(async files=>{
            if(!files?.[0])return;
            const source=files[0].content?.startsWith?.('data:')?files[0].content:await new Promise((resolve,reject)=>{
                const content=files[0].content;if(!content||typeof content==='string')throw Error('The skin file was not read as PNG bytes. Reload Cutscene Studio and choose the PNG again.');
                const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=()=>reject(Error('Cannot read skin file.'));reader.readAsDataURL(new Blob([content],{type:'image/png'}));
            });
            const img=await new Promise((resolve,reject)=>{const image=new Image();image.onload=()=>resolve(image);image.onerror=()=>reject(Error('Cannot read skin PNG.'));image.src=source;});
            if(img.width!==64||img.height!==64)throw Error('Use a modern 64×64 player skin. Convert legacy 64×32 skins first.');
            if(Project!==project)throw Error('Return to the original scene before replacing this skin.');
            const cubes=descendants(root).filter(e=>e instanceof Cube),textures=[];Undo.initEdit({elements:cubes,textures});
            const skin=new Texture({name:root.name+'_skin.png',uv_width:64,uv_height:64}).fromDataURL(source).add(false);textures.push(skin);
            cubes.forEach(c=>Object.values(c.faces).forEach(f=>{f.texture=skin.uuid;}));Undo.finishEdit('Replace actor skin');Canvas.updateAll();
            const d=data();d.actors.find(a=>a.id===id).skin=skin.uuid;save(d);
        }));
    }
    function actorTransform(id) {
        const root=actorGroup(id);if(!root)throw Error('Actor group was removed.');
        new Dialog({id:'asterion_actor_transform',title:'Actor placement',form:{
            offset:{label:'Move by XYZ (blocks)',type:'vector',dimensions:3,value:[0,0,0]},
            rotation:{label:'Root rotation (degrees)',type:'vector',dimensions:3,value:root.rotation.slice()}
        },onConfirm:guard(function(v){
            if(![...v.offset,...v.rotation].every(Number.isFinite))throw Error('Position and rotation must be finite.');
            const children=descendants(root),groups=[root,...children.filter(c=>c instanceof Group)],elements=children.filter(c=>!(c instanceof Group));
            Undo.initEdit({groups,elements,outliner:true});const offset=v.offset.map(n=>n*16);
            groups.forEach(g=>g.origin=g.origin.map((n,i)=>n+offset[i]));
            elements.forEach(e=>{if(e.from)e.from=e.from.map((n,i)=>n+offset[i]);if(e.to)e.to=e.to.map((n,i)=>n+offset[i]);if(e.origin)e.origin=e.origin.map((n,i)=>n+offset[i]);});
            root.rotation=v.rotation;Undo.finishEdit('Position cutscene actor');Canvas.updateAll();this.hide();
        })}).show();
    }
    function addBlock() {
        const elements = []; Undo.initEdit({elements, outliner: true});
        const cube = new Cube({name: 'scene_block_1m', from: [-8, -16, -8], to: [8, 0, 8], color: 4}).init(); elements.push(cube);
        Undo.finishEdit('Add one-block scene reference'); cube.select(); Canvas.updateAll();
    }
    function importActor() {
        if(Format.single_texture)throw Error('Create a Minecraft → Cutscene file to keep separate actor textures.');
        Blockbench.import({extensions: ['bbmodel','json'], type: 'Actor (.bbmodel / .geo.json)', readtype: 'text'}, guard(files => {
            if (!files?.[0]) return;
            const model = JSON.parse(files[0].content);
            if(model['minecraft:geometry']) { importGeo(model,files[0].name);return; }
            if(!model.meta || !Array.isArray(model.elements))throw Error('Choose a .bbmodel or Bedrock/GeckoLib .geo.json geometry file.');
            if((model.textures||[]).some(t=>!t.source?.startsWith('data:image/')))throw Error('Save this .bbmodel with embedded textures first, so each imported actor retains its own texture.');
            model.resolution||={width:64,height:64};
            if (model.meta?.model_format === 'geckolib_model' && Format.id !== 'geckolib_model'
                && (model.animations || []).some(a => Object.values(a.animators || {}).some(b => (b.keyframes || []).some(k => k.easing && k.easing !== 'linear'))))
                throw Error('This actor uses GeckoLib easing. Open its original GeckoLib project and export there, or bake the clip to standard keyframes before merging into a Generic scene. Merging directly would lose its easing.');
            Core.reidentify(model,guid);
            const rootId=guid(),name=(model.name||files[0].name||'actor').replace(/\.(bbmodel|json)$/,'');
            const root={name,uuid:rootId,origin:[0,0,0],children:model.outliner||model.elements.map(e=>e.uuid)};
            if(model.groups){model.groups.push({...root,children:undefined});model.outliner=[{uuid:rootId,children:root.children}];}else model.outliner=[root];
            for(const t of model.textures||[]){t.uv_width ||= model.resolution?.width||64;t.uv_height ||= model.resolution?.height||64;}
            Codecs.project.merge(model, files[0].path);
            const d=data();d.actors.push({id:rootId,name,type:'model'});save(d);actorGroup(rootId)?.select();
        }));
    }
    function importGeo(model,name) {
        const choices=model['minecraft:geometry'];if(!Array.isArray(choices)||!choices.length)throw Error('No geometry in file.');
        const run=geo=>{
            const elements=[],groups=[];const names=new Set();
            for(const bone of geo.bones||[]){if(names.has(bone.name))throw Error('Duplicate geometry bone '+bone.name);names.add(bone.name);if(bone.poly_mesh||bone.texture_meshes)throw Error('This geometry contains non-cube Bedrock extensions; open it in Blockbench and save as .bbmodel first.');}
            const parents=new Map((geo.bones||[]).map(b=>[b.name,b.parent]));
            for(const bone of geo.bones||[]){const seen=new Set([bone.name]);for(let parent=bone.parent;parent;parent=parents.get(parent)){if(!names.has(parent)||seen.has(parent))throw Error('Invalid/cyclic bone parent: '+parent);seen.add(parent);}for(const c of bone.cubes||[]){if(!Array.isArray(c.origin)||!Array.isArray(c.size)||c.origin.length!==3||c.size.length!==3||![...c.origin,...c.size,...(c.rotation||[]),...(c.pivot||[])].every(Number.isFinite))throw Error('Invalid geometry cube in '+bone.name);}}
            Undo.initEdit({elements,groups,outliner:true});
            const root=new Group({name:(geo.description?.identifier||name).replace(/^geometry\./,''),origin:[0,0,0]}).init();groups.push(root);
            const bones=new Map(),flip=(a=[0,0,0])=>[-a[0],a[1],a[2]],rotate=(a=[0,0,0])=>[-a[0],-a[1],a[2]];
            for(const bone of geo.bones||[]){const g=new Group({name:bone.name,origin:flip(bone.pivot),rotation:rotate(bone.rotation)}).addTo(root).init();bones.set(bone.name,g);groups.push(g);}
            for(const bone of geo.bones||[]){
                const group=bones.get(bone.name);if(bone.parent&&bones.has(bone.parent))group.addTo(bones.get(bone.parent));
                for(const c of bone.cubes||[]){
                    const from=[-(c.origin[0]+c.size[0]),c.origin[1],c.origin[2]],to=from.map((v,i)=>v+c.size[i]);
                    const cube=new Cube({name:bone.name,from,to,origin:flip(c.pivot),rotation:rotate(c.rotation),inflate:c.inflate??bone.inflate??0,
                        mirror_uv:c.mirror??bone.mirror??false,box_uv:Array.isArray(c.uv),uv_offset:Array.isArray(c.uv)?c.uv:[0,0]}).addTo(group).init();
                    if(c.uv&&!Array.isArray(c.uv))for(const [side,face] of Object.entries(cube.faces)){
                        const uv=c.uv[side];if(!uv){face.texture=null;continue;}
                        const size=uv.uv_size||([ 'up','down'].includes(side)?[c.size[0],c.size[2]]:['east','west'].includes(side)?[c.size[2],c.size[1]]:[c.size[0],c.size[1]]);
                        face.uv=[...uv.uv,uv.uv[0]+size[0],uv.uv[1]+size[1]];if(side==='up'||side==='down')face.uv=[face.uv[2],face.uv[3],face.uv[0],face.uv[1]];
                        face.rotation=uv.uv_rotation||0;
                    }
                    elements.push(cube);
                }
            }
            Undo.finishEdit('Import geometry actor');Canvas.updateAll();root.select();
            const d=data();d.actors.push({id:root.uuid,name:root.name,type:'model',textureWidth:geo.description?.texture_width||64,textureHeight:geo.description?.texture_height||64});save(d);
            setActorTexture(root.uuid);
        };
        if(choices.length===1)run(choices[0]);else new Dialog({id:'asterion_geometry',title:'Choose geometry',form:{geometry:{type:'select',options:Object.fromEntries(choices.map((g,i)=>[i,g.description?.identifier||String(i)])),value:'0'}},onConfirm:guard(function(v){this.hide();run(choices[Number(v.geometry)]);})}).show();
    }
    function setActorTexture(id) {
        const project=Project;
        Blockbench.import({extensions:['png'],type:'Actor texture',readtype:'image'},guard(files=>{
            if(!files?.[0])return;if(Project!==project)throw Error('Return to the actor scene first.');
            const root=actorGroup(id),actor=data().actors.find(a=>a.id===id);if(!root)throw Error('Actor was removed.');
            const elements=descendants(root).filter(e=>e.faces),textures=[];Undo.initEdit({elements,textures});
            const texture=new Texture({name:files[0].name,uv_width:actor.textureWidth||64,uv_height:actor.textureHeight||64}).fromFile(files[0]).add(false);textures.push(texture);
            elements.forEach(e=>Object.values(e.faces).forEach(face=>{if(face.texture!==null)face.texture=texture.uuid;}));
            Undo.finishEdit('Assign actor texture');Canvas.updateAll();
        }));
    }
    function importActorAnimation(id){
        const project=Project,root=actorGroup(id);if(!root)throw Error('Select an existing actor.');
        const bones=new Map();for(const bone of [root,...descendants(root).filter(e=>e instanceof Group)]){
            const name=bone.name.toLowerCase();if(bones.has(name))throw Error('Actor has duplicate bone names; rename them before importing animation.');bones.set(name,bone.uuid);
        }
        Blockbench.import({extensions:['json'],type:'Bedrock / GeckoLib bone animation',readtype:'text'},guard(files=>{
            if(!files?.[0])return;if(Project!==project)throw Error('Return to the actor scene first.');
            const source=JSON.parse(files[0].content);if(!source.animations)throw Error('Choose a .animation.json file.');
            const drafts=Object.entries(source.animations).map(([name,a])=>({name,...Core.convertAnimation(a,bones)}));
            const animations=[];Undo.initEdit({animations});for(const draft of drafts)animations.push(new Animation(draft).add());Undo.finishEdit('Import actor animations');animations[0]?.select();refresh();
        }));
    }
    function clearReference(){if(!referenceMesh)return;referenceMesh.parent?.remove(referenceMesh);referenceMesh.traverse(o=>{o.geometry?.dispose?.();o.material?.map?.dispose?.();o.material?.dispose?.();});referenceMesh=null;referenceKey=null;}
    function refreshReference(){
        const d=data(),ref=d.reference;
        if(!ref){clearReference();return;}
        const key=ref.id;
        if(referenceKey!==key){
            clearReference();referenceKey=key;referenceMesh=new THREE.Group();referenceMesh.no_export=true;
            if(ref.blocks){
                const {quads,exposedFaces}=preparedReference?.id===ref.id?preparedReference.mesh:Core.meshReference(ref),points=[],colors=[];
                preparedReference=null;
                const palette=ref.palette.map(name=>{
                    if(/grass|leaves|moss/.test(name))return new THREE.Color('#638653');if(/water|ice/.test(name))return new THREE.Color('#588daf');
                    if(/wood|log|plank|dirt/.test(name))return new THREE.Color('#95795a');if(/sand|birch/.test(name))return new THREE.Color('#cbbb89');
                    let hash=0;for(const ch of name)hash=(hash*31+ch.charCodeAt(0))|0;return new THREE.Color().setHSL((hash>>>0)%360/360,.12,.48);
                });
                for(const q of quads)for(const i of [0,1,2,0,2,3]){points.push(...q.corners[i].map(n=>n*16));colors.push(...palette[q.palette].toArray());}
                const geometry=new THREE.BufferGeometry();geometry.setAttribute('position',new THREE.Float32BufferAttribute(points,3));geometry.setAttribute('color',new THREE.Float32BufferAttribute(colors,3));geometry.computeVertexNormals();
                referenceMesh.add(new THREE.Mesh(geometry,new THREE.MeshBasicMaterial({vertexColors:true,side:THREE.DoubleSide,transparent:true,opacity:d.referenceOpacity,depthWrite:false})));
                referenceMesh.userData.quadCount=quads.length;
            }else if(ref.image){
                const texture=new THREE.TextureLoader().load(ref.image),mesh=new THREE.Mesh(new THREE.PlaneGeometry(ref.width*16,ref.height*16),new THREE.MeshBasicMaterial({map:texture,side:THREE.DoubleSide,transparent:true,opacity:d.referenceOpacity,depthWrite:false}));
                texture.magFilter=THREE.NearestFilter;
                if(ref.plane==='floor'){mesh.rotation.x=-Math.PI/2;mesh.position.set(ref.width*8,0,ref.height*8);}else mesh.position.set(ref.width*8,ref.height*8,0);
                referenceMesh.add(mesh);
            }
            scene.add(referenceMesh);
        }
        referenceMesh.position.fromArray(d.referencePosition).multiplyScalar(16);referenceMesh.rotation.y=d.referenceYaw*Math.PI/180;referenceMesh.visible=d.referenceVisible;
        referenceMesh.traverse(o=>{if(o.material)o.material.opacity=d.referenceOpacity;});
        if(panel?.vue&&ref.blocks)panel.vue.refInfo=ref.blocks.length.toLocaleString()+' blocks → '+referenceMesh.userData.quadCount.toLocaleString()+' quads';
    }
    async function decodeReference(content){
        let bytes=content instanceof Uint8Array?content:new Uint8Array(content);if(bytes.length>64*1024*1024)throw Error('Reference exceeds 64 MB.');
        if(bytes[0]===31&&bytes[1]===139){
            const reader=new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip')).getReader();let size=0,chunks=[];
            try{for(;;){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>64*1024*1024)throw Error('Expanded schematic exceeds 64 MB.');chunks.push(value);}}finally{await reader.cancel();}
            bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
        }
        return Core.normalizeReference(Core.readNBT(bytes));
    }
    function importReference(){
        const project=Project;Blockbench.import({extensions:['schem','schematic','nbt','json'],type:'Minecraft scene reference',readtype:'buffer'},guard(async files=>{
            if(!files?.[0])return;const f=files[0];
            const ref=/\.json$/i.test(f.name||f.path)?Core.normalizeReference(JSON.parse(new TextDecoder().decode(f.content))):await decodeReference(f.content);
            if(Project!==project)throw Error('Return to the scene you imported into.');
            // Check the optimized render budget before replacing the current reference.
            const mesh=Core.meshReference(ref),id=guid();preparedReference={id,mesh};
            const d=data();d.reference={...ref,id,name:f.name};d.referencePosition=Core.centerReference(ref);d.referenceYaw=0;
            if(ref.worldOrigin){d.worldOrigin=ref.worldOrigin;d.anchorWorld=true;}
            save(d);fitReference();
            Blockbench.showQuickMessage(`Imported ${ref.blocks.length.toLocaleString()} non-air blocks → ${mesh.quads.length.toLocaleString()} optimized quads.`,5000);
        }));
    }
    function importBlueprint(){
        const project=Project;Blockbench.import({extensions:['png'],type:'Blueprint image',readtype:'image'},guard(files=>{
            if(!files?.[0])return;const file=files[0];
            new Dialog({id:'asterion_blueprint',title:'Blueprint scale',form:{width:{label:'Width in blocks',type:'number',value:32,min:1,max:2048},height:{label:'Height / depth in blocks',type:'number',value:32,min:1,max:2048},plane:{label:'Placement',type:'select',options:{floor:'Ground plan',wall:'Vertical elevation'},value:'floor'}},onConfirm:guard(function(v){
                if(![v.width,v.height].every(n=>Number.isFinite(n)&&n>0&&n<=2048))throw Error('Blueprint dimensions must be between 0 and 2048 blocks.');
                if(Project!==project)throw Error('Return to the original scene.');if(typeof file.content!=='string'||!file.content.startsWith('data:image/'))throw Error('Could not read blueprint image.');
                const d=data();d.reference={...v,image:file.content,name:file.name,id:guid()};save(d);this.hide();fitReference();
            })}).show();
        }));
    }
    function fitReference(){
        if(!referenceMesh)return;unlink();const p=Preview.selected;if(!p)return;
        const box=new THREE.Box3().setFromObject(referenceMesh),center=box.getCenter(new THREE.Vector3()),size=box.getSize(new THREE.Vector3());
        p.controls.target.copy(center);p.camera.position.copy(center).add(new THREE.Vector3(1,.8,1).normalize().multiplyScalar(Math.max(size.length(),64)));p.camera.lookAt(center);
    }
    function referenceSettings(){const d=data();new Dialog({id:'asterion_reference_settings',title:'Reference and Minecraft alignment',form:{
        position:{label:'Reference offset (blocks)',type:'vector',dimensions:3,value:d.referencePosition},yaw:{label:'Reference yaw',type:'number',value:d.referenceYaw},opacity:{label:'Reference opacity',type:'number',value:d.referenceOpacity,min:.05,max:1,step:.05},
        world:{label:'World origin XYZ',type:'vector',dimensions:3,value:d.worldOrigin},anchor:{label:'Use this world origin in game',type:'checkbox',value:d.anchorWorld},visible:{label:'Show reference',type:'checkbox',value:d.referenceVisible}
    },onConfirm:guard(function(v){if(![...v.position,...v.world,v.yaw,v.opacity].every(Number.isFinite))throw Error('Alignment requires finite numbers.');save({...d,referencePosition:v.position,referenceYaw:v.yaw,referenceOpacity:Math.max(.05,Math.min(1,v.opacity)),worldOrigin:v.world,anchorWorld:v.anchor,referenceVisible:v.visible});this.hide();})}).show();}
    function capturePose(time) {
        const p=linked?.preview||Preview.selected;if(!p||p.isOrtho)throw Error('Select a perspective viewport to capture.');
        const direction=new THREE.Vector3();p.camera.getWorldDirection(direction);
        const position=p.camera.position.clone(),distance=Math.max(16,position.distanceTo(p.controls.target));
        return {time,position:position.toArray().map(v=>v/16),target:position.addScaledVector(direction,distance).toArray().map(v=>v/16),fov:p.camera.fov,roll:0,ease:'linear'};
    }
    function captureNow(){
        const d=data(),time=Math.min(d.duration,Timeline.time||0),k=capturePose(time);
        d.keys=d.keys.filter(v=>Math.abs(v.time-time)>1e-6);d.keys.push(k);d.keys.sort((a,b)=>a.time-b.time);validateKeys(d.keys,d.duration);save(d);
        Blockbench.showQuickMessage(`Viewport camera pose saved at ${time.toFixed(2)} seconds.`);
        return d.keys.findIndex(v=>Math.abs(v.time-time)<1e-6);
    }
    function addManualCameraKey(){
        const d=data(),time=Math.min(d.duration,Timeline.time||0),source=panel?.vue?.draft|| (d.keys.length?sampleCamera(d.keys,time):null);
        const initial=source?JSON.parse(JSON.stringify(source)):{time,position:[3,2,4],target:[0,1,0],fov:70,roll:0,ease:'smooth'};
        initial.time=time;editKey(-1,initial);
    }
    function duplicateCameraPose(){
        const d=data(),time=Math.min(d.duration,Timeline.time||0),source=panel?.vue?.draft|| (d.keys.length?sampleCamera(d.keys,time):null);
        if(!source)throw Error('Add a manual camera pose first.');
        const k={...JSON.parse(JSON.stringify(source)),time};d.keys=d.keys.filter(v=>Math.abs(v.time-time)>1e-6);d.keys.push(k);d.keys.sort((a,b)=>a.time-b.time);validateKeys(d.keys,d.duration);save(d);selectCameraKey(d.keys.findIndex(v=>v===k||Math.abs(v.time-time)<1e-6));
    }
    function selectCameraKey(index){stopTransport();const key=data().keys[index];if(!key)return;panel.vue.selectedKey=index;panel.vue.draft=JSON.parse(JSON.stringify(key));evaluateAt(key.time);}
    function inspectCamera(commit){
        const draft=panel.vue.draft;if(!draft)return;const k={...draft,time:Number(draft.time),position:draft.position.map(Number),target:draft.target.map(Number),fov:Number(draft.fov),roll:Number(draft.roll)};
        validateKeys([k],data().duration);
        if(commit){const d=data();d.keys[panel.vue.selectedKey]=k;validateKeys(d.keys,d.duration);save(d);}else{
            poseCamera(cameraHelper.camera,k);cameraHelper.update();targetLine.geometry.setFromPoints([new THREE.Vector3(...k.position).multiplyScalar(16),new THREE.Vector3(...k.target).multiplyScalar(16)]);
            if(linked?.mode==='preview'){poseCamera(linked.preview.camera,k);linked.preview.controls.target.fromArray(k.target).multiplyScalar(16);}
        }
    }
    function moveCamera(direction){
        const p=linked?.preview||Preview.selected;if(!p)throw Error('Select a viewport.');
        if(linked?.mode==='preview')togglePreview('pilot');
        const forward=new THREE.Vector3();p.camera.getWorldDirection(forward);const right=new THREE.Vector3(1,0,0).applyQuaternion(p.camera.quaternion);
        const delta=direction==='up'?new THREE.Vector3(0,1,0):direction==='down'?new THREE.Vector3(0,-1,0):direction==='left'?right.negate():direction==='right'?right:direction==='back'?forward.negate():forward;
        delta.multiplyScalar(Number(panel.vue.moveStep)*16);p.camera.position.add(delta);p.controls.target.add(delta);p.camera.updateMatrixWorld(true);
    }
    function focusActor(id){
        const root=actorGroup(id);if(!root?.mesh)throw Error('Choose an actor first.');scene.updateMatrixWorld(true);
        const center=new THREE.Box3().setFromObject(root.mesh).getCenter(new THREE.Vector3());
        if(!center.toArray().every(Number.isFinite))throw Error('Actor has no visible geometry.');
        if(panel.vue.draft){panel.vue.draft.target=center.toArray().map(n=>n/16);inspectCamera(true);}
        const p=linked?.preview||Preview.selected;if(p){p.controls.target.copy(center);p.camera.lookAt(center);}
    }
    function orbitShot(){
        const d=data(),target=panel.vue.draft?.target||[0,1,0];
        new Dialog({id:'asterion_orbit',title:'Create orbit camera path',form:{target:{label:'Orbit center XYZ (blocks)',type:'vector',dimensions:3,value:target},radius:{label:'Radius (blocks)',type:'number',value:4,min:.1},height:{label:'Height above target',type:'number',value:1},start:{label:'Start time',type:'number',value:0},end:{label:'End time',type:'number',value:d.duration},angle:{label:'Start angle',type:'number',value:0},sweep:{label:'Sweep degrees',type:'number',value:180},count:{label:'Path keys',type:'number',value:24,min:2,max:120}},onConfirm:guard(function(v){
            if(![...v.target,v.radius,v.height,v.start,v.end,v.angle,v.sweep,v.count].every(Number.isFinite)||v.radius<.1||v.start<0||v.end<=v.start||v.end>d.duration||!Number.isInteger(v.count)||v.count<2||v.count>120)throw Error('Check orbit range and dimensions.');
            const keys=d.keys.filter(k=>k.time<v.start||k.time>v.end);
            for(let i=0;i<v.count;i++){const t=i/(v.count-1),angle=(v.angle+v.sweep*t)*Math.PI/180;keys.push({time:v.start+(v.end-v.start)*t,position:[v.target[0]+Math.sin(angle)*v.radius,v.target[1]+v.height,v.target[2]+Math.cos(angle)*v.radius],target:[...v.target],fov:panel.vue.draft?.fov||70,roll:0,ease:'linear'});}
            keys.sort((a,b)=>a.time-b.time);validateKeys(keys,d.duration);save({...d,keys});selectCameraKey(0);this.hide();
        })}).show();
    }
    function evaluateAt(time){
        if(evaluating)return;evaluating=true;
        try{
            Timeline.time=time;const clips=readData().clips;
            if(!clips.length){Animator.preview();return;}
            Animator.showDefaultPose(true);
            for(const clip of clips){
                if(time<clip.start||time>clip.end)continue;
                const animation=Animation.all.find(a=>a.uuid===clip.animation);if(!animation)continue;
                const local=(time-clip.start)*clip.speed+clip.offset;
                Timeline.time=clip.loop&&animation.length>0?local%animation.length:Math.min(local,animation.length||local);
                Animator.stackAnimations([animation],false);
            }
            scene.updateMatrixWorld(true);
        }finally{Timeline.time=time;evaluating=false;updateCamera();}
    }
    function stopTransport(commit=true){
        transport=false;
        if(recording){const take=recording;recording=null;if(commit&&take.keys.length){const d=data();d.keys=d.keys.filter(k=>k.time<take.start||k.time>take.keys.at(-1).time);d.keys.push(...take.keys);d.keys.sort((a,b)=>a.time-b.time);validateKeys(d.keys,d.duration);save(d);}}
        updateCamera();
    }
    function playPause(){if(transport){stopTransport();return;}if(Timeline.playing)Timeline.pause();if(Timeline.time>=data().duration)Timeline.time=0;lastTick=Date.now();transport=true;updateCamera();}
    function recordCamera(){
        if(recording){stopTransport();return;}stopTransport();if(Timeline.playing)Timeline.pause();
        if(Timeline.time>=data().duration)Timeline.time=0;
        if(linked?.mode!=='pilot')togglePreview('pilot');
        const start=Timeline.time||0;recording={start,keys:[capturePose(start)]};lastTick=Date.now();transport=true;updateCamera();
    }
    function tickStudio(){
        if(!Project||!transport)return;
        const now=Date.now(),d=readData(),time=Math.min(d.duration,(Timeline.time||0)+Math.min(.1,(now-lastTick)/1000));lastTick=now;
        evaluateAt(time);
        if(recording&&(time-recording.keys.at(-1).time>=1/d.recordRate||time===d.duration))recording.keys.push(capturePose(time));
        if(time>=d.duration)stopTransport();
    }
    function addClip(index=-1){
        const d=data(),animations=Animation.all;if(!animations.length)throw Error('Create or import a timeline animation first.');
        const old=d.clips[index]||{animation:Animation.selected?.uuid||animations[0].uuid,start:Timeline.time||0,end:d.duration,speed:1,offset:0,loop:false};
        new Dialog({id:'asterion_clip',title:'Schedule animation clip',form:{
            animation:{label:'Animation',type:'select',options:Object.fromEntries(animations.map(a=>[a.uuid,a.name])),value:old.animation},
            start:{label:'Scene start (seconds)',type:'number',value:old.start},end:{label:'Scene end (seconds)',type:'number',value:old.end},
            offset:{label:'Source start (seconds)',type:'number',value:old.offset},speed:{label:'Playback speed',type:'number',value:old.speed},loop:{label:'Loop source animation',type:'checkbox',value:old.loop}
        },onConfirm:guard(function(v){
            if(![v.start,v.end,v.offset,v.speed].every(Number.isFinite)||v.start<0||v.end<=v.start||v.end>d.duration||v.offset<0||v.speed<=0||v.speed>20)throw Error('Check clip times and speed. The clip must fit inside the scene.');
            v.name=animations.find(a=>a.uuid===v.animation)?.name||'Animation';if(index>=0)d.clips[index]=v;else d.clips.push(v);save(d);this.hide();evaluateAt(Timeline.time);
        })}).show();
    }
    function toggleLayers(id){const root=actorGroup(id);if(!root)return;const elements=descendants(root).filter(e=>e.name?.endsWith('_layer'));if(!elements.length)throw Error('This actor has no player overlay layers.');Undo.initEdit({elements});const visible=!elements.some(e=>e.visibility);elements.forEach(e=>e.visibility=visible);Undo.finishEdit('Toggle player layers');Canvas.updateAll();}
    function nudgeReference(axis,amount){const d=data();d.referencePosition[axis]+=amount;save(d);}
    function markModelReference(id){const root=actorGroup(id);if(!root)return;Undo.initEdit({groups:[root],outliner:true});root.export=!root.export;Undo.finishEdit('Toggle model reference only');const d=data(),actor=d.actors.find(a=>a.id===id);if(actor)actor.reference=root.export===false;save(d);}
    function gatherMeshes() {
        return Outliner.elements.filter(e => e.export !== false && e.visibility !== false && e.mesh?.geometry?.attributes?.position).map(e => {
            for (let parent = e.parent; parent && typeof parent === 'object'; parent = parent.parent) if (parent.export === false || parent.visibility === false) return null;
            return {element: e, mesh: e.mesh};
        }).filter(Boolean);
    }
    function exportScene() {
        stopTransport();
        const d = data(); validateKeys(d.keys, d.duration);
        if(d.clips.some(c=>!Animation.all.some(a=>a.uuid===c.animation)))throw Error('A scheduled animation was deleted. Remove or replace its clip before export.');
        if (Project.view_mode && Project.view_mode !== 'textured') throw Error('Switch the viewport to Textured before exporting so model textures can be captured.');
        if (Outliner.elements.some(e => e.type === 'billboard' || e.type === 'armature' || e.getArmature?.())) throw Error('Billboards and deforming armatures need conversion to rigid cube/mesh actors before export.');
        if (typeof AnimationController !== 'undefined' && AnimationController.selected) throw Error('Select a timeline animation, not an animation controller, before baking.');
        const objects = gatherMeshes(), frames = Math.ceil(d.duration * d.fps) + 1, samples=objects.length*frames;
        if (samples > MAX_EXPORT_SAMPLES) throw Error(`This ${Math.round(d.duration)}-second scene would bake ${samples.toLocaleString()} object samples. The runtime export budget is ${MAX_EXPORT_SAMPLES.toLocaleString()}. Lower FPS, split the cutscene into chapters, or mark static/reference models as Reference only.`);
        const output = {format: 'asterion_cutscene', version: 1, units: 'blocks', duration: d.duration, fps: d.fps, aspect: d.aspect, fullbright: d.fullbright, camera: d.keys, textures: [], objects: []};
        if(d.anchorWorld)output.world_origin=d.worldOrigin;
        // Rebase an edited world reference back onto its captured coordinates. The same rigid
        // transform applies to cameras and actor matrices, keeping placement exact after moving it.
        const worldBasis=d.anchorWorld&&d.reference?.worldOrigin
            ?new THREE.Matrix4().makeRotationY(-d.referenceYaw*Math.PI/180).multiply(new THREE.Matrix4().makeTranslation(...d.referencePosition.map(v=>-v*16)))
            :null;
        if(worldBasis)output.camera=d.keys.map(k=>({...k,...Object.fromEntries(['position','target'].map(field=>[field,new THREE.Vector3(...k[field]).multiplyScalar(16).applyMatrix4(worldBasis).multiplyScalar(1/16).toArray()]))}));
        const textureIds = new Map();
        function textureIndex(material) {
            const texture = Texture.all.find(t => t.getMaterial() === material || (material?.map && t.getMaterial()?.map === material.map));
            if (!texture) return -1;
            if (!textureIds.has(texture.uuid)) {
                if (texture.frameCount > 1) throw Error('Animated textures are not supported by this exporter; use a static texture.');
                textureIds.set(texture.uuid, output.textures.length); output.textures.push({name: texture.name, png: texture.getBase64()});
            }
            return textureIds.get(texture.uuid);
        }
        for (const {element, mesh} of objects) {
            const g = mesh.geometry, pos = g.attributes.position, uv = g.attributes.uv, normals = g.attributes.normal;
            const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
            const parts = (g.groups.length ? g.groups : [{start: 0, count: g.index?.count || pos.count, materialIndex: 0}]).map(group => {
                const material = Array.isArray(mesh.material) ? materials[group.materialIndex || 0] : mesh.material, vertices = [];
                if (material?.visible === false) return null;
                for (let i = group.start; i < group.start + group.count; i++) {
                    const n = g.index ? g.index.getX(i) : i;
                    vertices.push([pos.getX(n), pos.getY(n), pos.getZ(n), uv ? uv.getX(n) : 0, uv ? 1 - uv.getY(n) : 0, normals ? normals.getX(n) : 0, normals ? normals.getY(n) : 1, normals ? normals.getZ(n) : 0]);
                }
                return {texture: textureIndex(material), vertices};
            }).filter(Boolean);
            output.objects.push({name: element.name, parts, samples: []});
        }
        const oldTime = Timeline.time, oldPlaying = Timeline.playing;
        if (oldPlaying) Timeline.pause();
        baking = true;
        try {
            for (let frame = 0; frame < frames; frame++) {
                const time = Math.min(frame / d.fps, d.duration); evaluateAt(time); scene.updateMatrixWorld(true);
                objects.forEach(({mesh}, i) => {
                    // Full affine matrices retain nonuniform parent scales and shear exactly at each sample.
                    const matrix = (worldBasis?worldBasis.clone().multiply(mesh.matrixWorld):mesh.matrixWorld).toArray().map((v, j) => j % 4 === 3 ? v : v / 16);
                    if (!matrix.every(Number.isFinite)) throw Error('Animation produced a non-finite transform in ' + output.objects[i].name);
                    output.objects[i].samples.push(matrix);
                });
            }
        } finally { baking = false; evaluateAt(oldTime); updateCamera(); if (oldPlaying) Timeline.start(); }
        const content = JSON.stringify(output);
        if (content.length > 64 * 1024 * 1024) throw Error('Export exceeds 64 MB. Reduce duration, geometry, or sample rate.');
        Blockbench.export({type: 'Asterion cutscene', extensions: ['json'], name: (Project.name || 'scene') + '.cutscene', savetype: 'text', content});
    }
    Plugin.register('asterion_cutscene', {
        title: 'Asterion Cutscene Studio', author: 'Asterion', description: 'Visual cutscene editor with layered players, world references and Asterion playback.', icon: 'movie', version: '0.3.0', min_version: '4.12.0', variant: 'both',
        onload() {
            property = new Property(ModelProject, 'string', 'asterion_cutscene', {default: ''});
            studioFormat=new ModelFormat('asterion_cutscene',{name:'Cutscene',description:'Asterion cinematic scene • players, actors, world references and cameras',icon:'movie',category:'minecraft',
                meshes:true,billboards:true,splines:true,texture_meshes:true,armature_rig:true,animated_textures:true,rotate_cubes:true,bone_rig:true,centered_grid:true,optional_box_uv:true,per_texture_uv_size:true,uv_rotation:true,animation_mode:true,
                onSetup(project,isNew){if(!isNew)return;Project.name='cutscene';new Animation({name:'animation.cutscene',length:5,loop:'once'}).add().select();save(defaults());studioMode?.select();}});
            studioMode=new Mode('asterion_cutscene',{name:'Cutscene',icon:'movie',default_tool:'move_tool',condition:()=>!!Project&&!!Format.animation_mode,onSelect:refresh,onUnselect:()=>stopTransport()});
            style=Blockbench.addCSS(`.asterion-studio{padding:10px;display:flex;flex-direction:column;gap:10px}.asterion-studio .cs-row{display:flex;gap:5px;flex-wrap:wrap;align-items:center}.asterion-studio button{min-height:28px;padding:3px 8px}.asterion-studio .cs-tabs button.active{background:var(--color-accent);color:var(--color-accent_text)}.asterion-studio .cs-card{background:var(--color-back);border:1px solid var(--color-border);border-radius:5px;padding:9px;margin:6px 0}.asterion-studio small{opacity:.7;display:block}.asterion-studio input[type=range]{width:100%}.asterion-studio .cs-bar{height:5px;background:var(--color-accent);border-radius:3px;margin-top:6px}.asterion-studio .cs-primary{background:var(--color-accent);color:var(--color-accent_text)}.asterion-studio .cs-key-list{max-height:310px;overflow-y:auto;overscroll-behavior:contain;padding-right:4px;border-top:1px solid var(--color-border);border-bottom:1px solid var(--color-border)}`);
            const create = new Action('asterion_cs_new', {name: 'Cutscene: New Scene', icon: 'movie', click: guard(() => {
                newProject(studioFormat);
            })}); actions.push(create); MenuBar.addAction(create, 'file');
            action('settings', 'Cutscene: Scene Settings', 'settings', settings);
            action('undo_camera', 'Cutscene: Undo Camera / Settings Edit', 'undo', () => {
                const stack = history.get(Project); if (!stack?.length) return;
                Project.asterion_cutscene = stack.pop(); Project.saved = false; refresh();
            });
            action('player', 'Cutscene: Add Player Rig', 'person', addPlayer);
            action('block', 'Cutscene: Add One-block Reference', 'deployed_code', addBlock);
            action('import', 'Cutscene: Import Actor / Scene (.bbmodel)', 'folder_open', importActor);
            action('key', 'Cutscene: Add Manual Camera Pose', 'add_a_photo', addManualCameraKey);
            action('capture_view', 'Cutscene: Capture View as Camera Pose', 'photo_camera', captureNow);
            action('preview', 'Cutscene: Toggle Camera Preview', 'videocam', ()=>togglePreview());
            action('pilot', 'Cutscene: Pilot Camera', 'videocam', ()=>togglePreview('pilot'));
            action('record', 'Cutscene: Record Camera Movement', 'fiber_manual_record', recordCamera);
            action('reference', 'Cutscene: Import World / Schematic', 'landscape', importReference);
            action('blueprint', 'Cutscene: Import Blueprint Image', 'image', importBlueprint);
            action('export', 'Cutscene: Export for Asterion', 'movie', exportScene);
            action('remove_actor', 'Cutscene: Remove Selected Actor', 'delete', () => {const selected=data().actors.find(a=>a.id===Group.first_selected?.uuid)||data().actors.find(a=>a.id===Group.first_selected?.parent?.uuid);if(!selected)throw Error('Select an actor root in the Outliner first.');removeActor(selected.id);});
            action('world_gizmo', 'Cutscene: Create World Placement Gizmo', 'open_with', () => makeGizmo('world'));
            action('save', 'Cutscene: Save Editable Scene', 'save', saveProject);
            panel = new Panel('asterion_cutscene_panel', {name: 'Cutscene Studio', icon: 'movie', condition: () => !!Project && !!Format.animation_mode,
                default_position: {slot: 'right_bar'}, component: {
                    data() { return {tab:'director',selectedKey:0,draft:null,moveStep:.25,focusId:'',guides:true,showPath:true,showCamera:true,showTargets:true,axes:['X','Y','Z'],keys:[],actors:[],clips:[],refName:'No reference loaded',refInfo:'',origin:'0, 0, 0',time:'0.00',duration:5,playhead:0,playing:false,recording:false,cameraMode:'free'}; },
                    methods: {
                        capture:guard(()=>selectCameraKey(captureNow())),manualPose:guard(addManualCameraKey),duplicatePose:guard(duplicateCameraPose),selectKey:guard(selectCameraKey),inspect:guard(inspectCamera),moveCamera:guard(moveCamera),focusActor:guard(focusActor),orbit:guard(orbitShot),toggleGuide:guard(field=>{const d=data();d[field]=!d[field];save(d);}),settings:guard(settings),preview:guard(()=>togglePreview()),pilot:guard(()=>togglePreview('pilot')),exportScene:guard(exportScene),
                        play:guard(playPause),record:guard(recordCamera),cancelRecord:guard(()=>stopTransport(false)),
                        scrub:guard(function(){stopTransport();evaluateAt(Number(this.playhead));}),
                        player:guard(addPlayer),importActor:guard(importActor),selectActor:guard(id=>{actorGroup(id)?.select();Modes.options.edit.select();}),
                        importAnimation:guard(importActorAnimation),skin:guard(replaceSkin),texture:guard(setActorTexture),layers:guard(toggleLayers),place:guard(actorTransform),modelReference:guard(markModelReference),
                        saveProject:guard(saveProject),repairSides:guard(repairPlayerSides),removeActor:guard(removeActor),deleteAnimation:guard(deleteAnimationClip),worldGizmo:guard(()=>makeGizmo('world')),applyGizmo:guard(applyGizmo),discardGizmo:guard(discardGizmo),centerWorld:guard(centerWorld),importReference:guard(importReference),blueprint:guard(importBlueprint),align:guard(referenceSettings),fit:guard(fitReference),nudge:guard(nudgeReference),
                        clearReference:guard(()=>{save({...data(),reference:null});}),toggleReference:guard(()=>{const d=data();d.referenceVisible=!d.referenceVisible;save(d);}),
                        addClip:guard(()=>addClip()),editClip:guard(addClip),removeClip:guard(i=>{const d=data();d.clips.splice(i,1);save(d);evaluateAt(Timeline.time);}),
                        animate:guard(()=>{stopTransport();unlink();Modes.options.animate.select();}),
                        edit: guard(i => editKey(i)),
                        jump(i) { stopTransport();evaluateAt(this.keys[i].time); },
                        remove(i) { const d = data(); d.keys.splice(i, 1); save(d); }
                    },
                    template: `<div class="asterion-studio">
                      <div class="cs-row"><b>● CUTSCENE STUDIO</b><button @click="saveProject" title="Save editable scene, embedded world reference and textures">Save scene</button><button @click="settings" title="Scene settings">⚙</button></div>
                      <div class="cs-row"><button class="cs-primary" @click="play">{{playing?'Ⅱ Pause':'▶ Play'}}</button><b>{{time}} / {{duration}} s</b><button @click="animate">Open Blockbench Timeline</button></div>
                      <input type="range" min="0" :max="duration" step="0.0166667" v-model="playhead" @input="scrub" aria-label="Scene time">
                      <div class="cs-row cs-tabs"><button v-for="t in ['director','actors','world','clips']" :class="{active:tab===t}" @click="tab=t">{{t}}</button></div>
                      <div v-if="tab==='director'"><div class="cs-row"><button @click="preview">{{cameraMode==='preview'?'Exit preview':'Camera preview'}}</button><button @click="pilot">{{cameraMode==='pilot'?'Exit pilot':'Pilot camera'}}</button></div>
                        <div class="cs-row"><button class="cs-primary" @click="capture">📷 Take viewport pose</button><button @click="manualPose">＋ Manual pose at playhead</button><button @click="duplicatePose">Duplicate pose here</button><button @click="record">{{recording?'■ Save recording':'● Record movement'}}</button><button v-if="recording" @click="cancelRecord">Discard</button></div>
                        <div class="cs-row"><button @click="moveCamera('forward')">Forward</button><button @click="moveCamera('back')">Back</button><button @click="moveCamera('left')">Left</button><button @click="moveCamera('right')">Right</button><button @click="moveCamera('up')">Up</button><button @click="moveCamera('down')">Down</button></div><label>Move step · {{moveStep}} blocks<input type="range" min="0.0625" max="4" step="0.0625" v-model="moveStep"></label>
                        <div class="cs-row"><button @click="orbit">Orbit shot…</button><button @click="toggleGuide('guides')">Guides {{guides?'on':'off'}}</button><button @click="toggleGuide('showPath')">Path {{showPath?'on':'off'}}</button><button @click="toggleGuide('showCamera')">Frustum {{showCamera?'on':'off'}}</button><button @click="toggleGuide('showTargets')">Look line {{showTargets?'on':'off'}}</button></div>
                        <div class="cs-row"><select v-model="focusId"><option value="">Focus on actor…</option><option v-for="actor in actors" :value="actor.id">{{actor.name}}</option></select><button @click="focusActor(focusId)">Aim at actor</button></div>
                        <div class="cs-card" v-if="draft"><b>Camera key {{selectedKey+1}} · {{draft.time.toFixed(2)}} s</b><small>Drag sliders to preview; release to save. Coordinates are in blocks.</small>
                          <div v-for="field in ['position','target']"><b>{{field==='position'?'Camera position':'Look-at position'}}</b><div v-for="(axis,i) in axes" class="cs-row"><span>{{axis}}</span><input style="flex:1;width:80px" type="range" min="-64" max="64" step="0.0625" v-model="draft[field][i]" @input="inspect(false)" @change="inspect(true)"><input style="width:65px" type="number" step="0.0625" v-model="draft[field][i]" @change="inspect(true)"></div></div>
                          <label>Vertical FOV · {{draft.fov}}°<input type="range" min="1" max="160" step="1" v-model="draft.fov" @input="inspect(false)" @change="inspect(true)"></label><label>Roll · {{draft.roll}}°<input type="range" min="-180" max="180" step="1" v-model="draft.roll" @input="inspect(false)" @change="inspect(true)"></label>
                          <select v-model="draft.ease" @change="inspect(true)"><option value="linear">Linear motion</option><option value="smooth">Smooth ease</option><option value="hold">Hold → hard cut</option></select></div>
                        <small>Take viewport pose saves exactly what the active perspective viewport sees: camera position, view direction and FOV, at the current playhead. It replaces only a key already at that exact time.</small><small>Manual pose opens exact time, camera XYZ, look-at XYZ, FOV, roll and interpolation fields. Duplicate pose copies the selected/interpolated pose to the current playhead. Recording replaces keys in its interval. Green = path; amber = look direction.</small>
                        <b>Camera keyframes · {{keys.length}}</b><div class="cs-key-list"><div class="cs-card" v-for="(key,i) in keys" :key="i"><div class="cs-row"><button @click="jump(i)">{{key.time.toFixed(2)}} s</button><button @click="selectKey(i)">{{key.fov}}° · {{key.ease}}</button><button @click="edit(i)" title="Time and precise values">Edit</button><button @click="remove(i)" title="Delete key">×</button></div><small>XYZ {{key.position.map(v=>v.toFixed(2)).join(', ')}}</small></div></div>
                        <small v-if="!keys.length">Move your viewport to frame the shot, then capture your first key.</small></div>
                      <div v-if="tab==='actors'"><div class="cs-row"><button @click="player">＋ Player</button><button @click="importActor">Import model</button></div><small>Independent textures · 16 model units = 1 block. Use Animate bones for native keyframe editing.</small>
                        <div class="cs-card" v-for="actor in actors" :key="actor.id"><b>{{actor.name}}</b><small>{{actor.reference?'Reference only':actor.type}}</small><div class="cs-row"><button @click="selectActor(actor.id)">Select / move</button><button @click="place(actor.id)">Position</button><button @click="importAnimation(actor.id)">Animation JSON</button><button v-if="actor.type==='player'" @click="skin(actor.id)">Skin PNG</button><button v-else @click="texture(actor.id)">Texture PNG</button><button v-if="actor.type==='player'" @click="layers(actor.id)">Layers</button><button v-if="actor.type==='player' &amp;&amp; actor.rigVersion!==2" @click="repairSides(actor.id)">Fix limb sides</button><button @click="modelReference(actor.id)">Reference only</button><button @click="removeActor(actor.id)">Remove</button></div></div></div>
                      <div v-if="tab==='world'"><div class="cs-row"><button @click="importReference">Import world / schematic</button><button @click="blueprint">Blueprint PNG</button></div><div class="cs-card"><b>{{refName}}</b><small>{{refInfo}}</small><small>Playback world origin: {{origin}}</small><div class="cs-row"><button @click="align">Position / align</button><button @click="fit">Frame world</button><button @click="centerWorld">Center structure</button><button @click="worldGizmo">Move / rotate gizmo</button><button @click="applyGizmo('world')">Apply gizmo</button><button @click="discardGizmo('world')">Discard gizmo</button><button @click="toggleReference">Show / hide</button><button @click="clearReference">Remove</button></div></div><small>Move reference by one block:</small><div class="cs-row"><button @click="nudge(0,-1)">X−</button><button @click="nudge(0,1)">X＋</button><button @click="nudge(1,-1)">Y−</button><button @click="nudge(1,1)">Y＋</button><button @click="nudge(2,-1)">Z−</button><button @click="nudge(2,1)">Z＋</button></div><small>Move/rotate gizmo creates a temporary selectable marker. Use Blockbench’s normal move arrows or rotation rings, then Apply gizmo. Terrain is an optimized block-volume reference; it is not exported as actors.</small></div>
                      <div v-if="tab==='clips'"><div class="cs-row"><button @click="addClip">＋ Schedule animation</button><button @click="animate">Edit animations</button></div><small>Without clips, the active native animation plays. Scheduled clips use scene time, speed, source offset and optional looping. Overlapping clips combine; they do not crossfade.</small><div class="cs-card" v-for="(clip,i) in clips"><b>{{clip.name}}</b><small>{{clip.start}}–{{clip.end}} s · ×{{clip.speed}} {{clip.loop?'· loop':''}}</small><div class="cs-bar" :style="{marginLeft:(100*clip.start/duration)+'%',width:(100*(clip.end-clip.start)/duration)+'%'}"></div><div class="cs-row"><button @click="editClip(i)">Edit</button><button @click="removeClip(i)">Remove schedule</button></div></div><small>Remove an imported animation from Blockbench’s Animate tab, or use Tools → Remove Selected Actor for models.</small></div>
                      <button class="cs-primary" @click="exportScene">Export for Asterion ↗</button>
                    </div>`
                }});
            const listen = (name, fn, target = Blockbench) => { target.on(name, fn); listeners.push([target, name, fn]); };
            listen('display_animation_frame', ()=>{if(!evaluating&&!baking&&data().clips.length)evaluateAt(Timeline.time);else updateCamera();}); listen('select_project', refresh);
            listen('parsed', ()=>{refresh();if(Format.id==='asterion_cutscene')studioMode.select();}, Codecs.project);
            listen('compile', ({model})=>{if(Project)model.asterion_cutscene=Project.asterion_cutscene||JSON.stringify(defaults());}, Codecs.project);
            listen('unselect_project', () => { stopTransport(false);unlink(); clearHelpers();clearReference(); });
            listen('close_project', () => { stopTransport(false);unlink(); clearHelpers();clearReference(); });
            ticker=setInterval(guard(tickStudio),33);
            if (Project) refresh();
        },
        onunload() { stopTransport(false);clearInterval(ticker);unlink(); clearHelpers();clearReference(); listeners.forEach(([target, name, fn]) => target.removeListener(name, fn)); actions.forEach(a => a.delete()); panel?.delete();studioMode?.delete();studioFormat?.delete();style?.delete(); property?.delete(); }
    });
})();
