package net.krodark.asterion.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class UpdateFeed {
    record Release(String version, String url) {}
    private static final java.util.regex.Pattern FILE_VERSION = java.util.regex.Pattern.compile(
            "^Asterion-([0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6})-(?:fabric|quilt|forge|neoforge)-mc[0-9.]+\\.jar$",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private UpdateFeed() {}

    static String fetch(String loader, String minecraft, String installed) throws IOException {
        Release release = fetchRelease(loader, minecraft, installed);
        return release == null ? null : release.version();
    }

    static Release fetchRelease(String loader, String minecraft, String installed) {
        if (!java.util.Set.of("fabric", "quilt", "forge", "neoforge").contains(loader)) return null;
        Release result = null;
        try {
            String target = available(read("https://raw.githubusercontent.com/DarkFoxYT/Asterion/master/updates/"
                    + loader + ".json", 65536), minecraft, installed);
            if (target != null) result = new Release(target, UpdateNotifications.DOWNLOADS);
        } catch (Exception unavailable) { }
        try {
            Release published = published(read("https://api.cfwidget.com/1671346", 2 * 1024 * 1024), loader, minecraft, installed);
            if (published != null && (result == null || !newer(result.version(), published.version()))) result = published;
        } catch (Exception unavailable) { }
        return result;
    }

    private static String read(String url, int limit) throws IOException {
        var connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        try (var input = connection.getInputStream()) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Update feed is too large");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static Release published(String json, String loader, String minecraft, String installed) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.get("id").getAsLong() != 1671346 || !root.has("files")) return null;
        Release best = null;
        for (var entry : root.getAsJsonArray("files")) {
            try {
                var file = entry.getAsJsonObject();
                if (!"release".equals(file.get("type").getAsString())) continue;
                boolean gameMatches = false, loaderMatches = false;
                for (var tag : file.getAsJsonArray("versions")) {
                    gameMatches |= minecraft.equals(tag.getAsString());
                    loaderMatches |= loader.equalsIgnoreCase(tag.getAsString());
                }
                if (!gameMatches || !loaderMatches) continue;
                var match = FILE_VERSION.matcher(file.get("name").getAsString());
                if (!match.matches()) continue;
                String version = match.group(1);
                long id = file.get("id").getAsLong();
                if (id > 0 && newer(version, installed) && (best == null || newer(version, best.version()))) {
                    best = new Release(version, UpdateNotifications.DOWNLOADS + "/" + id);
                }
            } catch (RuntimeException invalidEntry) { }
        }
        return best;
    }

    static String available(String json, String minecraft, String installed) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("promos") || !root.get("promos").isJsonObject()) return null;
        JsonObject promos = root.getAsJsonObject("promos");
        var value = promos.get(minecraft + "-recommended");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
        String target = value.getAsString();
        return newer(target, installed) ? target : null;
    }

    private static boolean newer(String target, String installed) {
        if (!stableVersion(target) || !stableVersion(installed)) return false;
        String[] current = installed.split("\\.");
        String[] next = target.split("\\.");
        for (int i = 0; i < 3; i++) {
            int comparison = Integer.compare(Integer.parseInt(next[i]), Integer.parseInt(current[i]));
            if (comparison > 0) return true;
            if (comparison < 0) return false;
        }
        return false;
    }

    private static boolean stableVersion(String version) {
        return version.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}");
    }
}
