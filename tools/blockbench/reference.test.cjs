// node tools/blockbench/reference.test.cjs [optional local schematic]
const assert=require('node:assert/strict');
const fs=require('node:fs');
const zlib=require('node:zlib');
const {normalizeReference,meshReference,readNBT,centerReference,playerParts}=require('./asterion_cutscene.js');

// A small build inside a large selection must count solids, not selection volume.
// Exercise a nonzero air ID and a multibyte solid palette ID together.
const padded=new Uint8Array(160**3+60).fill(7);
for(let i=0;i<60;i++){padded[i*2]=130;padded[i*2+1]=1;}
const small=normalizeReference({Width:160,Height:160,Length:160,Palette:{'minecraft:air':7,'minecraft:stone':130},BlockData:padded});
assert.equal(small.blocks.length,60);
assert.equal(small.selectionCells,160**3);
assert.equal(meshReference(small).quads.length,6);
assert.deepEqual(centerReference(small),[-30,-0,-.5]);
assert.deepEqual(centerReference({blocks:[[10,5,20,0],[13,7,25,0]]}),[-12,-5,-23]);
for(const slim of [false,true]){
    const parts=playerParts(slim),right=parts.find(p=>p[0]==='right_arm'),left=parts.find(p=>p[0]==='left_arm');
    assert.ok(right[1][0]>0&&left[2][0]<0);assert.deepEqual(right[4],[40,16]);assert.deepEqual(left[4],[32,48]);
    assert.equal(right[2][0]-right[1][0],slim?3:4);
}

// Dense terrain used to fail before it could be simplified to six quads.
const dense=normalizeReference({Width:65,Height:65,Length:65,Palette:{'minecraft:stone':0},BlockData:new Uint8Array(65**3)});
assert.equal(dense.blocks.length,274625);
assert.equal(meshReference(dense).quads.length,6);

// Repeated positions and empty entries do not inflate the unique solid count.
const duplicate=normalizeReference({format:'asterion_reference',size:[60,1,1],palette:['minecraft:stone'],blocks:Array.from({length:260000},(_,i)=>[i%60,0,0,0])});
assert.equal(duplicate.blocks.length,60);
assert.throws(()=>normalizeReference({Width:1,Height:1,Length:1,Palette:{'minecraft:stone':0},BlockData:new Uint8Array([0,0])}),/trailing/);
console.log('PASS: 60-block padded selection, multibyte palette IDs, dense terrain optimization, duplicate counting and malformed data.');

if(process.argv[2]){
    const start=performance.now();let bytes=fs.readFileSync(process.argv[2]);
    if(bytes[0]===31&&bytes[1]===139)bytes=zlib.gunzipSync(bytes);
    const ref=normalizeReference(readNBT(bytes)),mesh=meshReference(ref);
    console.log(JSON.stringify({size:ref.size,selectionCells:ref.selectionCells,nonAirBlocks:ref.blocks.length,exposedFaces:mesh.exposedFaces,optimizedQuads:mesh.quads.length,seconds:Number(((performance.now()-start)/1000).toFixed(2))}));
}
