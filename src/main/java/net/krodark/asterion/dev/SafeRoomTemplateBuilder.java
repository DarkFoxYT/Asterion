package net.krodark.asterion.dev;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SafeRoomTemplateBuilder {
    private static final int SIZE = 15;
    private static final ListTag BLOCKS = new ListTag();
    private SafeRoomTemplateBuilder() { }

    public static void main(String[] args) throws Exception {
        BLOCKS.clear();
        CompoundTag root = new CompoundTag();
        SharedConstants.tryDetectVersion();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.put("size", ints(SIZE, 9, SIZE));
        root.put("palette", palette());
        root.put("entities", new ListTag());
        for (int x=0;x<SIZE;x++) for(int z=0;z<SIZE;z++) add(x,0,z,(x+z)%7==0?1:0);
        for (int y=1;y<=7;y++) for(int x=0;x<SIZE;x++) for(int z=0;z<SIZE;z++) {
            boolean edge=x<2||z<2||x>=SIZE-2||z>=SIZE-2;
            boolean arch=(Math.abs(x-7)<=1&&(z<2||z>=SIZE-2)
                    ||Math.abs(z-7)<=1&&(x<2||x>=SIZE-2))&&y<=4;
            boolean rune=(x==4||x==7||x==10)&&z==2&&y==3;
            if(edge&&arch) add(x,y,z,7);
            else if(edge) add(x,y,z,(x*3+z*5+y)%9==0?1:0);
            else if(rune) add(x,y,z, x == 4 ? 8 : x == 7 ? 10 : 11);
            else add(x,y,z,7);
        }
        for(int x=0;x<SIZE;x++) for(int z=0;z<SIZE;z++)
            if((x+z)%4!=0 || x<2 || z<2 || x>=SIZE-2 || z>=SIZE-2) add(x,8,z,(x*z)%11==0?2:0);
        add(7,1,7,3); add(7,2,7,4);
        addBarrel(4,1,7); addBarrel(10,1,7);
        for(int[] p:new int[][]{{2,1,2},{12,1,2},{2,1,12},{12,1,12}})
            for(int y=1;y<=5;y++) add(p[0],y,p[2],5);
        root.put("blocks",BLOCKS);
        Path out=Path.of(args[0]); Files.createDirectories(out.getParent()); NbtIo.writeCompressed(root,out);
    }
    private static ListTag palette(){ListTag p=new ListTag();p.add(state("asterion:ancient_bricks"));p.add(state("asterion:ancient_stone"));p.add(state("minecraft:cracked_deepslate_bricks"));p.add(state("minecraft:lodestone"));p.add(state("minecraft:soul_lantern"));p.add(state("minecraft:crying_obsidian"));p.add(state("minecraft:barrel"));p.add(state("minecraft:air"));p.add(state("minecraft:air"));p.add(state("asterion:rune_zone_door","open","true"));p.add(state("minecraft:air"));p.add(state("minecraft:air"));return p;}
    private static CompoundTag state(String n){CompoundTag t=new CompoundTag();t.putString("Name",n);return t;}
    private static CompoundTag state(String n,String property,String value){CompoundTag t=state(n);CompoundTag props=new CompoundTag();props.putString(property,value);t.put("Properties",props);return t;}
    private static void add(int x,int y,int z,int s){CompoundTag b=new CompoundTag();b.put("pos",ints(x,y,z));b.putInt("state",s);BLOCKS.add(b);}
    private static void addBarrel(int x,int y,int z){CompoundTag b=new CompoundTag();b.put("pos",ints(x,y,z));b.putInt("state",6);CompoundTag n=new CompoundTag();n.putString("id","minecraft:barrel");n.putString("LootTable","asterion:chests/safe_rune_mid");b.put("nbt",n);BLOCKS.add(b);}
    private static ListTag ints(int...v){ListTag l=new ListTag();for(int i:v)l.add(IntTag.valueOf(i));return l;}
}
