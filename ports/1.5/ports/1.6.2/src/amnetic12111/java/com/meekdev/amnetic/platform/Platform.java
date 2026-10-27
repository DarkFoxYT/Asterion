package com.meekdev.amnetic.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

public final class Platform {
    private Platform() { }
    public static boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
    public static boolean isDevelopmentEnvironment() { return !FMLEnvironment.isProduction(); }
    public static Path gameDirectory() { return FMLPaths.GAMEDIR.get(); }
    public static List<Path> developmentResourceRoots() {
        if (!isDevelopmentEnvironment()) return List.of();
        List<Path> roots = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(System.getProperty("path.separator"))) {
            Path path = Path.of(entry).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) roots.add(path);
        }
        try {
            Path own = Path.of(Platform.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(own) && !roots.contains(own)) roots.add(own);
        } catch (Exception ignored) { }
        return roots;
    }
}
