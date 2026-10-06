package com.meekdev.amnetic.client.particle.editor;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/** Version-independent adapter for the renderer's local preset file operations. */
public final class SafePresetIO {
    private SafePresetIO() {}

    public static void validateName(String name) {
        if (!EffectPresetFiles.validName(name)) throw new IllegalArgumentException("Invalid local preset name");
    }

    private static EffectPresetFiles directory(Path directory) throws IOException {
        Path path = directory.toAbsolutePath().normalize();
        if (path.getParent() == null || path.getParent().getParent() == null
                || !path.getFileName().toString().equals("particles")
                || !path.getParent().getFileName().toString().equals("amnetic"))
            throw new IOException("Unexpected preset directory");
        EffectPresetFiles files = new EffectPresetFiles(path.getParent().getParent());
        files.directory();
        return files;
    }

    private static String name(Path path) throws IOException {
        String name = path.getFileName().toString();
        if (!name.endsWith(".json")) throw new IOException("Preset must be JSON");
        String stem = name.substring(0, name.length() - 5);
        if (!EffectPresetFiles.validName(stem)) throw new IOException("Invalid preset name");
        // Reject traversal in the original path before normalization.
        for (Path part : path) if (part.toString().equals("..")) throw new IOException("Preset path traversal");
        return stem;
    }

    public static Path createDirectories(Path path, java.nio.file.attribute.FileAttribute<?>... attributes) throws IOException {
        return directory(path).directory();
    }

    public static Stream<Path> list(Path path) throws IOException {
        EffectPresetFiles files = directory(path);
        Path root = files.directory();
        return files.list().stream().map(name -> root.resolve(name + ".json"));
    }

    public static String readString(Path path) throws IOException {
        String stem = name(path);
        return directory(path.getParent()).read(stem);
    }

    public static Path writeString(Path path, CharSequence json, OpenOption... options) throws IOException {
        String stem = name(path);
        directory(path.getParent()).write(stem, json.toString());
        return path;
    }

    public static boolean deleteIfExists(Path path) throws IOException {
        String stem = name(path);
        EffectPresetFiles files = directory(path.getParent());
        boolean existed = Files.exists(files.directory().resolve(stem + ".json"), LinkOption.NOFOLLOW_LINKS);
        files.delete(stem);
        return existed;
    }
}
