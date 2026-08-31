package net.krodark.asterion.dev;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Editable 9x9 crypt blueprints. All four rotations fit the 32-block generation tile. */
public final class CatacombTemplateBuilder {
    private final ListTag palette = new ListTag();
    private final Map<String, Integer> states = new LinkedHashMap<>();
    private final Map<Integer, CompoundTag> blocks = new LinkedHashMap<>();
    private final ListTag entities = new ListTag();

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        for (String type : new String[]{"crossing", "ossuary", "sluice", "parkour", "cursed_brazier"}) {
            CatacombTemplateBuilder builder = new CatacombTemplateBuilder();
            builder.build(type);
            builder.write(directory.resolve(type + ".nbt"));
        }
    }

    private void build(String type) {
        boolean crossing = type.equals("crossing");
        boolean through = crossing || type.equals("ossuary");
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) for (int y = 0; y < 11; y++) {
            boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
            boolean door = y >= 1 && y <= 4 && ((Math.abs(x - 4) <= 1 && (z == 8 || z == 0 && through))
                    || crossing && Math.abs(z - 4) <= 1 && (x == 0 || x == 8));
            String state = y == 0 || y >= 8 || edge && !door
                    ? (Math.floorMod(x * 3 + y + z * 7, 5) < 2 ? "asterion:ancient_mossy_bricks" : "asterion:ancient_bricks")
                    : y == 1 ? "minecraft:water" : "minecraft:air";
            put(x, y, z, state);
        }
        // Heavy ceiling ribs, hanging lights, and a sealed reservoir feeding real dripstone.
        for (int x = 1; x <= 7; x++) put(x, 7, 4, "minecraft:polished_deepslate");
        put(4, 6, 4, "minecraft:soul_lantern", "hanging", "true");
        put(2, 8, 2, "minecraft:dripstone_block");
        put(2, 9, 2, "minecraft:water");
        put(2, 7, 2, "minecraft:pointed_dripstone", "vertical_direction", "down", "thickness", "tip");
        // Wall faces overlooking the wet galleries, away from walkways and puzzle controls.
        put(0, 4, 2, "asterion:lamenter", "facing", "east", "crying", "false", "active", "false");
        put(8, 4, 2, "asterion:lamenter", "facing", "west", "crying", "false", "active", "false");
        jigsaw(4, 1, 8, "south_up", "minecraft:empty", "asterion:catacombs/door", "minecraft:water");
        if (crossing) {
            jigsaw(4, 0, 4, "down_south", "minecraft:empty", "asterion:catacombs/origin", "asterion:ancient_stone");
            jigsaw(4, 1, 0, "north_up", "asterion:catacombs/wings", "asterion:catacombs/door", "minecraft:water");
            jigsaw(4, 1, 8, "south_up", "asterion:catacombs/wings", "asterion:catacombs/door", "minecraft:water");
            jigsaw(0, 1, 4, "west_up", "asterion:catacombs/passages", "asterion:catacombs/door", "minecraft:water");
            jigsaw(8, 1, 4, "east_up", "asterion:catacombs/passages", "asterion:catacombs/door", "minecraft:water");
            for (int x : new int[]{1, 7}) for (int z : new int[]{1, 7}) {
                for (int y = 1; y <= 6; y++) put(x, y, z, "minecraft:chiseled_deepslate");
            }
        } else if (type.equals("ossuary")) {
            for (int z : new int[]{2, 5}) for (int x : new int[]{1, 7}) {
                put(x, 1, z, "asterion:ancient_stone");
                put(x, 2, z, "minecraft:bone_block", "axis", "z");
                put(x, 2, z + 1, "minecraft:polished_deepslate_slab", "type", "bottom");
                put(x, 3, z, "minecraft:skeleton_skull", "rotation", "0");
            }
            // A manual door between dry stepping stones; both directions can open it.
            put(4, 1, 4, "asterion:ancient_stone");
            put(4, 2, 4, "minecraft:dark_oak_door", "half", "lower", "facing", "south", "hinge", "left");
            put(4, 3, 4, "minecraft:dark_oak_door", "half", "upper", "facing", "south", "hinge", "left");
        } else if (type.equals("sluice")) {
            for (int x = 1; x <= 7; x++) for (int y = 1; y <= 7; y++)
                put(x, y, 3, x >= 3 && x <= 5 && y <= 4 ? "asterion:mazesteel_gate" : "asterion:ancient_bricks",
                        x >= 3 && x <= 5 && y <= 4 ? new String[]{"face", "floor", "facing", "south", "open", "false", "waterlogged", y == 1 ? "true" : "false"} : new String[0]);
            put(4, 5, 3, "asterion:sluice_lock", "facing", "south", "lit", "false");
            for (int x : new int[]{2, 4, 6}) {
                put(x, 1, 6, "asterion:ancient_stone");
                put(x, 2, 6, "asterion:ancient_stone");
                put(x, 3, 6, "minecraft:lever", "face", "floor", "facing", "south", "powered", "false");
                put(x, 5, 6, x == 4 ? "minecraft:chiseled_deepslate" : "minecraft:sea_lantern");
            }
            put(4, 1, 1, "asterion:ancient_stone");
            barrel(4, 2, 1);
        } else if (type.equals("cursed_brazier")) {
            for (int x = 1; x <= 7; x++) for (int z = 1; z <= 7; z++) put(x, 1, z, "asterion:ancient_stone");
            put(0, 3, 5, "asterion:spewer", "facing", "east", "triggered", "false");
            put(8, 3, 5, "asterion:fire_burst_trap", "facing", "west", "triggered", "false");
            put(2, 2, 5, "asterion:bear_trap");
            barrel(4, 2, 1);
            CompoundTag entity = new CompoundTag(), data = new CompoundTag();
            ListTag pos = new ListTag(); pos.add(net.minecraft.nbt.DoubleTag.valueOf(4.5));
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(2)); pos.add(net.minecraft.nbt.DoubleTag.valueOf(4.5));
            entity.put("pos", pos); entity.put("blockPos", ints(4, 2, 4)); data.putString("id", "asterion:cursed_brazier");
            data.putBoolean("PersistenceRequired", true); entity.put("nbt", data); entities.add(entity);
        } else {
            int[][] steps = {{2, 1, 6}, {2, 2, 3}, {5, 3, 2}, {6, 4, 5}};
            for (int[] step : steps) for (int x = step[0]; x <= step[0] + 1; x++)
                put(x, step[1], step[2], "asterion:slick_catacomb_stone");
            put(6, 7, 5, "asterion:mazesteel_block");
            put(6, 6, 5, "asterion:mazesteel_chain", "axis", "y");
            barrel(7, 5, 5);
        }
    }

    private void barrel(int x, int y, int z) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:barrel");
        nbt.putString("LootTable", "asterion:chests/catacomb_cache");
        put(x, y, z, "minecraft:barrel", "facing", "up").put("nbt", nbt);
    }

    private void jigsaw(int x, int y, int z, String orientation, String pool, String name, String finalState) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:jigsaw");
        nbt.putString("name", name);
        nbt.putString("target", "asterion:catacombs/door");
        nbt.putString("pool", pool);
        nbt.putString("joint", "aligned");
        nbt.putString("final_state", finalState);
        nbt.putInt("selection_priority", 0);
        nbt.putInt("placement_priority", 0);
        put(x, y, z, "minecraft:jigsaw", "orientation", orientation).put("nbt", nbt);
    }

    private CompoundTag put(int x, int y, int z, String name, String... properties) {
        String key = name + String.join("/", properties);
        int index = states.computeIfAbsent(key, ignored -> {
            CompoundTag state = new CompoundTag();
            state.putString("Name", name);
            if (properties.length > 0) {
                CompoundTag props = new CompoundTag();
                for (int i = 0; i < properties.length; i += 2) props.putString(properties[i], properties[i + 1]);
                state.put("Properties", props);
            }
            palette.add(state);
            return palette.size() - 1;
        });
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(x, y, z));
        block.putInt("state", index);
        blocks.put(x + z * 9 + y * 81, block);
        return block;
    }

    private void write(Path path) throws Exception {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.put("size", ints(9, 11, 9));
        root.put("palette", palette);
        root.put("entities", entities);
        ListTag list = new ListTag();
        blocks.values().forEach(list::add);
        root.put("blocks", list);
        NbtIo.writeCompressed(root, path);
        System.out.println("Built " + path + " (" + blocks.size() + " blocks)");
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) list.add(IntTag.valueOf(value));
        return list;
    }
}
