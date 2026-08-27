package net.krodark.labyrinth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LabyrinthConfig {
    private static final int CURRENT_VERSION = 9;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("labyrinth.json");
    public static LabyrinthConfig INSTANCE = load();

    public int configVersion = CURRENT_VERSION;
    public int underwaterRuinChance = 800;
    public float mechanismChance = 0.12f;
    public int gatewayDistance = 5_000;
    public int mazeRadiusCells = 80;
    public int cellSize = 13;
    public int wallThickness = 4;
    public int wallHeight = 30;
    public int floorThickness = 4;
    public int mazeLoopChance = 36;
    public int mazeLandmarkChance = 28;
    public int playerBlockDecayTicks = 400;
    public int wallZapDelayTicks = 60;
    public boolean deadSunEnabled = true;
    public boolean dustyAirEnabled = true;
    public float deadSunStrength = 0.661f;
    public float dustyAirStrength = 1.0f;
    public float deadSunHeight = 240.0f;
    public float deadSunSize = 48.0f;
    public float deadSunBrightness = 4.0f;
    public float shaderAnimationSpeed = 1.0f;
    public float dustDensity = 1.499f;
    public float fogStrength = 1.508f;
    public float deadSunX = 0.0f;
    public float deadSunZ = 0.0f;
    public float deadSunCorona = 1.423f;
    public float deadSunDensity = 3.0f;
    public float deadSunOpacity = 1.0f;
    public float deadSunCoreR = 1.0f;
    public float deadSunCoreG = 0.055f;
    public float deadSunCoreB = 0.025f;
    public float deadSunCoronaR = 1.0f;
    public float deadSunCoronaG = 0.025f;
    public float deadSunCoronaB = 0.012f;
    public float dustR = 0.2607004f;
    public float dustG = 0.07607989f;
    public float dustB = 0.07607989f;
    public float fogR = 0.15294118f;
    public float fogG = 0.1364837f;
    public float fogB = 0.049780853f;

    private static LabyrinthConfig load() {
        try {
            if (Files.exists(FILE)) {
                JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                LabyrinthConfig config = GSON.fromJson(json, LabyrinthConfig.class);
                boolean changed = false;
                if (!json.has("configVersion") || config.configVersion < 2) {
                    config.mazeRadiusCells = 80;
                    config.cellSize = 13;
                    config.wallThickness = 4;
                    config.wallHeight = 30;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 3) {
                    config.floorThickness = 4;
                    config.mazeLoopChance = 36;
                    config.mazeLandmarkChance = 28;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 5) {
                    config.deadSunHeight = 148.0f;
                    config.deadSunSize = 24.0f;
                    config.deadSunBrightness = 1.65f;
                    config.shaderAnimationSpeed = 0.55f;
                    config.dustDensity = 1.0f;
                    config.fogStrength = 1.0f;
                    config.deadSunX = 0.0f;
                    config.deadSunZ = 0.0f;
                    config.deadSunCorona = 1.0f;
                    config.deadSunDensity = 1.0f;
                    config.deadSunOpacity = 1.0f;
                    config.deadSunCoreR = 1.0f; config.deadSunCoreG = 0.38f; config.deadSunCoreB = 0.08f;
                    config.deadSunCoronaR = 1.0f; config.deadSunCoronaG = 0.16f; config.deadSunCoronaB = 0.025f;
                    config.dustR = 0.39f; config.dustG = 0.285f; config.dustB = 0.175f;
                    config.fogR = 0.050f; config.fogG = 0.041f; config.fogB = 0.034f;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 6) {
                    config.deadSunDensity = 1.0f;
                    config.deadSunOpacity = 1.0f;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 7) {
                    config.gatewayDistance = 5_000;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 8) {
                    config.playerBlockDecayTicks = 400;
                    config.wallZapDelayTicks = 60;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 9) {
                    config.deadSunCoreR = 1.0f; config.deadSunCoreG = 0.055f; config.deadSunCoreB = 0.025f;
                    config.deadSunCoronaR = 1.0f; config.deadSunCoronaG = 0.025f; config.deadSunCoronaB = 0.012f;
                    changed = true;
                }
                if (changed) {
                    config.configVersion = CURRENT_VERSION;
                    config.save();
                }
                return config;
            }
        } catch (Exception ignored) {
        }
        LabyrinthConfig config = new LabyrinthConfig();
        config.save();
        return config;
    }

    public void sanitize() {
        underwaterRuinChance = Math.max(1, underwaterRuinChance);
        mechanismChance = Math.max(0.0f, Math.min(1.0f, mechanismChance));
        gatewayDistance = Math.max(1_000, gatewayDistance);
        configVersion = CURRENT_VERSION;
        mazeRadiusCells = Math.max(16, Math.min(160, mazeRadiusCells));
        cellSize = Math.max(9, Math.min(21, cellSize | 1));
        wallThickness = Math.max(2, Math.min(6, wallThickness));
        wallThickness = Math.min(wallThickness, cellSize - 5);
        wallHeight = Math.max(16, Math.min(64, wallHeight));
        floorThickness = Math.max(2, Math.min(8, floorThickness));
        mazeLoopChance = Math.max(16, Math.min(96, mazeLoopChance));
        mazeLandmarkChance = Math.max(12, Math.min(96, mazeLandmarkChance));
        playerBlockDecayTicks = Math.max(40, Math.min(3_600, playerBlockDecayTicks));
        wallZapDelayTicks = Math.max(20, Math.min(200, wallZapDelayTicks));
        deadSunStrength = Math.max(0.0f, Math.min(2.0f, deadSunStrength));
        dustyAirStrength = Math.max(0.0f, Math.min(2.0f, dustyAirStrength));
        deadSunHeight = Math.max(100.0f, Math.min(240.0f, deadSunHeight));
        deadSunSize = Math.max(8.0f, Math.min(48.0f, deadSunSize));
        deadSunBrightness = Math.max(0.25f, Math.min(4.0f, deadSunBrightness));
        shaderAnimationSpeed = Math.max(0.0f, Math.min(2.0f, shaderAnimationSpeed));
        dustDensity = Math.max(0.0f, Math.min(2.5f, dustDensity));
        fogStrength = Math.max(0.0f, Math.min(2.5f, fogStrength));
        deadSunX = Math.max(-1024.0f, Math.min(1024.0f, deadSunX));
        deadSunZ = Math.max(-1024.0f, Math.min(1024.0f, deadSunZ));
        deadSunCorona = Math.max(0.0f, Math.min(3.0f, deadSunCorona));
        deadSunDensity = Math.max(0.1f, Math.min(3.0f, deadSunDensity));
        deadSunOpacity = Math.max(0.0f, Math.min(1.0f, deadSunOpacity));
        deadSunCoreR = clampColor(deadSunCoreR); deadSunCoreG = clampColor(deadSunCoreG); deadSunCoreB = clampColor(deadSunCoreB);
        deadSunCoronaR = clampColor(deadSunCoronaR); deadSunCoronaG = clampColor(deadSunCoronaG); deadSunCoronaB = clampColor(deadSunCoronaB);
        dustR = clampColor(dustR); dustG = clampColor(dustG); dustB = clampColor(dustB);
        fogR = clampColor(fogR); fogG = clampColor(fogG); fogB = clampColor(fogB);
    }

    private static float clampColor(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            Labyrinth.LOGGER.warn("Could not save Labyrinth config", e);
        }
    }
}
