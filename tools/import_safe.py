from pathlib import Path
import io,struct,gzip,nbtlib as n
import sys
source=sys.argv[1] if len(sys.argv)>1 else 'C:/Users/darkf/Downloads/safehouse_3.bp'
output=sys.argv[2] if len(sys.argv)>2 else 'src/main/resources/data/asterion/structure/safe_room_3.nbt'
p=open(source,'rb');p.read(4);p.read(struct.unpack('>i',p.read(4))[0]);p.read(struct.unpack('>i',p.read(4))[0]);p.read(4);d=n.File.parse(io.BytesIO(gzip.decompress(p.read())))
blocks=[];palette=[];markers=[]
for section in d['BlockRegion']:
 states=section['BlockStates'];pal=states['palette'];bits=max(4,(len(pal)-1).bit_length());per=64//bits;data=states.get('data',[])
 for i in range(4096):
  j=((int(data[i//per]) & ((1<<64)-1)) >> ((i%per)*bits)) & ((1<<bits)-1) if len(pal)>1 else 0
  state=pal[j]
  if str(state['Name'])=='minecraft:void_air':continue
  pos=[int(section['X'])*16+(i&15),int(section['Y'])*16+(i>>8),int(section['Z'])*16+((i>>4)&15)]
  if str(state['Name'])=='minecraft:lodestone':markers.append(pos)
  if state not in palette:palette.append(state)
  blocks.append((pos,palette.index(state)))
lo=[min(p[a] for p,s in blocks) for a in range(3)];hi=[max(p[a] for p,s in blocks) for a in range(3)]
print('Blocks',len(blocks),'bounds',lo,hi,'red wool',markers)
if len(markers) != 1: print('Queen placement pending: save exactly one red wool marker in the blueprint.')
out=n.File({'DataVersion':d['DataVersion'],'size':n.List[n.Int]([hi[a]-lo[a]+1 for a in range(3)]),'palette':n.List[n.Compound](palette),'blocks':n.List[n.Compound]([n.Compound({'pos':n.List[n.Int]([p[a]-lo[a] for a in range(3)]),'state':n.Int(s)}) for p,s in blocks]),'entities':n.List[n.Compound]([])})
out.save(output,gzipped=True)
