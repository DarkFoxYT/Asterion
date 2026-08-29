package net.krodark.asterion.dev;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;

/** Builds an NBT structure-template version of Minecraft's classic monster room. */
public final class VanillaDungeonTemplateBuilder {
    private static final int SIZE = 9;
    private static final ListTag BLOCKS = new ListTag();

    private VanillaDungeonTemplateBuilder() { }

    public static void main(String[] args) throws Exception {
        BLOCKS.clear();
        SharedConstants.tryDetectVersion();
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.put("size", ints(SIZE, 6, SIZE));
        root.put("palette", palette());
        root.put("entities", new ListTag());

        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++)
            add(x, 0, z, Math.floorMod(x * 11 + z * 7, 5) < 2 ? 1 : 0);

        for (int y = 1; y <= 4; y++) for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            boolean furnishing = y == 1 && ((x == 4 && z == 4)
                    || (x == 1 && z == 2) || (x == 7 && z == 6));
            if (furnishing) continue;
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            boolean doorway = y <= 3 && ((Math.abs(x - 4) <= 1 && (z == 0 || z == SIZE - 1))
                    || (Math.abs(z - 4) <= 1 && (x == 0 || x == SIZE - 1)));
            add(x, y, z, edge && !doorway ? 0 : 2);
        }
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) add(x, 5, z, 0);

        addSpawner(4, 1, 4);
        addChest(1, 1, 2, "south");
        addChest(7, 1, 6, "north");

        root.put("blocks", BLOCKS);
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        NbtIo.writeCompressed(root, output);
        System.out.println("Rebuilt " + output + " with " + BLOCKS.size() + " blocks");
    }

    private static ListTag palette() {
        ListTag palette = new ListTag();
        palette.add(state("minecraft:cobblestone"));
        palette.add(state("minecraft:mossy_cobblestone"));
        palette.add(state("minecraft:air"));
        palette.add(state("minecraft:spawner"));
        palette.add(state("minecraft:chest", "facing", "south"));
        palette.add(state("minecraft:chest", "facing", "north"));
        return palette;
    }

    private static void addSpawner(int x, int y, int z) {
        CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:mob_spawner");
        data.putShort("Delay", (short) 20);
        data.putShort("MinSpawnDelay", (short) 200);
        data.putShort("MaxSpawnDelay", (short) 800);
        data.putShort("SpawnCount", (short) 4);
        data.putShort("MaxNearbyEntities", (short) 6);
        data.putShort("RequiredPlayerRange", (short) 16);
        data.putShort("SpawnRange", (short) 4);
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:zombie");
        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        data.put("SpawnData", spawnData);
        addWithNbt(x, y, z, 3, data);
    }

    private static void addChest(int x, int y, int z, String facing) {
        CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:chest");
        data.putString("LootTable", "minecraft:chests/simple_dungeon");
        addWithNbt(x, y, z, facing.equals("south") ? 4 : 5, data);
    }

    private static CompoundTag state(String name) {
        CompoundTag state = new CompoundTag();
        state.putString("Name", name);
        return state;
    }

    private static CompoundTag state(String name, String property, String value) {
        CompoundTag state = state(name);
        CompoundTag properties = new CompoundTag();
        properties.putString(property, value);
        state.put("Properties", properties);
        return state;
    }

    private static void add(int x, int y, int z, int state) {
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(x, y, z));
        block.putInt("state", state);
        BLOCKS.add(block);
    }

    private static void addWithNbt(int x, int y, int z, int state, CompoundTag data) {
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(x, y, z));
        block.putInt("state", state);
        block.put("nbt", data);
        BLOCKS.add(block);
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) list.add(IntTag.valueOf(value));
        return list;
    }
}
