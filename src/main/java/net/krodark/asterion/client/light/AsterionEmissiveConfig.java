package net.krodark.asterion.client.light;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.meekdev.amnetic.client.bloom.Bloom;
import com.meekdev.amnetic.client.bloom.BloomSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
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
            JsonObject json = GSON.fromJson(Files.readString(PATH), JsonObject.class);
            Values loaded = GSON.fromJson(json, Values.class);
            values = loaded == null ? new Values() : loaded;
            boolean legacy = json != null && !json.has("version");
            if (legacy) migrateLegacy();
            boolean upgradeEyes = legacy || values.version < 3;
            // Upgrade the shipped eye setting once, preserving deliberately customized strengths.
            if (upgradeEyes && values.minotaurEyeStrength == 0.85F) values.minotaurEyeStrength = 1.0F;
            boolean upgradeFire = legacy || values.version < 4;
            if (upgradeFire) {
                if (values.beetleFireStrength == 2F) values.beetleFireStrength = 3.2F;
                if (values.intensity == .16F) values.intensity = .22F;
                if (values.threshold == 1.1F) values.threshold = .95F;
            }
            sanitize();
            if (upgradeEyes || upgradeFire) save();
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
        int quality = AsterionConfig.INSTANCE.cinematicQuality;
        int qualityLevelCap = quality == 0 ? 2 : 3;
        float qualityIntensity = quality == 0 ? 0.65F : quality == 1 ? 0.82F : 1.0F;
        bloom.enabled(values.enabled)
                .all(false)
                .occlude(true)
                .threshold(values.threshold)
                .intensity(values.intensity * qualityIntensity)
                .levels(Math.min(values.levels, qualityLevelCap))
                .scale(values.scale)
                .knee(values.knee);
    }

    public static float minotaurEyeStrength() {
        return values.minotaurEyeStrength;
    }

    public static float beetleFireStrength() {
        return values.beetleFireStrength;
    }

    public static float vineGlowStrength() { return values.vineGlowStrength; }

    private static void migrateLegacy() {
        // Replace the old shipped defaults once; retain custom choices within the new safe ranges.
        if (values.threshold == 0.035F) values.threshold = 1.1F;
        if (values.intensity == 4.8F) values.intensity = 0.16F;
        if (values.levels == 7) values.levels = 2;
        if (values.scale == 0.55F) values.scale = 0.75F;
        if (values.knee == 0.72F) values.knee = 0.25F;
        if (values.minotaurEyeStrength == 4.75F) values.minotaurEyeStrength = 0.85F;
        if (values.vineGlowStrength == 2.25F) values.vineGlowStrength = 0.65F;
        if (values.beetleFireStrength == 6.5F) values.beetleFireStrength = 2.0F;
    }

    private static float finiteClamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
    }

    private static void sanitize() {
        values.version = 4;
        values.threshold = finiteClamp(values.threshold, 0.0F, 2.0F, 1.1F);
        values.intensity = finiteClamp(values.intensity, 0.0F, 0.5F, 0.16F);
        values.levels = Mth.clamp(values.levels, 2, 3);
        values.scale = finiteClamp(values.scale, 0.5F, 1.0F, 0.75F);
        values.knee = finiteClamp(values.knee, 0.0F, 1.0F, 0.25F);
        values.minotaurEyeStrength = finiteClamp(values.minotaurEyeStrength, 0.0F, 1.0F, 1.0F);
        values.beetleFireStrength = finiteClamp(values.beetleFireStrength, 0.0F, 5.0F, 3.2F);
        values.vineGlowStrength = finiteClamp(values.vineGlowStrength, 0.0F, 1.0F, 0.65F);
    }

    private static final class Values {
        private int version = 4;
        private boolean enabled = true;
        private float threshold = .95F;
        private float intensity = .22F;
        private int levels = 2;
        private float scale = 0.75F;
        private float knee = 0.25F;
        private float minotaurEyeStrength = 1.0F;
        private float beetleFireStrength = 3.2F;
        private float vineGlowStrength = 0.65F;
    }
}
