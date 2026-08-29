package net.krodark.asterion.client.light;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.bloom.BloomSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.krodark.asterion.Asterion;
import net.minecraft.util.Mth;

/** Persistent client-side grading for Asterion's emissive masks and flame particles. */
public final class AsterionEmissiveConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("asterion-emissive.json");
    private static Values values = new Values();

    private AsterionEmissiveConfig() {}

    public static void load() {
        if (!Files.isRegularFile(PATH)) {
            save();
            apply();
            return;
        }
        try {
            Values loaded = GSON.fromJson(Files.readString(PATH), Values.class);
            values = loaded == null ? new Values() : loaded;
            sanitize();
        } catch (Exception exception) {
            Asterion.LOGGER.warn("Unable to load Asterion emissive config {}", PATH, exception);
            values = new Values();
        }
        apply();
    }

    public static void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(values));
        } catch (IOException exception) {
            Asterion.LOGGER.warn("Unable to save Asterion emissive config {}", PATH, exception);
        }
    }

    public static void apply() {
        BloomSettings bloom = Bloom.settings();
        bloom.enabled(values.enabled)
                .all(false)
                .occlude(true)
                .threshold(values.threshold)
                .intensity(values.intensity)
                .levels(values.levels)
                .scale(values.scale)
                .knee(values.knee);
    }

    public static float minotaurEyeStrength() {
        return values.minotaurEyeStrength;
    }

    public static float beetleFireStrength() {
        return values.beetleFireStrength;
    }

    private static void sanitize() {
        values.threshold = Mth.clamp(values.threshold, 0.0001F, 2.0F);
        values.intensity = Mth.clamp(values.intensity, 0.0F, 10.0F);
        values.levels = Mth.clamp(values.levels, 2, 8);
        values.scale = Mth.clamp(values.scale, 0.05F, 1.0F);
        values.knee = Mth.clamp(values.knee, 0.0F, 1.0F);
        values.minotaurEyeStrength = Mth.clamp(values.minotaurEyeStrength, 0.5F, 10.0F);
        values.beetleFireStrength = Mth.clamp(values.beetleFireStrength, 0.5F, 12.0F);
    }

    private static final class Values {
        private boolean enabled = true;
        private float threshold = 0.035F;
        private float intensity = 4.8F;
        private int levels = 7;
        private float scale = 0.55F;
        private float knee = 0.72F;
        private float minotaurEyeStrength = 4.75F;
        private float beetleFireStrength = 6.5F;
    }
}
