package net.krodark.asterion.port.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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

    private PortDimensionEffects() {}

    public static void initialize() {
        // A client tick is only 20 Hz.  Camera data sampled there visibly trails a
        // high-refresh-rate camera, making the atmosphere appear screen-locked.
        // Refresh it after the translucent world pass on every rendered frame.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> updateFrame(Minecraft.getInstance()));
    }

    public static void tick(Minecraft client) {
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

        pipeline.getUniformSafe("CameraPosition").setVector((float)position.x, (float)position.y, (float)position.z);
        pipeline.getUniformSafe("CameraForward").setVector(forward);
        pipeline.getUniformSafe("CameraUp").setVector(up);
        pipeline.getUniformSafe("CameraRight").setVector(-left.x, -left.y, -left.z);
        pipeline.getUniformSafe("ProjectionData").setVector((float)Math.tan(fov * 0.5F), aspect, 0.05F, far);
        float eclipse = PortDeadSunEvents.eclipse();
        pipeline.getUniformSafe("EffectData").setVector(
                config.dustyAirEnabled ? config.dustyAirStrength : 0.0F,
                config.deadSunEnabled ? config.deadSunStrength : 0.0F,
                config.dustDensity * mix(1.0F, 2.80F, eclipse),
                config.fogStrength * mix(1.0F, 2.25F, eclipse));
        pipeline.getUniformSafe("DustColor").setVector(mix(config.dustR, 0.15F, eclipse),
                mix(config.dustG, 0.018F, eclipse), mix(config.dustB, 0.012F, eclipse));
        pipeline.getUniformSafe("FogColor").setVector(mix(config.fogR, 0.018F, eclipse),
                mix(config.fogG, 0.003F, eclipse), mix(config.fogB, 0.002F, eclipse));
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

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
