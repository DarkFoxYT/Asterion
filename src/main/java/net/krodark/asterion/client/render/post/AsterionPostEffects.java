package net.krodark.asterion.client.render.post;

import com.meekdev.amnetic.client.post.PostEffects;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.krodark.asterion.client.DeadSunEntryCinematic;
import net.krodark.asterion.client.BossFinaleOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public final class AsterionPostEffects {
    private AsterionPostEffects() {
    }

    public static void register() {
        PostEffects.register(Asterion.id("dimension/dead_sun"), config -> config
                .when(() -> isPostProcessingReady() && AsterionConfig.INSTANCE.deadSunEnabled)
                .phase(RenderPhase.POST_WORLD)
                .priority(10)
                .fade(32, 16)
                .uniform("DustTime", AsterionPostEffects::renderTime)
                .uniform("AsterionStrength", () -> AsterionConfig.INSTANCE.deadSunStrength)
                .uniform("AsterionQuality", AsterionPostEffects::effectQuality)
                .uniformRaw("WorldData", AsterionPostEffects::worldData)
                .uniformVec4("DeadSunData", AsterionPostEffects::deadSunData)
                .uniformVec4("DeadSunTuning", AsterionPostEffects::deadSunTuning)
                .uniform("EclipseData", DeadSunClientEvents::eclipseStrength)
                .uniform("WorldDarkness", DeadSunClientEvents::darknessStrength)
                .uniform("EntryRadiance", AsterionPostEffects::radiance)
                .uniform("DeadSunOpacity", () -> AsterionConfig.INSTANCE.deadSunOpacity)
                .uniformVec3("DeadSunCoreColor", () -> new Vector3f(AsterionConfig.INSTANCE.deadSunCoreR,
                        AsterionConfig.INSTANCE.deadSunCoreG, AsterionConfig.INSTANCE.deadSunCoreB))
                .uniformVec3("DeadSunCoronaColor", () -> new Vector3f(AsterionConfig.INSTANCE.deadSunCoronaR,
                        AsterionConfig.INSTANCE.deadSunCoronaG, AsterionConfig.INSTANCE.deadSunCoronaB)));

        PostEffects.register(Asterion.id("dimension/dusty_air"), config -> config
                .when(() -> isPostProcessingReady() && AsterionConfig.INSTANCE.dustyAirEnabled)
                .phase(RenderPhase.POST_WORLD)
                .priority(20)
                .fade(24, 16)
                .uniform("DustTime", AsterionPostEffects::renderTime)
                .uniform("AsterionStrength", () -> AsterionConfig.INSTANCE.dustyAirStrength)
                .uniform("AsterionQuality", AsterionPostEffects::effectQuality)
                .uniformVec3("AtmosphereSettings", AsterionPostEffects::atmosphereSettings)
                .uniformVec3("DustColor", AsterionPostEffects::dustColor)
                .uniformVec3("FogColor", AsterionPostEffects::fogColor)
                .uniform("EclipseData", DeadSunClientEvents::eclipseStrength)
                .uniformRaw("WorldData", AsterionPostEffects::worldData));
    }

    private static boolean isInsideAsterion() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Asterion.ASTERION_LEVEL);
    }

    private static boolean isPostProcessingReady() {
        return isInsideAsterion() && AmneticCamera.isReady();
    }

    private static double effectQuality() {
        return AsterionConfig.INSTANCE.cinematicQuality;
    }

    private static double renderTime() {
        return (System.nanoTime() * 0.000000001 % 100000.0) * 20.0;
    }

    private static Vector4f deadSunData() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        Vec3 shake = DeadSunClientEvents.sunOffset();
        Vec3 camera = AmneticCamera.position();
        double dx = camera.x - config.deadSunX;
        double dz = camera.z - config.deadSunZ;
        float distanceScale = 1.0F + Math.min(7.0F,
                (float) Math.sqrt(dx * dx + dz * dz) / 1200.0F);
        return new Vector4f(config.deadSunX + (float) shake.x,
                config.deadSunHeight + (float) shake.y,
                config.deadSunZ + (float) shake.z,
                config.deadSunSize * distanceScale * mix(1.0F, 1.08F, eclipse())
                        * mix(1.0F, 3.8F, BossFinaleOverlay.sunDetonationStrength())
                        * mix(1.0F, 1.16F, DeadSunEntryCinematic.radianceStrength()));
    }

    private static Vector4f deadSunTuning() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        float eclipse = eclipse();
        return new Vector4f(
                config.deadSunBrightness * mix(1.0F, 0.95F, eclipse)
                        * mix(1.0F, 1.18F, radiance()),
                config.shaderAnimationSpeed * mix(1.0F, 0.72F, eclipse),
                config.deadSunCorona * mix(1.0F, 2.15F, eclipse)
                        * mix(1.0F, 1.22F, radiance()),
                config.deadSunDensity * mix(1.0F, 1.55F, eclipse));
    }

    private static Vector3f atmosphereSettings() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        float eclipse = darkness();
        return new Vector3f(
                config.dustDensity * mix(1.0F, 2.80F, eclipse),
                config.fogStrength * mix(1.0F, 2.25F, eclipse),
                config.shaderAnimationSpeed);
    }

    private static Vector3f dustColor() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        float eclipse = darkness();
        return new Vector3f(
                mix(config.dustR, 0.15F, eclipse),
                mix(config.dustG, 0.018F, eclipse),
                mix(config.dustB, 0.012F, eclipse));
    }

    private static Vector3f fogColor() {
        AsterionConfig config = AsterionConfig.INSTANCE;
        float eclipse = darkness();
        return new Vector3f(
                mix(config.fogR, 0.018F, eclipse),
                mix(config.fogG, 0.003F, eclipse),
                mix(config.fogB, 0.002F, eclipse));
    }

    private static float eclipse() {
        return Math.max(0.0F, Math.min(1.0F, DeadSunClientEvents.eclipseStrength()));
    }

    private static float darkness() {
        return Math.max(0.0F, Math.min(1.0F, DeadSunClientEvents.darknessStrength()));
    }

    private static float radiance() {
        return Math.max(DeadSunEntryCinematic.radianceStrength(),
                BossFinaleOverlay.sunDetonationStrength());
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static List<UniformValue> worldData() {
        Matrix4f inverseViewProjection = AmneticCamera.isReady()
                ? AmneticCamera.inverseViewProjection()
                : new Matrix4f();
        Vec3 camera = AmneticCamera.position();
        Vec3 forward = AmneticCamera.forward();
        float zeroToOne = RenderSystem.getDevice().isZZeroToOne() ? 1.0f : 0.0f;
        return List.of(
                new UniformValue.Matrix4x4Uniform(inverseViewProjection),
                new UniformValue.Vec4Uniform(new Vector4f(
                        (float) camera.x, (float) camera.y, (float) camera.z, zeroToOne)),
                new UniformValue.Vec4Uniform(new Vector4f(
                        (float) forward.x, (float) forward.y, (float) forward.z, 0.0f))
        );
    }
}
