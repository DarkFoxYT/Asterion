package net.krodark.asterion.dev;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RuneGateTemplateBuilder {
    private static final ListTag BLOCKS = new ListTag();
    private RuneGateTemplateBuilder() { }

    public static void main(String[] args) throws Exception {
        BLOCKS.clear();
        SharedConstants.tryDetectVersion();
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.put("size", ints(13, 8, 5));
        root.put("palette", palette());
        root.put("entities", new ListTag());
        for (int x=0;x<13;x++) for(int z=0;z<5;z++) add(x,0,z,0);
        for (int x=0;x<13;x++) for(int y=1;y<8;y++) {
            boolean opening=x>=5&&x<=7&&y<=4;
            if (opening) add(x,y,2,3);
            else add(x,y,2,(x+y)%7==0?1:0);
        }
        add(2,2,1,4); add(3,2,1,5); add(4,2,1,6);
        addBarrel(10,1,1);
        root.put("blocks", BLOCKS);
        Path out=Path.of(args[0]); Files.createDirectories(out.getParent()); NbtIo.writeCompressed(root,out);
    }

    private static ListTag palette() {
        ListTag p=new ListTag();
        p.add(state("asterion:ancient_bricks")); p.add(state("asterion:ancient_stone"));
        p.add(state("minecraft:air")); p.add(state("asterion:rune_zone_door","open","true"));
        p.add(state("minecraft:air")); p.add(state("minecraft:air"));
        p.add(state("minecraft:air")); p.add(state("minecraft:barrel"));
        return p;
    }
    private static CompoundTag state(String n){CompoundTag t=new CompoundTag();t.putString("Name",n);return t;}
    private static CompoundTag state(String n,String property,String value){CompoundTag t=state(n);CompoundTag p=new CompoundTag();p.putString(property,value);t.put("Properties",p);return t;}
    private static void add(int x,int y,int z,int state){CompoundTag b=new CompoundTag();b.put("pos",ints(x,y,z));b.putInt("state",state);BLOCKS.add(b);}
    private static void addBarrel(int x,int y,int z){CompoundTag b=new CompoundTag();b.put("pos",ints(x,y,z));b.putInt("state",7);CompoundTag n=new CompoundTag();n.putString("id","minecraft:barrel");n.putString("LootTable","asterion:chests/safe_rune_mid");b.put("nbt",n);BLOCKS.add(b);}
    private static ListTag ints(int... values){ListTag list=new ListTag();for(int value:values)list.add(IntTag.valueOf(value));return list;}
}
