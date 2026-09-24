package net.krodark.asterion.update.underworld.client;

import com.meekdev.amnetic.client.camera.AmneticCamera;
import com.meekdev.amnetic.client.post.PostEffects;
import com.meekdev.amnetic.client.post.RenderPhase;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.krodark.asterion.update.underworld.world.UnderworldWaterPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import com.meekdev.amnetic.client.post.UniformValue;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/** Depth-tested, animated Limbo atmosphere; water keeps its own geometry pass. */
public final class UnderworldPostEffects {
    private static final Matrix4f inverseViewProjection = new Matrix4f();
    private static Vec3 cameraPosition = Vec3.ZERO;
    private static Vec3 cameraForward = new Vec3(0, 0, 1);
    private static net.minecraft.client.multiplayer.ClientLevel fogLightLevel;
    private static final FogLight[] fogLights = new FogLight[4];
    private static final class FogLight {
        Vec3 position;
        float radius, strength;
        FogLight(Vec3 position, float radius) { this.position = position; this.radius = radius; }
    }

    private UnderworldPostEffects() { }

    public static void register() {
        // All quality levels retain the same atmosphere; only resolution and ray samples change.
        PostEffects.register(Asterion.id("underworld/river_atmosphere"), config -> net.krodark.asterion.client.render.post.AmneticPostBuffers.attach(withIntensity(config), "underworld_mist",
                        () -> switch (net.krodark.asterion.client.PerformanceGovernor.quality()) {
                            case 0 -> .40; case 1 -> .62; default -> 1.0;
                        })
                .when(UnderworldPostEffects::active)
                .uniformVec4("MistQuality", () -> switch (net.krodark.asterion.client.PerformanceGovernor.quality()) {
                    case 0 -> new Vector4f(4, 0, 0, 0);
                    case 1 -> new Vector4f(6, 0, 0, 0);
                    default -> new Vector4f(8, 0, 0, 0);
                })
                .phase(RenderPhase.POST_WORLD).priority(18).fade(0, 0)
                .uniform("UnderworldTime", UnderworldPostEffects::renderTime)
                .uniformVec4("Submersion", UnderworldPostEffects::submersion)
                .uniformRaw("WorldData", UnderworldPostEffects::worldData)
                .uniformRaw("LocalLights", UnderworldPostEffects::localLights)
                .uniformVec4("RiverData", () -> new Vector4f(
                        UnderworldTerrain.WATER_Y + 8F / 9F, 2.65F,
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

    private static List<UniformValue> localLights() {
        Minecraft client = Minecraft.getInstance();
        Vec3 camera = AmneticCamera.isReady() ? AmneticCamera.position() : cameraPosition;
        if (fogLightLevel != client.level) {
            java.util.Arrays.fill(fogLights, null);
            fogLightLevel = client.level;
        }
        java.util.ArrayList<UniformValue> values = new java.util.ArrayList<>(8);
        java.util.ArrayList<LedAmneticLight.LedPointLightSample> visible = new java.util.ArrayList<>(8);
        if (client.level != null) for (var light : LedAmneticLight.nearbyFogLights(camera, 12)) {
            if (visible.size() >= 8) break;
            if (camera.distanceToSqr(light.position()) > 2 && client.level.clip(new ClipContext(
                    camera, light.position(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty())).getType() != HitResult.Type.MISS) continue;
            visible.add(light);
        }
        boolean[] used = new boolean[visible.size()];
        for (int i = 0; i < 4; i++) {
            FogLight slot = fogLights[i];
            int match = -1;
            double best = 100;
            if (slot != null) for (int j = 0; j < visible.size(); j++) if (!used[j]) {
                double distance = slot.position.distanceToSqr(visible.get(j).position());
                if (distance < best) { best = distance; match = j; }
            }
            if (match < 0 && (slot == null || slot.strength < .08F))
                for (int j = 0; j < visible.size(); j++) if (!used[j]) { match = j; break; }
            if (match >= 0) {
                used[match] = true;
                var light = visible.get(match);
                if (slot == null) fogLights[i] = slot = new FogLight(light.position(), light.radius());
                slot.position = slot.position.lerp(light.position(), .22);
                slot.radius += (Math.max(2.5F, light.radius() * 2.25F) - slot.radius) * .18F;
                float brightest = Math.max(light.red(), Math.max(light.green(), light.blue()));
                float dimmest = Math.min(light.red(), Math.min(light.green(), light.blue()));
                float saturation = brightest <= .001F ? 0F : Math.clamp((brightest - dimmest) / brightest, 0F, 1F);
                float target = Math.min(1.5F, light.strength()) * (1F - saturation * .42F);
                slot.strength += (target - slot.strength) * .16F;
            } else if (slot != null) {
                slot.strength *= .82F;
                if (slot.strength < .01F) fogLights[i] = slot = null;
            }
        }
        for (int i = 0; i < 4; i++) {
            FogLight slot = fogLights[i];
            values.add(new UniformValue.Vec4Uniform(slot == null ? new Vector4f()
                    : new Vector4f((float)slot.position.x, (float)slot.position.y,
                    (float)slot.position.z, slot.radius)));
        }
        for (int i = 0; i < 4; i++) values.add(new UniformValue.Vec4Uniform(
                new Vector4f(0, 0, 0, fogLights[i] == null ? 0 : fogLights[i].strength)));
        return values;
    }

    private static double renderTime() {
        return (System.nanoTime() * 0.000000001 % 100000.0) * 20.0;
    }

    private static Vector4f submersion() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !AmneticCamera.isReady())
            return new Vector4f();
        if (client.player.getVehicle() instanceof net.krodark.asterion.update.underworld.entity.CharonsFerryEntity
                || net.krodark.asterion.update.underworld.entity.CharonsFerryEntity.supporting(client.player) != null)
            return new Vector4f();
        if (!client.player.isInWater() && (client.player.getY() < UnderworldTerrain.WATER_Y - 2
                || client.player.getY() > UnderworldTerrain.WATER_Y + 4
                || client.level.getBlockState(client.player.blockPosition().below()).isSolidRender()))
            return new Vector4f();
        double ticks = client.level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double surface = UnderworldWaterPhysics.surfaceAt(client.player, ticks);
        if (!Double.isFinite(surface)) return new Vector4f();
        float amount = (float)Math.clamp((surface - AmneticCamera.position().y + .1) * 1.3, 0, 1);
        return new Vector4f(amount, (float)surface, 0, 0);
    }
}
