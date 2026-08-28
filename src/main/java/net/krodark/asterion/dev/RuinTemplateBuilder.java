package net.krodark.asterion.dev;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.IntTag;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RuinTemplateBuilder {
    private static final int SIZE = 11;
    private static final ListTag BLOCKS = new ListTag();

    private RuinTemplateBuilder() {
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        CompoundTag root = new CompoundTag();
        SharedConstants.tryDetectVersion();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.put("size", ints(SIZE, 7, SIZE));
        root.put("palette", palette());
        root.put("entities", new ListTag());

        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            int dx = x - 5;
            int dz = z - 5;
            int r2 = dx * dx + dz * dz;
            if (r2 <= 27 && !(r2 > 20 && ((x * 7 + z * 11) & 3) == 0)) add(x, 0, z, r2 % 5 == 0 ? 1 : 0);
        }

        for (int y = 1; y <= 5; y++) for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            int dx = x - 5;
            int dz = z - 5;
            double shell = dx * dx + dz * dz + (y - 1.5) * (y - 1.5) * 1.65;
            boolean doorway = z <= 1 && Math.abs(dx) <= 1 && y <= 3;
            if (!doorway && shell >= 20 && shell <= 31 && ((x + z * 3 + y * 5) % 13 != 0))
                add(x, y, z, ((x + z + y) & 3) == 0 ? 1 : 0);
        }

        for (int y = 1; y <= 5; y++) {
            add(2, y, 2, y == 3 ? 3 : 2);
            add(8, y, 2, y == 3 ? 3 : 2);
            add(2, y, 8, y == 3 ? 3 : 2);
            add(8, y, 8, y == 3 ? 3 : 2);
        }
        addBarrel(5, 1, 5, 4);
        add(5, 5, 5, 5);
        add(5, 6, 5, 3);

        root.put("blocks", BLOCKS);
        Files.createDirectories(output.getParent());
        NbtIo.writeCompressed(root, output);
        System.out.println("Rebuilt " + output + " with " + BLOCKS.size() + " blocks");
    }

    private static ListTag palette() {
        ListTag palette = new ListTag();
        palette.add(state("minecraft:mossy_stone_bricks"));
        palette.add(state("minecraft:cracked_stone_bricks"));
        palette.add(state("minecraft:chiseled_stone_bricks"));
        palette.add(state("minecraft:dark_prismarine"));
        palette.add(state("minecraft:barrel"));
        palette.add(state("minecraft:sea_lantern"));
        return palette;
    }

    private static CompoundTag state(String name) {
        CompoundTag state = new CompoundTag();
        state.putString("Name", name);
        return state;
    }

    private static void add(int x, int y, int z, int state) {
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(x, y, z));
        block.putInt("state", state);
        BLOCKS.add(block);
    }

    private static void addBarrel(int x, int y, int z, int state) {
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(x, y, z));
        block.putInt("state", state);
        CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:barrel");
        data.putString("LootTable", "asterion:chests/underwater_ruin");
        block.put("nbt", data);
        BLOCKS.add(block);
    }

    private static ListTag ints(int... values) {
        ListTag result = new ListTag();
        for (int value : values) result.add(IntTag.valueOf(value));
        return result;
    }
}
