package net.krodark.asterion.update.underworld.client;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.post.PostEffects;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
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
        PostEffects.register(Asterion.id("underworld/river_atmosphere"), config -> net.krodark.asterion.client.render.post.AmneticPostBuffers.attach(config, "underworld_mist", .50F)
                .when(UnderworldPostEffects::active)
                .phase(RenderPhase.POST_WORLD).priority(18).fade(8, 0)
                .texture("Noise", Asterion.id("textures/effect/underworld_fog_atlas.png"))
                .uniform("UnderworldTime", UnderworldPostEffects::time)
                .uniformRaw("WorldData", UnderworldPostEffects::worldData)
                .uniformVec4("PresenceData", UnderworldPostEffects::presenceData)
                .uniformVec4("PresenceMotion", UnderworldPostEffects::presenceMotion)
                .uniformVec4("RiverData", () -> new Vector4f(
                        UnderworldTerrain.WATER_Y + .38F, 2.65F,
                        active() ? AsterionConfig.INSTANCE.limboFogStrength : 0F,
                        active() ? AsterionConfig.INSTANCE.limboMistStrength : 0F)));
    }

    private static boolean active() {
        Minecraft client = Minecraft.getInstance();
        boolean limbo = client.level != null && client.level.dimension().equals(Asterion.LIMBO_LEVEL);
        return limbo && !ShaderPackCompatibility.active() && AmneticCamera.isReady();
    }

    private static double time() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? 0 : client.level.getGameTime()
                + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
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
                        (float)cameraForward.z, (float)UnderworldTerrain.waveHeight(
                                cameraPosition.x, cameraPosition.z, time()))));
    }

    private static Vector4f presenceData() {
        CharonsFerryEntity ferry = ferry();
        return ferry == null ? new Vector4f(0F) : new Vector4f(
                (float)ferry.getX(), (float)ferry.getY(), (float)ferry.getZ(), 1F);
    }

    private static Vector4f presenceMotion() {
        Minecraft client = Minecraft.getInstance();
        Vec3 playerMotion = client.player == null ? Vec3.ZERO : client.player.getDeltaMovement();
        CharonsFerryEntity ferry = ferry();
        Vec3 ferryMotion = ferry == null ? Vec3.ZERO : ferry.getDeltaMovement();
        return new Vector4f((float)playerMotion.x, (float)playerMotion.z,
                (float)ferryMotion.x, (float)ferryMotion.z);
    }

    private static CharonsFerryEntity ferry() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof CharonsFerryEntity ferry) return ferry;
        return null;
    }
}
