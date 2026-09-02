package net.krodark.asterion.dev.verification;

import net.krodark.asterion.worldgen.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import java.nio.file.Path;
import java.util.*;

/** Contracts for the supplied full-size templates and their infinite rooted layout. */
public final class CatacombRegression {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Set<String> selected = new HashSet<>();
        int total=0, occupied=0, puzzles=0, deadends=0;
        for (long seed : new long[]{0,-1,894237,Long.MIN_VALUE,Long.MAX_VALUE}) {
            for (int x=-24; x<=24; x++) for(int z=-24; z<=24; z++) {
                if (CatacombLayout.reserved(x,z)) continue;
                total++;
                if (!CatacombLayout.occupied(seed,x,z)) {
                    for(Direction side:Direction.Plane.HORIZONTAL) require(!CatacombLayout.connected(seed,x,z,side),
                            "Connection into solid rock at " + x + "," + z + " toward " + side + " seed " + seed);
                    continue;
                }
                occupied++;
                var module = AuthoredCatacombs.module(seed,x,z);
                selected.add(module.name());
                if(Integer.bitCount(module.exits())==1)deadends++;
                if(module.name().equals("puzzleroom"))puzzles++;
                require((module.exits() & module.blocked()) == 0,"An active connection was capped");
                for (Direction side : Direction.Plane.HORIZONTAL)
                    require(CatacombLayout.connected(seed,x,z,side)==CatacombLayout.connected(seed,x+side.getStepX(),z+side.getStepZ(),side.getOpposite()),"Mismatched seam");
                int px=x,pz=z,steps=0;
                while(px!=CatacombLayout.ROOT_X || pz!=CatacombLayout.ROOT_Z) {
                    var parent=CatacombLayout.parent(seed,px,pz);
                    require(parent!=null && ++steps<512,"Cycle or disconnected cell from " + x + "," + z
                            + " at " + px + "," + pz + " seed " + seed);
                    px+=parent.getStepX(); pz+=parent.getStepZ();
                    require(CatacombLayout.occupied(seed,px,pz),"Path crosses arena or uncarved rock");
                }
            }
        }
        require(occupied>total*.30 && occupied<total*.50,"Layout too dense or too sparse: "+occupied+"/"+total);
        require(puzzles>0 && puzzles<deadends*.06,"Puzzle rooms are not rare: "+puzzles+"/"+deadends);
        require(AuthoredCatacombs.BRAZIER_ROOM_ORIGINS.size()==3,
                "Expected three authored Cursed Brazier chambers");
        for(long seed:new long[]{0,-1,894237,Long.MIN_VALUE,Long.MAX_VALUE}) {
            var crossing=AuthoredCatacombs.module(seed,CatacombLayout.BRAZIER_APPROACH_CROSSING_X,
                    CatacombLayout.BRAZIER_APPROACH_Z);
            require(crossing.name().startsWith("crossing_"),"Boss approach crossing was not guaranteed");
            require((crossing.exits()&2)!=0&&(crossing.exits()&8)!=0,
                    "Boss approach crossing does not connect root to boss room");
            for (int minZ : CatacombLayout.BRAZIER_ROOM_MIN_ZS) {
                int hallZ = minZ + 1;
                for (int x = CatacombLayout.ROOT_X; x < CatacombLayout.BRAZIER_ROOM_MIN_X; x++) {
                    require(CatacombLayout.occupied(seed, x, hallZ),
                            "Missing straight boss hall at " + x + "," + hallZ);
                    require(CatacombLayout.connected(seed, x, hallZ, Direction.EAST),
                            "Broken east/west boss hall at " + x + "," + hallZ);
                }
            }
        }
        System.out.println("Layout sample: "+occupied+" occupied / "+total+" cells; "+puzzles+" puzzles / "+deadends+" dead ends");
        require(selected.containsAll(AuthoredCatacombs.TEMPLATES),"Unreachable variants: "+AuthoredCatacombs.TEMPLATES.stream().filter(n->!selected.contains(n)).toList());
        for(String name:AuthoredCatacombs.TEMPLATES) template(name,19,31,19);
        for(int part=1;part<=9;part++) template("arena_part"+part,41,48,41);
        for(int part=1;part<=4;part++) brazierTemplate(part);
        require(AuthoredCatacombs.ARENA_BASE_Y+23==AuthoredCatacombs.CONNECTOR_Y,"Arena exit elevation differs from crypts");
        require(!CatacombLayout.contains(new net.minecraft.core.BlockPos(0,7,0)),"Arena counted as catacombs");
        System.out.println("Authored catacomb regression: "+checks+" checks passed; all 28 assets validated");
    }
    private static void brazierTemplate(int part) throws Exception {
        String name="cursed_brazier_room_part"+part;
        CompoundTag root=NbtIo.readCompressed(Path.of(
                "src/main/resources/data/asterion/structure/catacombs",name+".nbt"),NbtAccounter.unlimitedHeap());
        var size=root.getListOrEmpty("size");
        require(integer(size,0)==25&&integer(size,1)==31&&integer(size,2)==25,"Invalid dimensions: "+name);
        var palette=root.getListOrEmpty("palette");
        int wool=0,doors=0;
        for(var entry:root.getListOrEmpty("blocks")) {
            var block=(CompoundTag)entry;
            var state=(CompoundTag)palette.get(block.getIntOr("state",-1));
            String blockName=state.getStringOr("Name","");
            if(blockName.equals("minecraft:red_wool"))wool++;
            if(blockName.equals("asterion:cursed_brazier_door"))doors++;
        }
        require(wool==(part==4?1:0),"Boss marker must exist only in room part 4: "+name);
        require(doors>0,"Missing encounter door: "+name);
    }
    private static void template(String name,int sx,int sy,int sz) throws Exception {
        CompoundTag root=NbtIo.readCompressed(Path.of("src/main/resources/data/asterion/structure/catacombs",name+".nbt"),NbtAccounter.unlimitedHeap());
        var size=root.getListOrEmpty("size");
        require(integer(size,0)==sx && integer(size,1)==sy && integer(size,2)==sz,"Invalid dimensions: "+name);
        var palette=root.getListOrEmpty("palette");
        Set<Integer> positions=new HashSet<>(); int connectors=0, rewards=0;
        for(var entry:root.getListOrEmpty("blocks")) {
            var block=(CompoundTag)entry; var pos=block.getListOrEmpty("pos");
            int x=integer(pos,0),y=integer(pos,1),z=integer(pos,2);
            require(x>=0&&x<sx&&y>=0&&y<sy&&z>=0&&z<sz,"Out of bounds: "+name);
            require(positions.add(x+sx*(z+sz*y)),"Duplicate coordinate: "+name);
            var state=(CompoundTag)palette.get(block.getIntOr("state",-1));
            String blockName=state.getStringOr("Name","");
            if(blockName.equals("minecraft:chest")||blockName.equals("minecraft:barrel")||blockName.equals("minecraft:trapped_chest")) {
                var data=block.getCompoundOrEmpty("nbt");
                if(data.getListOrEmpty("Items").isEmpty()) {
                    String loot=data.getStringOr("LootTable","");
                    if(name.startsWith("arena_part") && loot.isEmpty()) continue;
                    require(loot.startsWith("asterion:chests/catacomb_"),"Missing container loot: "+name);
                    require(!data.contains("LootTableSeed"),"Fixed repeated loot seed: "+name);
                    if(loot.equals("asterion:chests/catacomb_puzzle_reward"))rewards++;
                    else require(loot.equals("asterion:chests/"+(name.equals("puzzleroom")?"catacomb_puzzle_supplies":"catacomb_cache")),"Wrong loot tier: "+name);
                }
            }
            if(state.getStringOr("Name","").equals("minecraft:jigsaw")) {
                connectors++;
                var data=block.getCompoundOrEmpty("nbt");
                require(data.getStringOr("name","").equals("asterion:catacombs/door"),"Unconfigured connector: "+name);
                require(data.getStringOr("target","").equals("asterion:catacombs/door"),"Unconfigured target: "+name);
                require(data.getStringOr("final_state","").equals("minecraft:air"),"Unexpected connector replacement");
                // Arena parts are installed at fixed world coordinates and never pass through
                // jigsaw rotation; procedural 19x19 crypt modules must remain aligned.
                if(!name.startsWith("arena_part"))
                    require(data.getStringOr("joint","").equals("aligned"),"Rollable module: "+name);
                require(y==(name.startsWith("arena_part")?23:5),"Misaligned doorway");
            }
        }
        require(positions.size()==sx*sy*sz,"Missing explicit blocks/air: "+name);
        int expected=name.startsWith("arena_part")?(name.equals("arena_part8")?1:0):name.startsWith("corridor_cross")||name.startsWith("crossing")?4:name.startsWith("corridor_t")?3:name.startsWith("corridor_deadend")||name.equals("puzzleroom")?1:2;
        require(connectors==expected,"Wrong connector count: "+name);
        require(rewards==(name.equals("puzzleroom")?1:0),"Expected one main puzzle reward and none elsewhere: "+name);
    }
    private static int integer(ListTag list,int i){return ((IntTag)list.get(i)).intValue();}
    private static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
}
