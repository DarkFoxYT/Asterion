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
