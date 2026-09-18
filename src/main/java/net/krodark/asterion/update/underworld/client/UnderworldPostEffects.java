package net.krodark.asterion.update.underworld.client;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.post.PostEffects;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.PerformanceGovernor;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/** Depth-tested low mist. Water itself is rendered exclusively by Minecraft or the shader pack. */
public final class UnderworldPostEffects {
    private static final Matrix4f inverseViewProjection = new Matrix4f();
    private static Vec3 cameraPosition = Vec3.ZERO;
    private static Vec3 cameraForward = new Vec3(0, 0, 1);

    private UnderworldPostEffects() { }

    public static void register() {
        PostEffects.register(Asterion.id("underworld/river_atmosphere"), config -> config
                .when(() -> active() && AsterionConfig.INSTANCE.cinematicQuality > 0
                        && PerformanceGovernor.quality() > 0)
                .phase(RenderPhase.POST_WORLD).priority(18).fade(20, 14)
                .uniform("UnderworldTime", UnderworldPostEffects::time)
                .uniformRaw("WorldData", UnderworldPostEffects::worldData)
                .uniformVec4("RiverData", () -> new Vector4f(
                        UnderworldTerrain.WATER_Y + 1.15F, 2.2F,
                        active() ? AsterionConfig.INSTANCE.limboFogStrength : 0F,
                        active() ? AsterionConfig.INSTANCE.limboMistStrength : 0F)));
        PostEffects.register(Asterion.id("underworld/river_atmosphere_fast"), config -> config
                .when(() -> active() && (AsterionConfig.INSTANCE.cinematicQuality <= 0
                        || PerformanceGovernor.quality() == 0))
                .phase(RenderPhase.POST_WORLD).priority(18).fade(6, 8)
                .uniform("UnderworldTime", UnderworldPostEffects::time)
                .uniformRaw("WorldData", UnderworldPostEffects::worldData)
                .uniformVec4("RiverData", () -> new Vector4f(
                        UnderworldTerrain.WATER_Y + 1.15F, 2.2F,
                        active() ? AsterionConfig.INSTANCE.limboFogStrength : 0F,
                        active() ? AsterionConfig.INSTANCE.limboMistStrength : 0F)));
    }

    private static boolean active() {
        Minecraft client = Minecraft.getInstance();
        boolean limbo = client.level != null && client.level.dimension().equals(Asterion.LIMBO_LEVEL);
        return limbo && !ShaderPackCompatibility.active() && AmneticCamera.isReady();
    }

    private static double time() { return (System.nanoTime() * 0.000000001 % 100000.0) * 20.0; }

    private static List<UniformValue> worldData() {
        if (AmneticCamera.isReady()) {
            inverseViewProjection.set(AmneticCamera.inverseViewProjection());
            cameraPosition = AmneticCamera.position();
            cameraForward = AmneticCamera.forward();
        }
        return List.of(
                new UniformValue.Matrix4x4Uniform(new Matrix4f(inverseViewProjection)),
                new UniformValue.Vec4Uniform(new Vector4f((float)cameraPosition.x, (float)cameraPosition.y,
                        (float)cameraPosition.z, RenderSystem.getDevice().isZZeroToOne() ? 1F : 0F)),
                new UniformValue.Vec4Uniform(new Vector4f((float)cameraForward.x, (float)cameraForward.y,
                        (float)cameraForward.z, 0F)));
    }
}
