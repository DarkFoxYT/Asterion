package net.krodark.asterion.dev.verification;

import net.krodark.asterion.worldgen.CatacombLayout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Checks the worldgen contracts most likely to strand players or break edited NBT rooms. */
public final class CatacombRegression {
    private static int checks;

    public static void main(String[] args) throws Exception {
        connectedFloodedGalleries();
        for (String name : new String[]{"crossing", "ossuary", "sluice", "parkour"}) template(name);
        System.out.println("Catacomb regression: " + checks + " checks passed");
    }

    private static void connectedFloodedGalleries() {
        Set<Long> passages = new HashSet<>();
        for (int x = -64; x < 64; x++) for (int z = -64; z < 64; z++) {
            if (CatacombLayout.passage(x, z)) passages.add(key(x, z));
            for (long seed : new long[]{0, -1, 894237, Long.MIN_VALUE, Long.MAX_VALUE}) {
                int depth = CatacombLayout.WATER_Y - CatacombLayout.floor(seed, x, z);
                require(depth == 1 || depth == 2, "Unsafe water depth at " + x + "," + z);
            }
        }
        Set<Long> reached = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(passages.iterator().next());
        while (!queue.isEmpty()) {
            long next = queue.removeFirst();
            if (!passages.contains(next) || !reached.add(next)) continue;
            int x = (int)(next >> 32), z = (int)next;
            queue.add(key(x - 1, z)); queue.add(key(x + 1, z));
            queue.add(key(x, z - 1)); queue.add(key(x, z + 1));
        }
        require(reached.equals(passages), "Disconnected galleries across tile/negative-coordinate seams");
    }

    private static void template(String name) throws Exception {
        Path path = Path.of("src/main/resources/data/asterion/structure/catacombs", name + ".nbt");
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        ListTag size = root.getListOrEmpty("size");
        require(integer(size, 0) == 9 && integer(size, 1) == 11 && integer(size, 2) == 9,
                name + ": footprint no longer fits the jigsaw reservation");
        ListTag palette = root.getListOrEmpty("palette");
        Map<Integer, CompoundTag> states = new HashMap<>();
        int connectors = 0;
        for (var value : root.getListOrEmpty("blocks")) {
            CompoundTag block = (CompoundTag)value;
            ListTag pos = block.getListOrEmpty("pos");
            int x = integer(pos, 0), y = integer(pos, 1), z = integer(pos, 2);
            require(x >= 0 && x < 9 && y >= 0 && y < 11 && z >= 0 && z < 9, name + ": out-of-bounds block");
            int index = block.getIntOr("state", -1);
            require(index >= 0 && index < palette.size(), name + ": invalid palette index");
            CompoundTag state = (CompoundTag)palette.get(index);
            require(states.put(x + z * 9 + y * 81, state) == null, name + ": overlapping block records");
            if (state.getStringOr("Name", "").equals("minecraft:jigsaw")) {
                connectors++;
                CompoundTag nbt = block.getCompoundOrEmpty("nbt");
                require(!nbt.getStringOr("pool", "").isBlank(), name + ": missing jigsaw pool");
                require(!nbt.getStringOr("final_state", "").isBlank(), name + ": unsanitized jigsaw");
            }
        }
        require(states.size() == 891, name + ": unfilled shell or missing explicit air");
        require(is(states, 0, 4, 2, "asterion:lamenter") && is(states, 8, 4, 2, "asterion:lamenter"),
                name + ": missing wall Lamenters");
        require(states.get(2 * 9 + 4 * 81).getCompoundOrEmpty("Properties").getStringOr("facing", "").equals("east")
                && states.get(8 + 2 * 9 + 4 * 81).getCompoundOrEmpty("Properties").getStringOr("facing", "").equals("west"),
                name + ": Lamenters must face into the gallery");
        require(connectors == (name.equals("crossing") ? 5 : 1), name + ": incompatible connector count");
        require(is(states, 2, 9, 2, "minecraft:water") && is(states, 2, 8, 2, "minecraft:dripstone_block")
                && is(states, 2, 7, 2, "minecraft:pointed_dripstone"), name + ": missing roof leak");
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) {
            String floor = states.get(x + z * 9).getStringOr("Name", "");
            require(floor.startsWith("asterion:ancient_") || floor.equals("minecraft:jigsaw"), name + ": leaking floor");
            require(states.get(x + z * 9 + 10 * 81).getStringOr("Name", "").startsWith("asterion:ancient_"), name + ": leaking reservoir lid");
        }
        if (name.equals("sluice")) {
            for (int x = 3; x <= 5; x++) {
                CompoundTag gate = states.get(x + 3 * 9 + 81);
                require(gate.getStringOr("Name", "").equals("asterion:mazesteel_gate"), "Missing lower gate panel");
                require(gate.getCompoundOrEmpty("Properties").getStringOr("waterlogged", "").equals("true"),
                        "Water will wash away a lower sluice panel");
            }
            for (int x : new int[]{2, 4, 6}) require(is(states, x, 3, 6, "minecraft:lever"), "Valve/controller offsets disagree");
            require(is(states, 4, 5, 3, "asterion:sluice_lock"), "Missing puzzle controller");
        }
    }

    private static boolean is(Map<Integer, CompoundTag> states, int x, int y, int z, String name) {
        return states.get(x + z * 9 + y * 81).getStringOr("Name", "").equals(name);
    }
    private static int integer(ListTag list, int index) { return ((IntTag)list.get(index)).intValue(); }
    private static long key(int x, int z) { return (long)x << 32 | Integer.toUnsignedLong(z); }
    private static void require(boolean success, String message) { checks++; if (!success) throw new AssertionError(message); }
}
