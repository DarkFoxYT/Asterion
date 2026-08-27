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

/** Registers dimension-specific post effects through Amnetic's post pipeline. */
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
                .uniformVec4("DeadSunTuning", () -> new Vector4f(LabyrinthConfig.INSTANCE.deadSunBrightness,
                        LabyrinthConfig.INSTANCE.shaderAnimationSpeed, LabyrinthConfig.INSTANCE.deadSunCorona,
                        LabyrinthConfig.INSTANCE.deadSunDensity))
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
                .uniformVec3("AtmosphereSettings", () -> new Vector3f(LabyrinthConfig.INSTANCE.dustDensity,
                        LabyrinthConfig.INSTANCE.fogStrength, LabyrinthConfig.INSTANCE.shaderAnimationSpeed))
                .uniformVec3("DustColor", () -> new Vector3f(LabyrinthConfig.INSTANCE.dustR,
                        LabyrinthConfig.INSTANCE.dustG, LabyrinthConfig.INSTANCE.dustB))
                .uniformVec3("FogColor", () -> new Vector3f(LabyrinthConfig.INSTANCE.fogR,
                        LabyrinthConfig.INSTANCE.fogG, LabyrinthConfig.INSTANCE.fogB))
                .uniformRaw("WorldData", LabyrinthPostEffects::worldData));
    }

    private static boolean isInsideLabyrinth() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL);
    }

    private static double renderTime() {
        // A monotonic high-resolution clock avoids tick-boundary resets and partial-tick jitter.
        return (System.nanoTime() * 0.000000001 % 100000.0) * 20.0;
    }

    private static Vector4f deadSunData() {
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        Vec3 shake = DeadSunClientEvents.sunOffset();
        return new Vector4f(config.deadSunX + (float) shake.x,
                config.deadSunHeight + (float) shake.y,
                config.deadSunZ + (float) shake.z, config.deadSunSize);
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
