package net.krodark.asterion.client.lightning;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.Lights;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.BossFinaleOverlay;
import net.krodark.asterion.client.DeadSunEntryCinematic;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.network.MazeZapPayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class MazeZapRenderer {
    private static final List<Strike> STRIKES = new ArrayList<>();
    private static final List<GroundStrike> GROUND_STRIKES = new ArrayList<>();
    private static final List<Telegraph> TELEGRAPHS = new ArrayList<>();

    private MazeZapRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MazeZapRenderer::tick);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            long now = client.level.getGameTime();
            Iterator<Strike> iterator = STRIKES.iterator();
            while (iterator.hasNext()) {
                Strike strike = iterator.next();
                if (now > strike.expiresAt) {
                    strike.removeLights();
                    iterator.remove();
                    continue;
                }
                Entity target = client.level.getEntity(strike.entityId);
                if (target == null) continue;
                Vec3 body = bodyCenter(target);
                float charge = Math.min(1.0F, (strike.expiresAt - now + 1) / 4.0F);
                var lightning = context.bufferSource().getBuffer(RenderTypes.lightning());
                if (now - strike.createdAt <= 10L) {
                    BetterLightningRenderer.draw(lightning, context.poseStack().last(), Vec3.ZERO,
                            strike.source.subtract(camera), body.subtract(camera), charge, now / 2L);
                }
                strike.drawBodyArcs(lightning, context.poseStack().last(), camera, target, body, charge, now);
            }
            var lightning = context.bufferSource().getBuffer(RenderTypes.lightning());
            drawDeadSunFractures(client, lightning, context.poseStack().last(), camera, now);
            for (GroundStrike strike : GROUND_STRIKES) {
                if (now < strike.startsAt || now > strike.expiresAt) continue;
                float life = 1.0F - (now - strike.startsAt) / (float)Math.max(1L, strike.expiresAt - strike.startsAt);
                Vec3 source = new Vec3(AsterionConfig.INSTANCE.deadSunX,
                        AsterionConfig.INSTANCE.deadSunHeight, AsterionConfig.INSTANCE.deadSunZ);
                Vec3 target = Vec3.atCenterOf(strike.target);
                BetterLightningRenderer.draw(lightning, context.poseStack().last(), Vec3.ZERO,
                        source.subtract(camera), target.subtract(camera), 0.72F + life * 0.28F,
                        strike.seed + (now - strike.startsAt) / 2L);
                if (AsterionConfig.INSTANCE.cinematicQuality >= 2)
                    for (int branch = 0; branch < 2; branch++) {
                        double angle = branch * Math.PI + strike.seed * 0.000013D;
                        Vec3 fork = target.add(Math.cos(angle) * (2.5D + branch),
                                0.6D + branch * 0.8D, Math.sin(angle) * (2.5D + branch));
                        BetterLightningRenderer.draw(lightning, context.poseStack().last(), Vec3.ZERO,
                                source.subtract(camera), fork.subtract(camera), life,
                                strike.seed + branch * 7919L + now / 3L);
                    }
            }
            for (Telegraph telegraph : TELEGRAPHS) {
                if (now > telegraph.expiresAt) continue;
                float pulse = 0.55F + 0.25F * (float)Math.sin(now * 0.55D);
                Vec3 forward = new Vec3(telegraph.direction.x, 0.0D, telegraph.direction.z).normalize();
                if (forward.lengthSqr() < 0.01D) forward = new Vec3(0.0D, 0.0D, 1.0D);
                double baseAngle = Math.atan2(forward.z, forward.x);
                if (telegraph.kind == BossTelegraphPayload.CHARGE_LANE) {
                    Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 a = telegraph.center.add(right.scale(side * 2.25D)).add(0, 0.08D, 0);
                        Vec3 b = a.add(forward.scale(telegraph.radius));
                        BetterLightningRenderer.draw(lightning, context.poseStack().last(), Vec3.ZERO,
                                a.subtract(camera), b.subtract(camera), pulse,
                                telegraph.seed + side * 977L);
                    }
                    for (int rung = 2; rung <= 8; rung += 2) {
                        Vec3 middle = telegraph.center.add(forward.scale(telegraph.radius * rung / 8.0D))
                                .add(0, 0.075D, 0);
                        BetterLightningRenderer.draw(lightning, context.poseStack().last(), Vec3.ZERO,
                                middle.add(right.scale(-2.25D)).subtract(camera),
                                middle.add(right.scale(2.25D)).subtract(camera), pulse * 0.72F,
                                telegraph.seed + rung * 313L);
                    }
                    continue;
                }
                double arc = telegraph.kind == BossTelegraphPayload.TARGET_CIRCLE
                        ? Mth.TWO_PI : telegraph.kind == BossTelegraphPayload.FRONT_CONE
                        ? Math.toRadians(125.0D) : Math.PI;
                double startAngle = telegraph.kind == BossTelegraphPayload.TARGET_CIRCLE
                        ? 0.0D : baseAngle - arc * 0.5D;
                int segments = telegraph.kind == BossTelegraphPayload.TARGET_CIRCLE ? 24 : 18;
                int rays = telegraph.kind == BossTelegraphPayload.TARGET_CIRCLE ? 8
                        : AsterionConfig.INSTANCE.cinematicQuality >= 2 ? 13 : 8;
                for (int ray = 0; ray < rays; ray++) {
                    double angle = startAngle + arc * ray / Math.max(1.0D, rays - 1.0D);
                    Vec3 end = telegraph.center.add(Math.cos(angle) * telegraph.radius, 0.07D,
                            Math.sin(angle) * telegraph.radius);
                    BetterLightningRenderer.draw(lightning, context.poseStack().last(), Vec3.ZERO,
                            telegraph.center.add(0, 0.07D, 0).subtract(camera), end.subtract(camera),
                            pulse * 0.78F, telegraph.seed + ray * 977L);
                }
                for (int segment = 0; segment < segments; segment++) {
                    double a0 = startAngle + arc * segment / segments;
                    double a1 = startAngle + arc * (segment + 1) / segments;
                    Vec3 a = telegraph.center.add(Math.cos(a0) * telegraph.radius, 0.08D,
                            Math.sin(a0) * telegraph.radius);
                    Vec3 b = telegraph.center.add(Math.cos(a1) * telegraph.radius, 0.08D,
                            Math.sin(a1) * telegraph.radius);
                    BetterLightningRenderer.draw(lightning, context.poseStack().last(), Vec3.ZERO,
                            a.subtract(camera), b.subtract(camera), pulse, telegraph.seed + segment * 131L);
                }
            }
        });
    }

    public static void receive(MazeZapPayload payload) {
        Minecraft client = Minecraft.getInstance();
        long now = client.level == null ? 0L : client.level.getGameTime();
        STRIKES.removeIf(strike -> {
            if (strike.entityId != payload.targetEntityId()) return false;
            strike.removeLights();
            return true;
        });
        STRIKES.add(new Strike(payload.targetEntityId(), payload.source(), payload.impulse(),
                now + payload.durationTicks()));
    }

    public static void receiveGroundStrike(DeadSunStrikePayload payload) {
        Minecraft client = Minecraft.getInstance();
        long now = client.level == null ? 0L : client.level.getGameTime();
        GROUND_STRIKES.add(new GroundStrike(payload.target(), payload.seed(),
                now + Math.max(0, payload.warningTicks()),
                now + Math.max(0, payload.warningTicks()) + 12L));
    }

    public static void receiveTelegraph(BossTelegraphPayload payload) {
        Minecraft client = Minecraft.getInstance();
        long now = client.level == null ? 0L : client.level.getGameTime();
        TELEGRAPHS.add(new Telegraph(payload.center(), payload.direction(), payload.radius(), payload.kind(),
                now + Math.max(1, payload.durationTicks()), now * 7919L + TELEGRAPHS.size()));
    }

    public static void clearTransientCombatEffects() {
        STRIKES.forEach(Strike::removeLights);
        STRIKES.clear();
        GROUND_STRIKES.clear();
        TELEGRAPHS.clear();
    }

    private static void tick(Minecraft client) {
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            STRIKES.forEach(Strike::removeLights);
            STRIKES.clear();
            GROUND_STRIKES.clear();
            TELEGRAPHS.clear();
            return;
        }
        long now = client.level.getGameTime();
        GROUND_STRIKES.removeIf(strike -> now > strike.expiresAt);
        TELEGRAPHS.removeIf(telegraph -> now > telegraph.expiresAt);
        Iterator<Strike> iterator = STRIKES.iterator();
        while (iterator.hasNext()) {
            Strike strike = iterator.next();
            Entity target = client.level.getEntity(strike.entityId);
            if (target == null || now > strike.expiresAt) {
                strike.removeLights();
                iterator.remove();
                continue;
            }
            strike.applyElectrification(client, target);
            strike.updateLights(bodyCenter(target), now);
        }
    }

    private record GroundStrike(net.minecraft.core.BlockPos target, long seed,
                                long startsAt, long expiresAt) { }
    private record Telegraph(Vec3 center, Vec3 direction, float radius, int kind,
                             long expiresAt, long seed) { }

    private static Vec3 bodyCenter(Entity target) {
        return target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
    }

    private static void drawDeadSunFractures(Minecraft client,
                                              com.mojang.blaze3d.vertex.VertexConsumer out,
                                              com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                              Vec3 camera, long now) {
        if (client.player == null || client.level == null) return;
        float damage = Math.max(BossFinaleOverlay.sunDetonationStrength(),
                DeadSunEntryCinematic.isActive() ? 0.42F : 0.0F);
        AABB search = client.player.getBoundingBox().inflate(320.0D);
        for (MinotaurEntity minotaur : client.level.getEntitiesOfClass(MinotaurEntity.class, search))
            if (minotaur.isExtremeBoss()) damage = Math.max(damage, minotaur.bossDamageFraction());
        int fractures = Mth.clamp(Mth.floor(damage * 20.0F), 0, 20);
        if (fractures <= 0) return;
        AsterionConfig config = AsterionConfig.INSTANCE;
        Vec3 shake = DeadSunClientEvents.sunOffset();
        Vec3 center = new Vec3(config.deadSunX, config.deadSunHeight, config.deadSunZ).add(shake);
        Vec3 towardCamera = camera.subtract(center).normalize();
        Vec3 right = towardCamera.cross(Math.abs(towardCamera.y) > 0.92D
                ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 up = right.cross(towardCamera).normalize();
        double radius = config.deadSunSize
                * (1.0D + BossFinaleOverlay.sunDetonationStrength() * 2.6D);
        for (int index = 0; index < fractures; index++) {
            long seed = 0x5DEECE66DL + index * 0x9E3779B97F4A7C15L;
            double angle = index * 2.399963229728653D + (seed & 255L) * 0.0017D;
            double innerRadius = radius * (0.06D + (index % 3) * 0.045D);
            double outerRadius = radius * (0.48D + 0.42D * ((index * 37L & 255L) / 255.0D));
            Vec3 start = sunFacePoint(center, right, up, towardCamera, radius, angle, innerRadius);
            Vec3 end = sunFacePoint(center, right, up, towardCamera, radius,
                    angle + Math.sin(index * 1.73D) * 0.22D, outerRadius);
            float strength = Mth.clamp((damage * 16.0F - index) * 0.34F, 0.28F, 1.0F);
            BetterLightningRenderer.draw(out, pose, Vec3.ZERO, start.subtract(camera),
                    end.subtract(camera), Math.min(1.0F, strength * 1.35F), seed + now / 3L);
            if (index < fractures - 2) {
                Vec3 branchStart = start.lerp(end, 0.48D + (index % 3) * 0.09D);
                Vec3 branchEnd = sunFacePoint(center, right, up, towardCamera, radius,
                        angle + (index % 2 == 0 ? 0.38D : -0.34D), outerRadius * 0.78D);
                BetterLightningRenderer.draw(out, pose, Vec3.ZERO, branchStart.subtract(camera),
                        branchEnd.subtract(camera), Math.min(1.0F, strength * 1.18F),
                        seed ^ 0xD1B54A32D192ED03L ^ now / 2L);
            }
        }
    }

    private static Vec3 sunFacePoint(Vec3 center, Vec3 right, Vec3 up, Vec3 towardCamera,
                                     double sphereRadius, double angle, double discRadius) {
        double radial = Math.min(sphereRadius * 0.96D, discRadius);
        double depth = Math.sqrt(Math.max(0.0D, sphereRadius * sphereRadius - radial * radial));
        return center.add(right.scale(Math.cos(angle) * radial))
                .add(up.scale(Math.sin(angle) * radial)).add(towardCamera.scale(depth));
    }

    private static final class Strike {
        private final int entityId;
        private final Vec3 source;
        private final Vec3 impulse;
        private final long createdAt;
        private final long expiresAt;
        private boolean applied;
        private Light bodyLight;
        private Light channelLight;

        private Strike(int entityId, Vec3 source, Vec3 impulse, long expiresAt) {
            this.entityId = entityId;
            this.source = source;
            this.impulse = impulse;
            this.expiresAt = expiresAt;
            Minecraft client = Minecraft.getInstance();
            this.createdAt = client.level == null ? 0L : client.level.getGameTime();
        }

        private void applyElectrification(Minecraft client, Entity target) {
            if (applied || !(target instanceof LivingEntity living)) return;
            applied = true;
            DismembermentEngine.INSTANCE.electrify(client, living, source, impulse,
                    (int) Math.max(1L, expiresAt - createdAt));
        }

        private void drawBodyArcs(com.mojang.blaze3d.vertex.VertexConsumer out,
                                  com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                  Vec3 camera, Entity target, Vec3 body, float charge, long now) {
            double radius = Math.max(0.34D, target.getBbWidth() * 0.62D);
            double height = Math.max(0.7D, target.getBbHeight() * 0.72D);
            long phaseTick = now / 3L;
            for (int arc = 0; arc < 3; arc++) {
                double phase = phaseTick * 1.73D + entityId * 0.91D + arc * 2.094D;
                Vec3 start = body.add(Math.cos(phase) * radius,
                        Math.sin(phase * 1.37D) * height * 0.48D,
                        Math.sin(phase) * radius);
                double endPhase = phase + 1.05D + Math.sin(phase * 0.61D) * 0.32D;
                Vec3 end = body.add(Math.cos(endPhase) * radius * 0.86D,
                        Math.sin(endPhase * 1.21D) * height * 0.52D,
                        Math.sin(endPhase) * radius * 0.86D);
                BetterLightningRenderer.draw(out, pose, Vec3.ZERO, start.subtract(camera),
                        end.subtract(camera), charge * 0.72F,
                        phaseTick * 31L + entityId * 7L + arc * 101L);
            }
        }

        private void updateLights(Vec3 body, long now) {
            AsterionConfig config = AsterionConfig.INSTANCE;
            Vec3 towardSun = source.subtract(body);
            double distance = towardSun.length();
            Vec3 nearChannel = distance < 0.01D ? body : body.add(towardSun.scale(Math.min(3.5D, distance * 0.12D) / distance));
            float life = Math.min(1.0F, (expiresAt - now + 1) / 4.0F);
            float pulse = 0.82F + (float) Math.floorMod(now * 37L + entityId * 11L, 17L) / 100.0F;
            float r = config.deadSunCoreR;
            float g = config.deadSunCoreG;
            float b = config.deadSunCoreB;
            if (bodyLight == null) {
                bodyLight = Lights.point(body, r, g, b, 7.0F, 4.2F).godray(0.0F).castsShadow(false);
                channelLight = Lights.point(nearChannel, r, g, b, 4.5F, 2.2F).godray(0.0F).castsShadow(false);
            }
            bodyLight.setPosition(body).setColor(r, g, b).setRange(6.0F + life * 2.0F)
                    .setIntensity(3.2F * life * pulse);
            channelLight.setPosition(nearChannel).setColor(r, g, b).setRange(4.0F + life)
                    .setIntensity(1.8F * life * pulse);
        }

        private void removeLights() {
            if (bodyLight != null) bodyLight.remove();
            if (channelLight != null) channelLight.remove();
            bodyLight = null;
            channelLight = null;
        }
    }
}
