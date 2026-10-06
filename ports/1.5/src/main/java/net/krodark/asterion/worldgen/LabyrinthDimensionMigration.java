package net.krodark.asterion.worldgen;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** One-time migration before world metadata and player dimensions are decoded. */
public final class LabyrinthDimensionMigration {
    private static final String OLD = "asterion:asterion_dimension", NEW = "asterion:labyrinth";
    private LabyrinthDimensionMigration() {}
    public static void migrate(Path directory) {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("level.dat"))) return;
        Path marker = root.resolve(".asterion-labyrinth-migrated");
        if (Files.exists(marker)) return;
        Path namespace = root.resolve("dimensions/asterion").normalize();
        Path old = namespace.resolve("asterion_dimension"), replacement = namespace.resolve("labyrinth");
        try {
            if (Files.exists(old) && Files.exists(replacement))
                throw new IOException("Both legacy and renamed Labyrinth directories exist; refusing to overwrite terrain");
            List<Path> files = new ArrayList<>();
            files.add(root.resolve("level.dat"));
            if (Files.isRegularFile(root.resolve("level.dat_old"))) files.add(root.resolve("level.dat_old"));
            Path players = root.resolve("playerdata");
            if (Files.isDirectory(players)) try (var stream = Files.list(players)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".dat")).forEach(files::add);
            }
            for (Path file : files) {
                CompoundTag tag = readCompressed(file);
                if (!rewrite(tag)) continue;
                Path backup = file.resolveSibling(file.getFileName() + ".asterion-dimension-backup");
                if (!Files.exists(backup)) Files.copy(file, backup);
                Path temporary = file.resolveSibling(file.getFileName() + ".asterion-migrating");
                try (var output = Files.newOutputStream(temporary)) { NbtIo.writeCompressed(tag, output); }
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(old)) Files.move(old, replacement);
            Files.writeString(marker, NEW);
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot safely migrate the Labyrinth dimension", error);
        }
    }
    public static CompoundTag readCompressed(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
                    //? if >=1.20.5 {
            return NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
                    //?} else {
                    /*return NbtIo.readCompressed(input);*/
                    //?}
                }
    }

    static boolean rewrite(Tag tag) {
        boolean changed = false;
        if (tag instanceof CompoundTag compound) {
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                Tag value = compound.get(key);
                if (value.equals(StringTag.valueOf(OLD))) {
                    value = StringTag.valueOf(NEW); compound.put(key, value); changed = true;
                } else changed |= rewrite(value);
                if (key.equals(OLD)) { compound.remove(key); compound.put(NEW, value); changed = true; }
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                Tag value = list.get(i);
                if (value.equals(StringTag.valueOf(OLD))) { list.set(i, StringTag.valueOf(NEW)); changed = true; }
                else changed |= rewrite(value);
            }
        }
        return changed;
    }
}
