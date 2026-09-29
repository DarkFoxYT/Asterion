"""Import the authored gate .bp as a vanilla structure (Python standard library only)."""
import gzip, io, struct, json
from pathlib import Path

def read_nbt(f):
    def num(fmt): return struct.unpack('>'+fmt, f.read(struct.calcsize('>'+fmt)))[0]
    def string(): return f.read(num('H')).decode('utf-8')
    def payload(t):
        if t in (1,2,3,4,5,6): return num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t == 7: return f.read(num('i'))
        if t == 8: return string()
        if t == 9:
            element, count = num('B'), num('i')
            return [payload(element) for _ in range(count)]
        if t == 10:
            result = {}
            while (tag := num('B')):
                name = string(); result[name] = payload(tag)
            return result
        if t in (11,12): return [num('i' if t == 11 else 'q') for _ in range(num('i'))]
        raise ValueError(t)
    t = num('B'); string(); return payload(t)

def blueprint(path):
    with open(path, 'rb') as f:
        magic=f.read(4)
        meta=read_nbt(io.BytesIO(f.read(struct.unpack('>i',f.read(4))[0])))
        thumbnail=f.read(struct.unpack('>i',f.read(4))[0])
        f.read(4)
        data=read_nbt(io.BytesIO(gzip.decompress(f.read())))
    return meta,thumbnail,data

def blocks(data):
    result=[]
    for section in data['BlockRegion']:
        states=section['BlockStates']; palette=states['palette']
        bits=max(4,(len(palette)-1).bit_length()); per=64//bits
        packed=states.get('data',[])
        for i in range(4096):
            index=((packed[i//per] & ((1<<64)-1)) >> ((i%per)*bits)) & ((1<<bits)-1) if len(palette)>1 else 0
            state=palette[index]
            if state['Name']=='minecraft:void_air': continue
            pos=[section['X']*16+(i&15),section['Y']*16+(i>>8),section['Z']*16+((i>>4)&15)]
            result.append((pos,state))
    return result


def encode_string(value):
    b=value.encode('utf-8'); return struct.pack('>H',len(b))+b

def tag(kind,name,payload):
    return bytes([kind])+encode_string(name)+payload

def integer(value): return struct.pack('>i',value)
def int_list(values): return bytes([3])+integer(len(values))+b''.join(integer(v) for v in values)

def convert(source, output):
    meta, thumbnail, data=blueprint(source)
    if data.get('BlockEntities') or data.get('Entities'):
        raise ValueError('Gate has entities; preserve their NBT before importing this revision')
    entries=blocks(data)
    if len(entries)!=meta['BlockCount']: raise ValueError('Incomplete blueprint decoding')
    lo=[min(p[a] for p,s in entries) for a in range(3)]
    hi=[max(p[a] for p,s in entries) for a in range(3)]
    size=[hi[a]-lo[a]+1 for a in range(3)]
    if size != [34,173,146]: raise ValueError(f'Unexpected gate dimensions: {size}')
    palette=[]; indices={}; encoded_blocks=[]
    for pos,state in entries:
        key=json.dumps(state,sort_keys=True)
        if key not in indices:
            indices[key]=len(palette)
            encoded=tag(8,'Name',encode_string(state['Name']))
            if state.get('Properties'):
                properties=b''.join(tag(8,k,encode_string(v)) for k,v in state['Properties'].items())+bytes([0])
                encoded+=tag(10,'Properties',properties)
            palette.append(encoded+bytes([0]))
        encoded_blocks.append(tag(9,'pos',int_list([pos[a]-lo[a] for a in range(3)]))
                              +tag(3,'state',integer(indices[key]))+bytes([0]))
    def compounds(values): return bytes([10])+integer(len(values))+b''.join(values)
    payload=(tag(3,'DataVersion',integer(data['DataVersion']))+tag(9,'size',int_list(size))
             +tag(9,'palette',compounds(palette))+tag(9,'blocks',compounds(encoded_blocks))
             +tag(9,'entities',compounds([]))+bytes([0]))
    output=Path(output); output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes(gzip.compress(tag(10,'',payload),mtime=0))
    # Read the result back, including every position and state.
    verified=read_nbt(io.BytesIO(gzip.decompress(output.read_bytes())))
    assert verified['size']==size and len(verified['blocks'])==len(entries)
    for (pos,state),block in zip(entries,verified['blocks']):
        assert block['pos']==[pos[a]-lo[a] for a in range(3)]
        assert verified['palette'][block['state']]==state
    print(f'Imported and verified {len(entries)} blocks; size {size}; {len(palette)} states')

if __name__=='__main__':
    import sys
    root=Path(__file__).resolve().parents[1]
    convert(Path(sys.argv[1]) if len(sys.argv)>1 else root/'docs/underworld/source/gate.bp',
            Path(sys.argv[2]) if len(sys.argv)>2 else root/'src/main/resources/data/asterion/structure/limbo_gate.nbt')
