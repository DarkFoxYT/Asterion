package com.meekdev.amnetic.client.particle.editor;

import java.nio.file.*;
import java.util.Comparator;

public final class SafePresetIORegression {
    public static void main(String[] args) throws Exception {
        Path game = Files.createTempDirectory("amnetic-adapter-test-");
        try {
            Path directory = SafePresetIO.createDirectories(game.resolve("amnetic/particles"));
            Path preset = directory.resolve("local.json");
            SafePresetIO.writeString(preset, "{}");
            if (!SafePresetIO.readString(preset).equals("{}")) throw new AssertionError("round trip");
            try (var files = SafePresetIO.list(directory)) {
                if (files.count() != 1) throw new AssertionError("listing");
            }
            for (String name : new String[]{"../outside", "..\\outside", "/outside", "C:\\outside", "CON", "file:stream", "trailing."}) {
                try { SafePresetIO.validateName(name); throw new AssertionError("Unsafe name accepted"); }
                catch (IllegalArgumentException expected) { }
            }
            try { SafePresetIO.readString(directory.resolve("../outside.json")); throw new AssertionError("Traversal accepted"); }
            catch (java.io.IOException expected) { }
            if (!SafePresetIO.deleteIfExists(preset) || SafePresetIO.deleteIfExists(preset)) throw new AssertionError("deletion");
            System.out.println("PASS: version-independent preset adapter and original-name validation");
        } finally {
            try (var paths = Files.walk(game)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
