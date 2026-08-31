package net.krodark.asterion.dev;

import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.ArrayList;

/** Imports copies only; never edits the author's saved world or source structures. */
public final class ImportAuthoredCatacombs {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), destination = Path.of(args[1]);
        var names = new ArrayList<>(AuthoredCatacombs.TEMPLATES);
        for (int i = 1; i <= 9; i++) names.add("arena_part" + i);
        Files.createDirectories(destination);
        for (String name : names) {
            CompoundTag root = NbtIo.readCompressed(source.resolve(name + ".nbt"), NbtAccounter.unlimitedHeap());
            ListTag palette = root.getListOrEmpty("palette");
            CompoundTag reward = null;
            int rewardScore = -1;
            if (name.equals("puzzleroom")) for (var value : root.getListOrEmpty("blocks")) {
                CompoundTag block = (CompoundTag)value;
                CompoundTag state = (CompoundTag)palette.get(block.getIntOr("state", -1));
                if (!state.getStringOr("Name", "").equals("minecraft:chest")) continue;
                ListTag pos = block.getListOrEmpty("pos");
                int score = ((IntTag)pos.get(2)).intValue()*1024 + ((IntTag)pos.get(1)).intValue()*32 + ((IntTag)pos.get(0)).intValue();
                if (score > rewardScore) { rewardScore = score; reward = block; }
            }
            for (var value : root.getListOrEmpty("blocks")) {
                CompoundTag block = (CompoundTag)value;
                CompoundTag state = (CompoundTag)palette.get(block.getIntOr("state", -1));
                String type = state.getStringOr("Name", "");
                if (type.equals("minecraft:chest") || type.equals("minecraft:trapped_chest") || type.equals("minecraft:barrel")) {
                    CompoundTag data = block.getCompoundOrEmpty("nbt");
                    // Preserve intentionally filled containers (e.g. comparator puzzle inventories).
                    if (data.getListOrEmpty("Items").isEmpty()) {
                        data.remove("Items");
                        data.remove("LootTableSeed"); // Vanilla rolls once on first access, independently for each container.
                        data.putString("id", type.equals("minecraft:barrel") ? "minecraft:barrel" : "minecraft:chest");
                        data.putString("LootTable", "asterion:chests/" + (block == reward ? "catacomb_puzzle_reward"
                                : name.equals("puzzleroom") ? "catacomb_puzzle_supplies" : "catacomb_cache"));
                        block.put("nbt", data);
                    }
                }
                if (!state.getStringOr("Name", "").equals("minecraft:jigsaw")) continue;
                CompoundTag data = block.getCompoundOrEmpty("nbt");
                data.putString("name", "asterion:catacombs/door");
                data.putString("target", "asterion:catacombs/door");
                data.putString("joint", "aligned");
                data.putString("pool", name.startsWith("corridor_deadend") || name.equals("puzzleroom")
                        ? "minecraft:empty" : "asterion:catacombs/modules");
                block.put("nbt", data);
            }
            NbtIo.writeCompressed(root, destination.resolve(name + ".nbt"));
            System.out.println("Imported " + name);
        }
    }
}
