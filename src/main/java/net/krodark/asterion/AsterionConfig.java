package net.krodark.asterion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AsterionConfig {
    private static final int CURRENT_VERSION = 19;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("asterion.json");
    public static AsterionConfig INSTANCE = load();

    public int configVersion = CURRENT_VERSION;
    public int underwaterRuinChance = 800;
    public float mechanismChance = 0.12f;
    public int gatewayDistance = 5_000;
    public int mazeRadiusCells = 80;
    public int cellSize = 13;
    public int wallThickness = 2;
    public int wallHeight = 30;
    public int floorThickness = 10;
    public int mazeLoopChance = 36;
    public int mazeLandmarkChance = 28;
    public int playerBlockDecayTicks = 200;
    public int wallZapDelayTicks = 60;
    public int minotaurStalkDistance = 40;
    public int minotaurApproachDistance = 24;
    public int minotaurGazeMinTicks = 140;
    public int minotaurGazeMaxTicks = 200;
    public int minotaurWindupMinTicks = 60;
    public int minotaurWindupMaxTicks = 100;
    public int minotaurEscapeTicks = 2_400;
    public int minotaurEscapeDistance = 32;
    public int minotaurDamageMin = 50;
    public int minotaurDamageMax = 70;
    public float minotaurScale = 2.0f;
    public float minotaurPathfindingMultiplier = 32.0f;
    public int minotaurRepathTicks = 2;
    public int minotaurStuckRecoveryTicks = 24;
    public boolean minotaurUnkillable = true;
    public float minotaurHorizontalFov = 105.0f;
    public float minotaurVerticalFov = 70.0f;
    public int minotaurBossPillarCount = 8;
    public boolean cinematicsEnabled = true;
    /** 0 low, 1 medium, 2 high. */
    public int cinematicQuality = 2;
    /** 0 low, 1 medium, 2 high. */
    public int dynamicLightQuality = 2;
    public boolean ragdollEquipment = true;
    /** true uses repeated presses; false uses a continuous hold for forced ragdoll recovery. */
    public boolean ragdollMashRecovery = false;
    public boolean enhancedLightning = true;
    public boolean deadSunEnabled = true;
    public boolean dustyAirEnabled = true;
    public float deadSunStrength = 0.82f;
    public float dustyAirStrength = 1.0f;
    public float deadSunHeight = 260.0f;
    public float deadSunSize = 68.0f;
    public float deadSunBrightness = 4.0f;
    public float shaderAnimationSpeed = 1.0f;
    public float dustDensity = 1.499f;
    public float fogStrength = 1.508f;
    public float deadSunX = 0.0f;
    public float deadSunZ = 0.0f;
    public float deadSunCorona = 1.80f;
    public float deadSunDensity = 3.0f;
    public float deadSunOpacity = 1.0f;
    public float deadSunCoreR = 1.0f;
    public float deadSunCoreG = 0.012f;
    public float deadSunCoreB = 0.006f;
    public float deadSunCoronaR = 1.0f;
    public float deadSunCoronaG = 0.085f;
    public float deadSunCoronaB = 0.008f;
    public float dustR = 0.2607004f;
    public float dustG = 0.07607989f;
    public float dustB = 0.07607989f;
    public float fogR = 0.15294118f;
    public float fogG = 0.1364837f;
    public float fogB = 0.049780853f;

    private static AsterionConfig load() {
        try {
            if (Files.exists(FILE)) {
                JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                AsterionConfig config = GSON.fromJson(json, AsterionConfig.class);
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
                if (!json.has("configVersion") || config.configVersion < 10) {
                    config.minotaurStalkDistance = 40;
                    config.minotaurApproachDistance = 24;
                    config.minotaurGazeMinTicks = 140;
                    config.minotaurGazeMaxTicks = 200;
                    config.minotaurWindupMinTicks = 60;
                    config.minotaurWindupMaxTicks = 100;
                    config.minotaurEscapeTicks = 2_400;
                    config.minotaurEscapeDistance = 32;
                    config.minotaurDamageMin = 50;
                    config.minotaurDamageMax = 70;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 11) {
                    config.deadSunStrength = 0.82f;
                    config.deadSunSize = 68.0f;
                    config.deadSunCorona = 1.80f;
                    config.dustR = 0.30f; config.dustG = 0.36f; config.dustB = 0.32f;
                    config.fogR = 0.18f; config.fogG = 0.23f; config.fogB = 0.20f;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 12) {
                    config.dustR = 0.2607004f;
                    config.dustG = 0.07607989f;
                    config.dustB = 0.07607989f;
                    config.fogR = 0.15294118f;
                    config.fogG = 0.1364837f;
                    config.fogB = 0.049780853f;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 13) {
                    config.minotaurScale = 2.0f;
                    config.minotaurPathfindingMultiplier = 32.0f;
                    config.minotaurRepathTicks = 2;
                    config.minotaurStuckRecoveryTicks = 24;
                    config.minotaurUnkillable = true;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 14) {
                    config.deadSunCoreR = 1.0f;
                    config.deadSunCoreG = 0.012f;
                    config.deadSunCoreB = 0.006f;
                    config.deadSunCoronaR = 1.0f;
                    config.deadSunCoronaG = 0.085f;
                    config.deadSunCoronaB = 0.008f;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 15) {
                    config.minotaurHorizontalFov = 105.0f;
                    config.minotaurVerticalFov = 70.0f;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 16) {
                    config.minotaurBossPillarCount = 8;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 17) {
                    config.cinematicsEnabled = true;
                    config.cinematicQuality = 2;
                    config.dynamicLightQuality = 2;
                    config.ragdollEquipment = true;
                    config.enhancedLightning = true;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 18) {
                    config.ragdollMashRecovery = false;
                    changed = true;
                }
                if (!json.has("configVersion") || config.configVersion < 19) {
                    config.wallThickness = 2;
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
        AsterionConfig config = new AsterionConfig();
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
        minotaurStalkDistance = Math.max(28, Math.min(56, minotaurStalkDistance));
        minotaurApproachDistance = Math.max(14, Math.min(minotaurStalkDistance - 6, minotaurApproachDistance));
        minotaurGazeMinTicks = Math.max(60, Math.min(300, minotaurGazeMinTicks));
        minotaurGazeMaxTicks = Math.max(minotaurGazeMinTicks, Math.min(400, minotaurGazeMaxTicks));
        minotaurWindupMinTicks = Math.max(40, Math.min(120, minotaurWindupMinTicks));
        minotaurWindupMaxTicks = Math.max(minotaurWindupMinTicks, Math.min(160, minotaurWindupMaxTicks));
        minotaurEscapeTicks = Math.max(1_200, Math.min(4_800, minotaurEscapeTicks));
        minotaurEscapeDistance = Math.max(20, Math.min(56, minotaurEscapeDistance));
        minotaurDamageMin = Math.max(20, Math.min(100, minotaurDamageMin));
        minotaurDamageMax = Math.max(minotaurDamageMin, Math.min(140, minotaurDamageMax));
        minotaurScale = Math.max(0.75f, Math.min(4.0f, minotaurScale));
        minotaurPathfindingMultiplier = Math.max(2.0f, Math.min(64.0f, minotaurPathfindingMultiplier));
        minotaurRepathTicks = Math.max(1, Math.min(20, minotaurRepathTicks));
        minotaurStuckRecoveryTicks = Math.max(8, Math.min(100, minotaurStuckRecoveryTicks));
        minotaurHorizontalFov = Math.max(35.0f, Math.min(170.0f, minotaurHorizontalFov));
        minotaurVerticalFov = Math.max(25.0f, Math.min(120.0f, minotaurVerticalFov));
        minotaurBossPillarCount = Math.max(4, Math.min(16, minotaurBossPillarCount));
        cinematicQuality = Math.max(0, Math.min(2, cinematicQuality));
        dynamicLightQuality = Math.max(0, Math.min(2, dynamicLightQuality));
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
            Asterion.LOGGER.warn("Could not save Asterion config", e);
        }
    }
}
