package net.krodark.asterion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AsterionConfig {
    private static final int CURRENT_VERSION = 24;
    private static final Logger LOGGER = LoggerFactory.getLogger("asterion.config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("asterion.json");
    public static AsterionConfig INSTANCE = load();

    public int configVersion = CURRENT_VERSION;
    public int underwaterRuinChance = 800;
    public float mechanismChance = 0.12f;
    public int gatewayDistance = 900;
    public int mazeRadiusCells = 80;
    public int cellSize = 13;
    public int wallThickness = 3;
    public int wallHeight = 30;
    public int floorThickness = 4;
    public int mazeLoopChance = 36;
    public int mazeLandmarkChance = 28;
    public int playerBlockDecayTicks = 400;
    public int wallZapDelayTicks = 60;
    public int minotaurStalkDistance = 43;
    public int minotaurApproachDistance = 20;
    public int minotaurGazeMinTicks = 140;
    public int minotaurGazeMaxTicks = 200;
    public int minotaurWindupMinTicks = 60;
    public int minotaurWindupMaxTicks = 100;
    public int minotaurEscapeTicks = 2_400;
    public int minotaurEscapeDistance = 32;
    public int minotaurDamageMin = 50;
    public int minotaurDamageMax = 70;
    public float minotaurScale = 2.0f;
    public float minotaurPathfindingMultiplier = 64.0f;
    public int minotaurRepathTicks = 20;
    public int minotaurStuckRecoveryTicks = 100;
    public boolean minotaurUnkillable = true;
    public float minotaurHorizontalFov = 105.0f;
    public float minotaurVerticalFov = 70.0f;
    public int minotaurBossPillarCount = 6;
    public boolean cinematicsEnabled = true;
    /** -1 preserves vanilla brightness; 0 is Moody and 100 is Bright. */
    public int brightnessPercent = 0;
    public int musicVolumePercent = 50;
    public int cinematicQuality = 2;
    public int ambientParticleQuality = 2;
    public boolean potatoParticleCulling = false;
    public int ragdollPhysicsQuality = 2;
    public boolean dynamicLightsEnabled = true;
    public int dynamicLightQuality = 2;
    public boolean droppedItemLights = true;
    public int dynamicLightRangePercent = 100;
    public int maxDynamicLights = 96;
    public boolean adaptivePerformance = true;
    public int performanceTargetFps = 240;
    public boolean ragdollEquipment = true;
    public boolean ragdollMashRecovery = true;
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
        if (Files.notExists(FILE)) {
            AsterionConfig config = new AsterionConfig();
            config.save();
            return config;
        }

        try {
            JsonElement contents = JsonParser.parseString(Files.readString(FILE));
            if (!contents.isJsonObject()) {
                throw new JsonParseException("Expected a JSON object");
            }
            JsonObject json = contents.getAsJsonObject();
            AsterionConfig config = GSON.fromJson(json, AsterionConfig.class);
            int version = json.has("configVersion") && !json.get("configVersion").isJsonNull()
                    ? config.configVersion : 0;
            config.migrate(version);
            config.sanitize();
            if (version < CURRENT_VERSION) config.save();
            return config;
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("Could not load {}: {}. Using defaults; the file was left unchanged.",
                    FILE, exception.getMessage());
            AsterionConfig config = new AsterionConfig();
            config.sanitize();
            return config;
        }
    }

    private void migrate(int version) {
        if (version < 2) {
            mazeRadiusCells = 80;
            cellSize = 13;
            wallHeight = 30;
        }
        if (version < 3) {
            floorThickness = 4;
            mazeLoopChance = 36;
            mazeLandmarkChance = 28;
        }
        if (version < 7) {
            gatewayDistance = 900;
        }
        if (version < 8) {
            playerBlockDecayTicks = 400;
            wallZapDelayTicks = 60;
        }
        if (version < 10) {
            minotaurStalkDistance = 40;
            minotaurApproachDistance = 24;
            minotaurGazeMinTicks = 140;
            minotaurGazeMaxTicks = 200;
            minotaurWindupMinTicks = 60;
            minotaurWindupMaxTicks = 100;
            minotaurEscapeTicks = 2_400;
            minotaurEscapeDistance = 32;
            minotaurDamageMin = 50;
            minotaurDamageMax = 70;
        }
        if (version < 13) {
            minotaurScale = 2.0f;
            minotaurPathfindingMultiplier = 32.0f;
            minotaurRepathTicks = 2;
            minotaurStuckRecoveryTicks = 24;
            minotaurUnkillable = true;
        }
        if (version < 15) {
            minotaurHorizontalFov = 105.0f;
            minotaurVerticalFov = 70.0f;
        }
        if (version < 16) {
            minotaurBossPillarCount = 8;
        }
        if (version < 17) {
            cinematicsEnabled = true;
            cinematicQuality = 2;
            dynamicLightQuality = 2;
            ragdollEquipment = true;
            enhancedLightning = true;
        }
        if (version < 18) {
            ragdollMashRecovery = false;
        }
        if (version < 19) {
            wallThickness = 2;
        }
        if (version < 20) {
            applySkyDefaults();
        }
        if (version < 21) {
            ambientParticleQuality = 2;
            ragdollPhysicsQuality = 2;
            dynamicLightsEnabled = true;
            droppedItemLights = true;
            dynamicLightRangePercent = 100;
            maxDynamicLights = 96;
        }
        if (version < 22) {
            adaptivePerformance = true;
            performanceTargetFps = 240;
        }
        if (version < 23) ragdollMashRecovery = true;
        if (version < 24 && wallThickness < 3) wallThickness = 3;
    }

    private void applySkyDefaults() {
        deadSunEnabled = true;
        dustyAirEnabled = true;
        deadSunStrength = 0.82f;
        dustyAirStrength = 1.0f;
        deadSunHeight = 240.0f;
        deadSunSize = 48.0f;
        deadSunBrightness = 4.0f;
        shaderAnimationSpeed = 1.0f;
        dustDensity = 1.499f;
        fogStrength = 1.508f;
        deadSunX = 0.0f;
        deadSunZ = 0.0f;
        deadSunCorona = 1.80f;
        deadSunDensity = 3.0f;
        deadSunOpacity = 1.0f;
        deadSunCoreR = 1.0f;
        deadSunCoreG = 0.012f;
        deadSunCoreB = 0.006f;
        deadSunCoronaR = 1.0f;
        deadSunCoronaG = 0.085f;
        deadSunCoronaB = 0.008f;
        dustR = 0.2607004f;
        dustG = 0.07607989f;
        dustB = 0.07607989f;
        fogR = 0.15294118f;
        fogG = 0.1364837f;
        fogB = 0.049780853f;
    }

    public void sanitize() {
        underwaterRuinChance = Math.max(1, underwaterRuinChance);
        mechanismChance = clamp(mechanismChance, 0.0f, 1.0f);
        gatewayDistance = Math.max(128, Math.min(1_000, gatewayDistance));
        configVersion = CURRENT_VERSION;
        mazeRadiusCells = Math.max(16, Math.min(160, mazeRadiusCells));
        cellSize = Math.max(9, Math.min(21, cellSize | 1));
        wallThickness = Math.max(3, Math.min(6, wallThickness));
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
        minotaurScale = clamp(minotaurScale, 0.75f, 4.0f);
        minotaurPathfindingMultiplier = clamp(minotaurPathfindingMultiplier, 2.0f, 64.0f);
        minotaurRepathTicks = Math.max(1, Math.min(20, minotaurRepathTicks));
        minotaurStuckRecoveryTicks = Math.max(8, Math.min(100, minotaurStuckRecoveryTicks));
        minotaurHorizontalFov = clamp(minotaurHorizontalFov, 35.0f, 170.0f);
        minotaurVerticalFov = clamp(minotaurVerticalFov, 25.0f, 120.0f);
        minotaurBossPillarCount = Math.max(4, Math.min(16, minotaurBossPillarCount));
        cinematicQuality = Math.max(0, Math.min(2, cinematicQuality));
        brightnessPercent = Math.clamp(brightnessPercent, -1, 100);
        musicVolumePercent = Math.clamp(musicVolumePercent, 0, 100);
        ambientParticleQuality = Math.max(0, Math.min(2, ambientParticleQuality));
        ragdollPhysicsQuality = Math.max(0, Math.min(2, ragdollPhysicsQuality));
        dynamicLightQuality = Math.max(0, Math.min(2, dynamicLightQuality));
        dynamicLightRangePercent = Math.max(25, Math.min(100, dynamicLightRangePercent));
        maxDynamicLights = Math.max(8, Math.min(256, maxDynamicLights));
        performanceTargetFps = Math.max(60, Math.min(360, performanceTargetFps));
        deadSunStrength = clamp(deadSunStrength, 0.0f, 2.0f);
        dustyAirStrength = clamp(dustyAirStrength, 0.0f, 2.0f);
        deadSunHeight = clamp(deadSunHeight, 100.0f, 240.0f);
        deadSunSize = clamp(deadSunSize, 8.0f, 48.0f);
        deadSunBrightness = clamp(deadSunBrightness, 0.25f, 4.0f);
        shaderAnimationSpeed = clamp(shaderAnimationSpeed, 0.0f, 2.0f);
        dustDensity = clamp(dustDensity, 0.0f, 2.5f);
        fogStrength = clamp(fogStrength, 0.0f, 2.5f);
        deadSunX = clamp(deadSunX, -1024.0f, 1024.0f);
        deadSunZ = clamp(deadSunZ, -1024.0f, 1024.0f);
        deadSunCorona = clamp(deadSunCorona, 0.0f, 3.0f);
        deadSunDensity = clamp(deadSunDensity, 0.1f, 3.0f);
        deadSunOpacity = clamp(deadSunOpacity, 0.0f, 1.0f);
        deadSunCoreR = clamp(deadSunCoreR, 0.0f, 1.0f);
        deadSunCoreG = clamp(deadSunCoreG, 0.0f, 1.0f);
        deadSunCoreB = clamp(deadSunCoreB, 0.0f, 1.0f);
        deadSunCoronaR = clamp(deadSunCoronaR, 0.0f, 1.0f);
        deadSunCoronaG = clamp(deadSunCoronaG, 0.0f, 1.0f);
        deadSunCoronaB = clamp(deadSunCoronaB, 0.0f, 1.0f);
        dustR = clamp(dustR, 0.0f, 1.0f);
        dustG = clamp(dustG, 0.0f, 1.0f);
        dustB = clamp(dustB, 0.0f, 1.0f);
        fogR = clamp(fogR, 0.0f, 1.0f);
        fogG = clamp(fogG, 0.0f, 1.0f);
        fogB = clamp(fogB, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Float.isNaN(value) ? min : Math.clamp(value, min, max);
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.warn("Could not save Asterion config", e);
        }
    }
}
