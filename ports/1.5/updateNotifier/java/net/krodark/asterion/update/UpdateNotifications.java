package net.krodark.asterion.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class UpdateNotifications {
    public record Notice(String version, String installed, String minecraft, String loader, String url) {}
    public static final String DOWNLOADS = "https://www.curseforge.com/minecraft/mc-mods/asterion/files";
    private static volatile Notice available;
    private static boolean started;
    private static Path preferences;
    private static final Properties settings = new Properties();

    private UpdateNotifications() {}

    public static synchronized void start(Path gameDirectory) {
        if (started) return;
        started = true;
        preferences = gameDirectory.resolve("config/asterion-updates.properties");
        Thread worker = new Thread(() -> {
            try {
                if (Files.isRegularFile(preferences)) {
                    try (var input = Files.newInputStream(preferences)) { settings.load(input); }
                }
                if (!Boolean.parseBoolean(settings.getProperty("enabled", "true"))) return;
                Properties build = new Properties();
                try (var input = UpdateNotifications.class.getResourceAsStream("/asterion-update.properties")) {
                    if (input == null) return;
                    build.load(input);
                }
                String loader = build.getProperty("loader");
                if (loader.equals("fabric")) {
                    try {
                        Class.forName("org.quiltmc.loader.api.QuiltLoader", false, UpdateNotifications.class.getClassLoader());
                        loader = "quilt";
                    } catch (ClassNotFoundException absent) { }
                }
                String minecraft = build.getProperty("minecraft");
                String installed = build.getProperty("version");
                var target = UpdateFeed.fetchRelease(loader, minecraft, installed);
                if (target != null && !target.version().equals(settings.getProperty("dismissed"))) {
                    available = new Notice(target.version(), installed, minecraft, loader, target.url());
                }
            } catch (Exception ignored) {
                // Offline play and an unavailable feed must not interrupt the menu.
            }
        }, "Asterion update check");
        worker.setDaemon(true);
        worker.start();
    }

    public static Notice available() { return available; }

    public static void dismiss() {
        Notice notice = available;
        available = null;
        if (notice == null || preferences == null) return;
        settings.setProperty("dismissed", notice.version());
        Thread save = new Thread(() -> {
            try {
                Files.createDirectories(preferences.getParent());
                try (var output = Files.newOutputStream(preferences)) {
                    settings.store(output, "Asterion update notifications");
                }
            } catch (IOException ignored) { }
        }, "Asterion update preferences");
        save.setDaemon(true);
        save.start();
    }
}
