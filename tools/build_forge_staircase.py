"""Build the compact Forge stair module, reusing the authored catacomb landing."""
from pathlib import Path
from copy import deepcopy
import nbtlib as n
root = Path(__file__).resolve().parents[1] / 'src/main/resources/data/asterion/structure'
source = n.load(root / 'catacombs/corridor_cross_01.nbt')
palette = list(deepcopy(source['palette']))
def state(name, **properties):
    value = n.Compound({'Name':n.String(name)})
    if properties: value['Properties'] = n.Compound({k:n.String(v) for k,v in properties.items()})
    if value not in palette: palette.append(value)
    return palette.index(value)
brick=state('asterion:mazesteel_bricks'); air=state('minecraft:air'); floor=state('asterion:polished_mazesteel')
blocks={}
def put(x,y,z,s,tag=None):
    value=n.Compound({'pos':n.List[n.Int]([x,y,z]),'state':n.Int(s)})
    if tag is not None: value['nbt']=tag
    blocks[x,y,z]=value
# The lower half occupies only the space west of the Forge's real socket.
for x in range(10):
 for y in range(39):
  for z in range(19): put(x,y,z,brick)
for block in source['blocks']:
 x,y,z=map(int,block['pos']); put(x,y+39,z,int(block['state']),deepcopy(block.get('nbt')))
# A compact rising loop, with full landings at its corners.
path=[(7,9)]
heights=[1]
while heights[-4:] != [44]*4:
 for target in [(7,3),(2,3),(2,15),(7,15),(7,9)]:
  while path[-1]!=target and heights[-4:] != [44]*4:
   x,z=path[-1]; tx,tz=target
   nx,nz=x+(tx>x)-(tx<x),z+(tz>z)-(tz<z)
   # Three level steps around each turn leave a full-width landing.
   near_corner=any(abs(nx-cx)+abs(nz-cz)<=1 for cx,cz in [(7,3),(2,3),(2,15),(7,15)])
   heights.append(min(44,heights[-1]+(0 if near_corner else 1)))
   path.append((nx,nz))
plan={}
def cell(x,y,z,s,priority):
 if (x,y,z) not in plan or priority>=plan[x,y,z][1]: plan[x,y,z]=(s,priority)
def walk(x,z,feet,dx,dz,stair=False):
 for side in range(-1,2):
  px,pz=x+dz*side,z-dx*side
  face='east' if dx>0 else 'west' if dx<0 else 'south' if dz>0 else 'north'
  s=state('asterion:polished_mazesteel_stairs',facing=face,half='bottom',shape='straight',waterlogged='false') if stair else floor
  cell(px,feet-1,pz,s,3)
  for y in range(feet,feet+4): cell(px,y,pz,air,2)
for i,(x,z) in enumerate(path):
 nx,nz=path[min(i+1,len(path)-1)]
 dx,dz=nx-x,nz-z
 if not dx and not dz: dx=1
 corner=i>0 and (x-path[i-1][0],z-path[i-1][1])!=(dx,dz)
 walk(x,z,heights[i],dx,dz,i>0 and i<len(path)-1 and heights[i]>heights[i-1] and not corner)
# Three-block bottom landing; the upper landing retains the authored cross-room ports.
for x in range(4,10): walk(x,9,1,1,0)
x,z=path[-1]
for px in range(x,10): walk(px,z,44,1,0)
for pz in range(z,10): walk(9,pz,44,0,1)
for (x,y,z),(s,_) in plan.items(): put(x,y,z,s)
# The saved socket faces the Forge's west socket directly, without a bridging hallway.
jigsaw=state('minecraft:jigsaw',orientation='east_up')
put(9,1,9,jigsaw,n.Compound({k:n.String(v) for k,v in {
 'name':'asterion:catacombs/door','target':'asterion:catacombs/door','pool':'minecraft:empty',
 'final_state':'minecraft:air','joint':'rollable'}.items()}))
out=n.File({'DataVersion':source['DataVersion'],'size':n.List[n.Int]([19,70,19]),
 'palette':n.List[n.Compound](palette),'blocks':n.List[n.Compound](blocks.values()),'entities':n.List[n.Compound]([])})
out.save(root / 'forge/staircase.nbt',gzipped=True)
print('Saved compact staircase:',len(blocks),'blocks; upper path end',path[-1])
