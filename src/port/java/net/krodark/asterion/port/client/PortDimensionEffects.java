package net.krodark.asterion.port.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.krodark.asterion.worldgen.CatacombLayout;
import net.krodark.asterion.worldgen.LabyrinthLevels;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Drives the depth-aware Veil atmosphere used in the Labyrinth.  The shader
 * reconstructs a world ray from the real camera basis and integrates dust only
 * up to the scene depth, so it cannot turn into the old full-screen colour wash.
 */
public final class PortDimensionEffects {
    private static final ResourceLocation PIPELINE = Asterion.id("dimension/atmosphere");
    private static boolean active;
    private static int biomeTarget;
    private static float overgrowthBlend;
    private static float crimsonBlend;
    private static float catacombBlend;
    private static float arenaBlend;
    private static float caveBlend;
    private static float forgeBlend;
    private static float floodBlend;

    private PortDimensionEffects() {}

    public static void initialize() {
        // A client tick is only 20 Hz.  Camera data sampled there visibly trails a
        // high-refresh-rate camera, making the atmosphere appear screen-locked.
        // Refresh it after the translucent world pass on every rendered frame.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> updateFrame(Minecraft.getInstance()));
    }

    public static void tick(Minecraft client) {
        tickBiomeAtmosphere(client);
        boolean wanted = client.level != null && client.player != null
                && client.level.dimension().equals(Asterion.ASTERION_LEVEL)
                && (AsterionConfig.INSTANCE.deadSunEnabled || AsterionConfig.INSTANCE.dustyAirEnabled);
        var manager = VeilRenderSystem.renderer().getPostProcessingManager();
        if (wanted != active) {
            active = wanted;
            if (wanted) manager.add(20, PIPELINE);
            else manager.remove(PIPELINE);
        }
        if (!wanted) return;
        updateFrame(client);
    }

    private static void updateFrame(Minecraft client) {
        if (!active || client.level == null || client.player == null) return;
        var manager = VeilRenderSystem.renderer().getPostProcessingManager();
        PostPipeline pipeline = manager.getPipeline(PIPELINE);
        if (pipeline == null) return;
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 position = camera.getPosition();
        Vector3f forward = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        float aspect = Math.max(1, client.getWindow().getWidth())
                / (float)Math.max(1, client.getWindow().getHeight());
        float partialTick = client.getTimer().getGameTimeDeltaPartialTick(true);
        float fov = (float)Math.toRadians(((net.krodark.asterion.mixin.PortGameRendererAccessor)
                client.gameRenderer).asterion$currentFov(camera, partialTick, true));
        float far = Math.max(64.0F, client.gameRenderer.getDepthFar());
        AsterionConfig config = AsterionConfig.INSTANCE;
        Vector3f atmosphere = atmosphereSettings(config);
        Vector3f dust = dustColor(config);
        Vector3f fog = fogColor(config);

        pipeline.getUniformSafe("CameraPosition").setVector((float)position.x, (float)position.y, (float)position.z);
        pipeline.getUniformSafe("CameraForward").setVector(forward);
        pipeline.getUniformSafe("CameraUp").setVector(up);
        pipeline.getUniformSafe("CameraRight").setVector(-left.x, -left.y, -left.z);
        pipeline.getUniformSafe("ProjectionData").setVector((float)Math.tan(fov * 0.5F), aspect, 0.05F, far);
        float eclipse = PortDeadSunEvents.eclipse();
        pipeline.getUniformSafe("EffectData").setVector(
                config.dustyAirEnabled ? config.dustyAirStrength : 0.0F,
                config.deadSunEnabled ? config.deadSunStrength : 0.0F,
                atmosphere.x, atmosphere.y);
        pipeline.getUniformSafe("DustColor").setVector(dust);
        pipeline.getUniformSafe("FogColor").setVector(fog);
        Vec3 sunOffset = PortDeadSunEvents.sunOffset();
        double dx = position.x - config.deadSunX;
        double dz = position.z - config.deadSunZ;
        float distanceScale = 1.0F + Math.min(7.0F, (float)Math.sqrt(dx * dx + dz * dz) / 1200.0F);
        // Keep the astronomical body camera-relative on the GPU.  This avoids
        // losing sub-pixel precision when the maze is far from the world origin;
        // the direction still comes from its fixed world-space coordinate.
        pipeline.getUniformSafe("DeadSunPosition").setVector(
                (float)(config.deadSunX + sunOffset.x - position.x),
                (float)(config.deadSunHeight + sunOffset.y - position.y),
                (float)(config.deadSunZ + sunOffset.z - position.z));
        pipeline.getUniformSafe("DeadSunData").setVector(
                config.deadSunSize * distanceScale * mix(1.0F, 1.08F, eclipse),
                config.deadSunBrightness * mix(1.0F, 0.95F, eclipse),
                config.deadSunCorona * mix(1.0F, 2.15F, eclipse), config.deadSunOpacity);
        pipeline.getUniformSafe("DeadSunDensity").setFloat(config.deadSunDensity * mix(1.0F, 1.55F, eclipse));
        pipeline.getUniformSafe("DeadSunCoreColor").setVector(config.deadSunCoreR, config.deadSunCoreG, config.deadSunCoreB);
        pipeline.getUniformSafe("DeadSunCoronaColor").setVector(config.deadSunCoronaR,
                config.deadSunCoronaG, config.deadSunCoronaB);
        pipeline.getUniformSafe("AnimationData").setVector(
                (float)((System.nanoTime() * 1.0E-9D % 100000.0D) * 20.0D),
                config.shaderAnimationSpeed, Math.max(0, Math.min(2, config.cinematicQuality)), eclipse);
    }

    public static void setBiome(int biome) {
        biomeTarget = Mth.clamp(biome, 0, 4);
    }

    /** Smoothly restores the authored atmosphere for each vertical/biome zone. */
    private static void tickBiomeAtmosphere(Minecraft client) {
        boolean inside = client.level != null && client.player != null
                && client.level.dimension().equals(Asterion.ASTERION_LEVEL);
        if (!inside) {
            biomeTarget = 0;
            catacombBlend = approach(catacombBlend, 0.0F, .12F);
            arenaBlend = approach(arenaBlend, 0.0F, .12F);
            caveBlend = approach(caveBlend, 0.0F, .12F);
            forgeBlend = approach(forgeBlend, 0.0F, .12F);
            floodBlend = approach(floodBlend, 0.0F, .12F);
        } else {
            Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
            double cameraY = camera.y;
            float caveTarget = Mth.clamp((float)(LabyrinthLevels.CAVE_ROOF_Y - cameraY) / 12.0F,
                    0.0F, 1.0F);
            caveBlend = approach(caveBlend, caveTarget, .04F);

            float arenaTarget = Math.abs(camera.x) <= AuthoredCatacombs.ARENA_RADIUS
                    && Math.abs(camera.z) <= AuthoredCatacombs.ARENA_RADIUS
                    && cameraY >= AuthoredCatacombs.ARENA_BASE_Y
                    && cameraY < AuthoredCatacombs.ARENA_BASE_Y + 47 ? 1.0F : 0.0F;
            arenaBlend = approach(arenaBlend, arenaTarget, .08F);

            int vaultRoof = Math.min(LabyrinthLevels.MAZE_FLOOR_Y - 2,
                    CatacombLayout.roofAt(client.player.getBlockX(), client.player.getBlockZ()));
            float underground = Mth.clamp((float)(vaultRoof + 3 - cameraY) / 4.0F, 0.0F, 1.0F)
                    * Mth.clamp((float)(cameraY - LabyrinthLevels.FORGE_ROOF_Y), 0.0F, 1.0F);
            if (biomeTarget == 3) underground = 1.0F;
            catacombBlend = approach(catacombBlend, underground, .04F);
            forgeBlend = approach(forgeBlend, biomeTarget == 4 ? 1.0F - caveTarget : 0.0F, .04F);
            floodBlend = approach(floodBlend, PortDeadSunEvents.floodStrength(), .025F);
        }
        overgrowthBlend = approach(overgrowthBlend, biomeTarget == 1 ? 1.0F : 0.0F, .026F);
        crimsonBlend = approach(crimsonBlend, biomeTarget == 2 ? 1.0F : 0.0F, .026F);
    }

    private static Vector3f atmosphereSettings(AsterionConfig config) {
        float eclipse = Mth.clamp(PortDeadSunEvents.eclipse(), 0.0F, 1.0F);
        float dust = config.dustDensity * mix(mix(1.0F, .82F, overgrowthBlend)
                * mix(1.0F, .92F, crimsonBlend)
                * mix(1.0F, 1.18F, forgeBlend)
                * mix(1.0F, 2.80F, eclipse)
                * mix(1.0F, 1.40F + 2.80F * floodBlend * (1.0F - arenaBlend), catacombBlend)
                * mix(1.0F, 3.20F, caveBlend), .45F, arenaBlend);
        float fog = config.fogStrength * mix(mix(1.0F, .90F, overgrowthBlend)
                * mix(1.0F, .94F, crimsonBlend)
                * mix(1.0F, 1.12F, forgeBlend)
                * mix(1.0F, 2.25F, eclipse)
                * mix(1.0F, 1.10F + .40F * floodBlend * (1.0F - arenaBlend), catacombBlend)
                * mix(1.0F, 2.10F, caveBlend), .38F, arenaBlend);
        return new Vector3f(dust, fog, config.shaderAnimationSpeed);
    }

    private static Vector3f dustColor(AsterionConfig config) {
        float eclipse = Mth.clamp(PortDeadSunEvents.eclipse(), 0.0F, 1.0F);
        float red = mix(mix(mix(config.dustR, .43F, overgrowthBlend), .50F, crimsonBlend), .92F, forgeBlend);
        float green = mix(mix(mix(config.dustG, .46F, overgrowthBlend), .70F, crimsonBlend), .34F, forgeBlend);
        float blue = mix(mix(mix(config.dustB, .40F, overgrowthBlend), .84F, crimsonBlend), .12F, forgeBlend);
        return new Vector3f(
                mix(mix(mix(mix(red, .15F, eclipse), .70F, catacombBlend), .82F, arenaBlend), .025F, caveBlend),
                mix(mix(mix(mix(green, .018F, eclipse), .81F, catacombBlend), .52F, arenaBlend), .028F, caveBlend),
                mix(mix(mix(mix(blue, .012F, eclipse), .90F, catacombBlend), .25F, arenaBlend), .032F, caveBlend));
    }

    private static Vector3f fogColor(AsterionConfig config) {
        float eclipse = Mth.clamp(PortDeadSunEvents.eclipse(), 0.0F, 1.0F);
        float red = mix(mix(mix(config.fogR, .20F, overgrowthBlend), .20F, crimsonBlend), .22F, forgeBlend);
        float green = mix(mix(mix(config.fogG, .235F, overgrowthBlend), .34F, crimsonBlend), .085F, forgeBlend);
        float blue = mix(mix(mix(config.fogB, .205F, overgrowthBlend), .43F, crimsonBlend), .045F, forgeBlend);
        return new Vector3f(
                mix(mix(mix(mix(red, .018F, eclipse), .64F, catacombBlend), .20F, arenaBlend), .003F, caveBlend),
                mix(mix(mix(mix(green, .003F, eclipse), .76F, catacombBlend), .13F, arenaBlend), .004F, caveBlend),
                mix(mix(mix(mix(blue, .002F, eclipse), .86F, catacombBlend), .085F, arenaBlend), .006F, caveBlend));
    }

    private static float approach(float value, float target, float speed) {
        float result = value + (target - value) * speed;
        return Math.abs(target - result) < .001F ? target : result;
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
