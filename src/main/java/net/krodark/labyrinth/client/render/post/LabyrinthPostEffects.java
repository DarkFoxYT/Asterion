package net.krodark.labyrinth.client.render.post;

import com.meekdev.amnetic.client.post.PostEffects;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.labyrinth.Labyrinth;
import net.krodark.labyrinth.LabyrinthConfig;
import net.krodark.labyrinth.client.event.DeadSunClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public final class LabyrinthPostEffects {
    private LabyrinthPostEffects() {
    }

    public static void register() {
        PostEffects.register(Labyrinth.id("dimension/dead_sun"), config -> config
                .when(() -> isInsideLabyrinth() && LabyrinthConfig.INSTANCE.deadSunEnabled)
                .phase(RenderPhase.POST_WORLD)
                .priority(10)
                .fade(32, 16)
                .uniform("DustTime", LabyrinthPostEffects::renderTime)
                .uniform("LabyrinthStrength", () -> LabyrinthConfig.INSTANCE.deadSunStrength)
                .uniformRaw("WorldData", LabyrinthPostEffects::worldData)
                .uniformVec4("DeadSunData", LabyrinthPostEffects::deadSunData)
                .uniformVec4("DeadSunTuning", LabyrinthPostEffects::deadSunTuning)
                .uniform("EclipseData", DeadSunClientEvents::eclipseStrength)
                .uniform("DeadSunOpacity", () -> LabyrinthConfig.INSTANCE.deadSunOpacity)
                .uniformVec3("DeadSunCoreColor", () -> new Vector3f(LabyrinthConfig.INSTANCE.deadSunCoreR,
                        LabyrinthConfig.INSTANCE.deadSunCoreG, LabyrinthConfig.INSTANCE.deadSunCoreB))
                .uniformVec3("DeadSunCoronaColor", () -> new Vector3f(LabyrinthConfig.INSTANCE.deadSunCoronaR,
                        LabyrinthConfig.INSTANCE.deadSunCoronaG, LabyrinthConfig.INSTANCE.deadSunCoronaB)));

        PostEffects.register(Labyrinth.id("dimension/dusty_air"), config -> config
                .when(() -> isInsideLabyrinth() && LabyrinthConfig.INSTANCE.dustyAirEnabled)
                .phase(RenderPhase.POST_WORLD)
                .priority(20)
                .fade(24, 16)
                .uniform("DustTime", LabyrinthPostEffects::renderTime)
                .uniform("LabyrinthStrength", () -> LabyrinthConfig.INSTANCE.dustyAirStrength)
                .uniformVec3("AtmosphereSettings", LabyrinthPostEffects::atmosphereSettings)
                .uniformVec3("DustColor", LabyrinthPostEffects::dustColor)
                .uniformVec3("FogColor", LabyrinthPostEffects::fogColor)
                .uniformRaw("WorldData", LabyrinthPostEffects::worldData));
    }

    private static boolean isInsideLabyrinth() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL);
    }

    private static double renderTime() {
        return (System.nanoTime() * 0.000000001 % 100000.0) * 20.0;
    }

    private static Vector4f deadSunData() {
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        Vec3 shake = DeadSunClientEvents.sunOffset();
        return new Vector4f(config.deadSunX + (float) shake.x,
                config.deadSunHeight + (float) shake.y,
                config.deadSunZ + (float) shake.z, config.deadSunSize * mix(1.0F, 1.08F, eclipse()));
    }

    private static Vector4f deadSunTuning() {
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        float eclipse = eclipse();
        return new Vector4f(
                config.deadSunBrightness * mix(1.0F, 0.58F, eclipse),
                config.shaderAnimationSpeed * mix(1.0F, 0.72F, eclipse),
                config.deadSunCorona * mix(1.0F, 2.15F, eclipse),
                config.deadSunDensity * mix(1.0F, 1.55F, eclipse));
    }

    private static Vector3f atmosphereSettings() {
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        float eclipse = eclipse();
        return new Vector3f(
                config.dustDensity * mix(1.0F, 2.20F, eclipse),
                config.fogStrength * mix(1.0F, 1.85F, eclipse),
                // Never alter the time domain during an Eclipse. Multiplying an accumulated
                // clock by a changing speed makes the entire noise field visibly jump.
                config.shaderAnimationSpeed);
    }

    private static Vector3f dustColor() {
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        float eclipse = eclipse();
        return new Vector3f(
                mix(config.dustR, 0.15F, eclipse),
                mix(config.dustG, 0.018F, eclipse),
                mix(config.dustB, 0.012F, eclipse));
    }

    private static Vector3f fogColor() {
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        float eclipse = eclipse();
        return new Vector3f(
                mix(config.fogR, 0.018F, eclipse),
                mix(config.fogG, 0.003F, eclipse),
                mix(config.fogB, 0.002F, eclipse));
    }

    private static float eclipse() {
        return Math.max(0.0F, Math.min(1.0F, DeadSunClientEvents.eclipseStrength()));
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
