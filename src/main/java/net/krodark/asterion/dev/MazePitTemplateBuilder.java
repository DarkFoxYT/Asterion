package net.krodark.asterion.dev;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import java.nio.file.*;

/** Replaceable surface-aligned jigsaw example; the upper connector defines the maze floor. */
public final class MazePitTemplateBuilder {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        CompoundTag root = new CompoundTag(); ListTag palette = new ListTag(), blocks = new ListTag();
        for (String id : new String[]{"asterion:ancient_bricks", "minecraft:air", "minecraft:water", "asterion:mazesteel_chain", "asterion:mazesteel_block", "minecraft:jigsaw"}) {
            CompoundTag state = new CompoundTag(); state.putString("Name", id);
            if (id.endsWith("jigsaw")) { CompoundTag props = new CompoundTag(); props.putString("orientation", "up_north"); state.put("Properties", props); }
            palette.add(state);
        }
        for (int x = 0; x < 13; x++) for (int z = 0; z < 13; z++) for (int y = 0; y < 25; y++) {
            boolean edge = x == 0 || z == 0 || x == 12 || z == 12;
            int state = edge || y == 0 ? 0 : y == 1 ? 2 : 1;
            if (x == 1 && z == 6 && y >= 2) state = 3;
            if (y == 23 && x >= 5 && x <= 7 && (z == 2 || z == 5 || z == 8 || z == 11)) state = 4;
            if (x == 6 && z == 6 && y == 24) state = 5;
            CompoundTag block = new CompoundTag(); block.put("pos", ints(x, y, z)); block.putInt("state", state);
            if (state == 5) {
                CompoundTag nbt = new CompoundTag(); nbt.putString("id", "minecraft:jigsaw");
                nbt.putString("name", "asterion:maze_pits/surface"); nbt.putString("target", "minecraft:empty");
                nbt.putString("pool", "minecraft:empty"); nbt.putString("joint", "aligned"); nbt.putString("final_state", "minecraft:air");
                block.put("nbt", nbt);
            }
            blocks.add(block);
        }
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version()); root.put("size", ints(13, 25, 13));
        root.put("palette", palette); root.put("blocks", blocks); root.put("entities", new ListTag());
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent()); NbtIo.writeCompressed(root, path);
    }
    private static ListTag ints(int... values) { var list = new ListTag(); for (int value : values) list.add(IntTag.valueOf(value)); return list; }
}
