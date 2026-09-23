package net.krodark.asterion.update.underworld.client;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.post.PostEffects;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.client.Minecraft;
import com.meekdev.amnetic.client.post.UniformValue;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/** Depth-tested low mist; the animated water surface has its own geometry pass. */
public final class UnderworldPostEffects {
    private static final Matrix4f inverseViewProjection = new Matrix4f();
    private static Vec3 cameraPosition = Vec3.ZERO;
    private static Vec3 cameraForward = new Vec3(0, 0, 1);

    private UnderworldPostEffects() { }

    public static void register() {
        // Retain all three volumetric layers even on low quality; scale pixels and samples instead.
        PostEffects.register(Asterion.id("underworld/river_atmosphere"), config -> net.krodark.asterion.client.render.post.AmneticPostBuffers.attach(withIntensity(config), "underworld_mist",
                        () -> switch (net.krodark.asterion.client.PerformanceGovernor.quality()) {
                            case 0 -> .38; case 1 -> .50; default -> .64;
                        })
                .when(UnderworldPostEffects::active)
                .uniformVec4("MistQuality", () -> switch (net.krodark.asterion.client.PerformanceGovernor.quality()) {
                    case 0 -> new Vector4f(4, 5, 10, 0);
                    case 1 -> new Vector4f(5, 6, 14, 0);
                    default -> new Vector4f(7, 8, 16, 0);
                })
                .phase(RenderPhase.POST_WORLD).priority(18).fade(0, 0)
                .texture("Noise", Asterion.id("textures/effect/underworld_fog_atlas.png"))
                .uniformRaw("WorldData", UnderworldPostEffects::worldData)
                .uniformVec4("RiverData", () -> new Vector4f(
                        UnderworldTerrain.WATER_Y + .38F, 2.65F,
                        active() ? AsterionConfig.INSTANCE.limboFogStrength : 0F,
                        active() ? AsterionConfig.INSTANCE.limboMistStrength : 0F)));
    }

    private static com.meekdev.amnetic.client.post.PostEffectConfig withIntensity(
            com.meekdev.amnetic.client.post.PostEffectConfig config) {
        // Amnetic only auto-binds Intensity for nonzero fades. This effect uses fade(0, 0).
        return config.uniform("Intensity", () -> 1.0);
    }

    private static boolean active() {
        Minecraft client = Minecraft.getInstance();
        boolean limbo = client.level != null && client.level.dimension().equals(Asterion.LIMBO_LEVEL);
        return limbo && !ShaderPackCompatibility.active() && AmneticCamera.isReady();
    }

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
