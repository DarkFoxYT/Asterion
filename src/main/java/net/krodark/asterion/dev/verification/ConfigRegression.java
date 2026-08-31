package net.krodark.asterion.dev.verification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.krodark.asterion.AsterionConfig;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigRegression {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("build"), "config-regression-");
        Path configDirectory = directory.resolve("config");
        Path file = configDirectory.resolve("asterion.json");
        try {
            // Standalone checks need the config directory normally supplied by Fabric's launcher.
            Object loader = FabricLoader.getInstance();
            Method setGameDir = loader.getClass().getDeclaredMethod("setGameDir", Path.class);
            setGameDir.setAccessible(true);
            setGameDir.invoke(loader, directory);

            AsterionConfig defaults = AsterionConfig.INSTANCE;
            require(Files.isRegularFile(file), "first launch did not create a config");
            require(defaults.cellSize == 13, "default maze dimensions changed");
            require(!defaults.potatoParticleCulling, "extra particle culling must default off");
            Method load = AsterionConfig.class.getDeclaredMethod("load");
            load.setAccessible(true);

            preservesInvalidFiles(file, load);
            unreadableFile(file, load);
            migrations(file, load);
            currentSettings(file, load);
            nonFiniteSettings(file, load);
            sanitizedRoundTrip(file, load);
            System.out.println("Config regression: " + checks + " checks passed");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(configDirectory);
            Files.deleteIfExists(directory);
        }
    }

    private static void unreadableFile(Path file, Method load) throws Exception {
        Files.delete(file);
        Files.createDirectory(file);
        try {
            require(read(load).cellSize == 13, "unreadable config did not use defaults");
            require(Files.isDirectory(file), "unreadable config path was replaced");
        } finally {
            Files.delete(file);
        }
    }

    private static void preservesInvalidFiles(Path file, Method load) throws Exception {
        for (String input : new String[]{"{broken", "null", "[]", "", "{\"cellSize\":\"oops\"}"}) {
            Files.writeString(file, input);
            AsterionConfig config = read(load);
            require(Files.readString(file).equals(input), "loading replaced an invalid config: " + input);
            require(config.cellSize == 13 && config.deadSunHeight == 240.0F && config.deadSunSize == 48.0F,
                    "invalid config did not fall back to sanitized defaults");
        }
    }

    private static void migrations(Path file, Method load) throws Exception {
        for (int version = 0; version <= 21; version++) {
            JsonObject input = new JsonObject();
            if (version > 0) input.addProperty("configVersion", version);
            input.addProperty("underwaterRuinChance", 919);
            input.addProperty("wallThickness", 5);
            input.addProperty("ragdollMashRecovery", true);
            input.addProperty("dustR", 0.8F);
            input.addProperty("ambientParticleQuality", 0);
            Files.writeString(file, input.toString());

            AsterionConfig config = read(load);
            require(config.configVersion == 21, "version was not upgraded: " + version);
            require(config.underwaterRuinChance == 919, "migration changed an unrelated setting");
            require(config.wallThickness == (version < 19 ? 2 : 5), "wall migration: " + version);
            require(config.ragdollMashRecovery == (version >= 18), "recovery migration: " + version);
            require(config.dustR == (version < 20 ? 0.2607004F : 0.8F), "sky migration: " + version);
            require(config.ambientParticleQuality == (version < 21 ? 2 : 0), "quality migration: " + version);

            config.save();
            String saved = Files.readString(file);
            read(load).save();
            require(saved.equals(Files.readString(file)), "migration is not idempotent: " + version);
        }
    }

    private static void currentSettings(Path file, Method load) throws Exception {
        String input = """
                {"configVersion":21,"cinematicsEnabled":false,"dynamicLightsEnabled":false,
                 "mechanismChance":0.7,"cellSize":17,"wallThickness":5,
                 "minotaurGazeMinTicks":200,"minotaurGazeMaxTicks":250,"deadSunSize":32}
                """;
        Files.writeString(file, input);
        AsterionConfig config = read(load);
        require(!config.cinematicsEnabled && !config.dynamicLightsEnabled, "custom toggles were reset");
        require(config.mechanismChance == 0.7F && config.cellSize == 17 && config.wallThickness == 5,
                "valid gameplay settings were reset");
        require(config.minotaurGazeMinTicks == 200 && config.minotaurGazeMaxTicks == 250,
                "valid timing settings were reset");
        require(config.deadSunSize == 32, "valid visual setting was reset");
        require(Files.readString(file).equals(input), "current config was rewritten during load");
    }

    private static void nonFiniteSettings(Path file, Method load) throws Exception {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            AsterionConfig config = new AsterionConfig();
            for (var field : AsterionConfig.class.getFields()) {
                if (field.getType() == float.class) field.setFloat(config, value);
            }
            config.sanitize();
            for (var field : AsterionConfig.class.getFields()) {
                if (field.getType() == float.class) {
                    require(Float.isFinite(field.getFloat(config)), "non-finite setting survived: " + field.getName());
                }
            }
            config.save();
            require(JsonParser.parseString(Files.readString(file)).isJsonObject(), "sanitized config is not valid JSON");
            read(load);
        }
    }

    private static void sanitizedRoundTrip(Path file, Method load) throws Exception {
        AsterionConfig config = new AsterionConfig();
        config.cellSize = 2;
        config.potatoParticleCulling = true;
        config.wallThickness = 99;
        config.minotaurGazeMinTicks = 300;
        config.minotaurGazeMaxTicks = 1;
        config.minotaurDamageMin = 90;
        config.minotaurDamageMax = 1;
        config.save();
        AsterionConfig loaded = read(load);
        require(loaded.potatoParticleCulling, "particle culling choice was not preserved");
        require(loaded.cellSize == 9 && loaded.wallThickness == 4, "corridor clearance became invalid");
        require(loaded.minotaurGazeMinTicks == 300 && loaded.minotaurGazeMaxTicks == 300,
                "gaze timing bounds were inverted");
        require(loaded.minotaurDamageMin == 90 && loaded.minotaurDamageMax == 90,
                "damage bounds were inverted");
        String saved = Files.readString(file);
        loaded.save();
        require(saved.equals(Files.readString(file)), "saving sanitized settings changed them again");
    }

    private static AsterionConfig read(Method load) throws Exception {
        return (AsterionConfig) load.invoke(null);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
