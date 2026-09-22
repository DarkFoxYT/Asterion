// Rebuild the editable demo without external packages: node tools/blockbench/create-demo.cjs
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
let serial = 0;
const uuid = () => '00000000-0000-4000-8000-' + (++serial).toString(16).padStart(12, '0');
function png(color) {
    function chunk(name, bytes) {
        const data = Buffer.concat([Buffer.from(name), bytes]); let crc = -1;
        for (const byte of data) { crc ^= byte; for (let n = 0; n < 8; n++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0); }
        const head = Buffer.alloc(4), tail = Buffer.alloc(4); head.writeUInt32BE(bytes.length); tail.writeUInt32BE((crc ^ -1) >>> 0);
        return Buffer.concat([head, data, tail]);
    }
    const header = Buffer.alloc(13); header.writeUInt32BE(1); header.writeUInt32BE(1, 4); header[8] = 8; header[9] = 6;
    return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', header), chunk('IDAT', zlib.deflateSync(Buffer.from([0,...color,255]))), chunk('IEND', Buffer.alloc(0))]).toString('base64');
}
const colors = [[182,133,100], [44,131,150], [51,59,85], [66,78,85], [214,160,66]];
const textures = colors.map((c, i) => ({uuid: uuid(), name: 'demo_' + i + '.png', source: 'data:image/png;base64,' + png(c), mode: 'bitmap', saved: false, internal: true, uv_width: 1, uv_height: 1}));
const elements = [], outliner = [], animators = {};
function cube(name, from, to, texture, parent) {
    const id = uuid(); elements.push({name, type: 'cube', uuid: id, from, to, origin: [0,0,0], box_uv: false, faces: Object.fromEntries(['north','south','east','west','up','down'].map(face => [face, {uv: [0,0,1,1], texture}]))});
    parent.push(id); return id;
}
const root = {name: 'demo_player', uuid: uuid(), origin: [0,0,0], children: []}; outliner.push(root);
const parts = [
    ['body',[-4,12,-2],[4,24,2],[0,24,0],1], ['head',[-4,24,-4],[4,32,4],[0,24,0],0],
    ['right_arm',[-8,12,-2],[-4,24,2],[-5,22,0],0], ['left_arm',[4,12,-2],[8,24,2],[5,22,0],0],
    ['right_leg',[-4,0,-2],[0,12,2],[-2,12,0],2], ['left_leg',[0,0,-2],[4,12,2],[2,12,0],2]
];
for (const [name, from, to, origin, texture] of parts) {
    const bone = {name, uuid: uuid(), origin, children: []}; root.children.push(bone); cube(name, from, to, texture, bone.children);
    if (name === 'right_arm') animators[bone.uuid] = {name, type: 'bone', keyframes: [[0,0],[1,-110],[2,-85],[3,-110],[4,0],[5,0]].map(([time,x]) => ({uuid: uuid(), channel: 'rotation', time, interpolation: 'linear', data_points: [{x:String(x), y:'0', z:'0'}]}))};
}
cube('stage_4_by_4_blocks',[-32,-4,-32],[32,0,32],3,outliner);
cube('one_block_reference',[16,0,8],[32,16,24],4,outliner);
const camera = [
    {time:0,position:[3,2.2,4],target:[0,1,0],fov:55,roll:0,ease:'smooth'},
    {time:2.5,position:[-3,1.6,3],target:[0,1.3,0],fov:45,roll:0,ease:'smooth'},
    {time:5,position:[-2,2.5,-3],target:[0,1,0],fov:65,roll:0,ease:'linear'}
];
const model = {meta:{format_version:'4.10',model_format:'asterion_cutscene',box_uv:false},name:'cutscene_demo',resolution:{width:64,height:64},elements,outliner,textures,
    animations:[{uuid:uuid(),name:'animation.demo.wave',length:5,loop:'once',animators}],
    asterion_cutscene:JSON.stringify({duration:5,fps:60,aspect:16/9,fullbright:true,keys:camera,actors:[{id:root.uuid,name:root.name,type:'model'}]})};
fs.writeFileSync(path.join(__dirname,'cutscene_demo.bbmodel'),JSON.stringify(model,null,2));
console.log('Created editable five-second camera-orbit and player-wave demo.');
