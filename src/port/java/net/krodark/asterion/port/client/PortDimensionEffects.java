package net.krodark.asterion.port.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
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

        PostPipeline pipeline = manager.getPipeline(PIPELINE);
        if (pipeline == null) return;
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 position = camera.getPosition();
        Vector3f forward = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        float aspect = Math.max(1, client.getWindow().getWidth())
                / (float)Math.max(1, client.getWindow().getHeight());
        float fov = (float)Math.toRadians(client.options.fov().get());
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
                config.dustDensity * (1.0F + eclipse * 1.8F), config.fogStrength * (1.0F + eclipse));
        pipeline.getUniformSafe("DustColor").setVector(config.dustR * (1.0F - eclipse * 0.42F),
                config.dustG * (1.0F - eclipse * 0.78F), config.dustB * (1.0F - eclipse * 0.78F));
        pipeline.getUniformSafe("FogColor").setVector(config.fogR * (1.0F - eclipse * 0.62F),
                config.fogG * (1.0F - eclipse * 0.88F), config.fogB * (1.0F - eclipse * 0.88F));
        Vec3 sunOffset = PortDeadSunEvents.sunOffset();
        pipeline.getUniformSafe("DeadSunPosition").setVector(config.deadSunX + (float)sunOffset.x,
                config.deadSunHeight + (float)sunOffset.y, config.deadSunZ + (float)sunOffset.z);
        pipeline.getUniformSafe("DeadSunData").setVector(config.deadSunSize, config.deadSunBrightness,
                config.deadSunCorona, config.deadSunOpacity);
        pipeline.getUniformSafe("DeadSunCoreColor").setVector(config.deadSunCoreR, config.deadSunCoreG, config.deadSunCoreB);
        pipeline.getUniformSafe("DeadSunCoronaColor").setVector(config.deadSunCoronaR,
                config.deadSunCoronaG, config.deadSunCoronaB);
        pipeline.getUniformSafe("AnimationData").setVector(
                (float)(System.nanoTime() * 1.0E-9D),
                config.shaderAnimationSpeed, Math.max(0, Math.min(2, config.cinematicQuality)), 0.0F);
    }
}
