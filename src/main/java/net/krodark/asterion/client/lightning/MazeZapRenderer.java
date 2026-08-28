package net.krodark.asterion.client.lightning;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.Lights;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.network.MazeZapPayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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
                double baseAngle = Math.atan2(forward.z, forward.x);
                int rays = AsterionConfig.INSTANCE.cinematicQuality >= 2 ? 15 : 9;
                for (int ray = 0; ray < rays; ray++) {
                    double angle = baseAngle - Math.PI * 0.5D + Math.PI * ray / (rays - 1.0D);
                    Vec3 end = telegraph.center.add(Math.cos(angle) * telegraph.radius, 0.07D,
                            Math.sin(angle) * telegraph.radius);
                    BetterLightningRenderer.draw(lightning, context.poseStack().last(), Vec3.ZERO,
                            telegraph.center.add(0, 0.07D, 0).subtract(camera), end.subtract(camera),
                            pulse, telegraph.seed + ray * 977L);
                }
                for (int segment = 0; segment < 18; segment++) {
                    double a0 = baseAngle - Math.PI * 0.5D + Math.PI * segment / 18.0D;
                    double a1 = baseAngle - Math.PI * 0.5D + Math.PI * (segment + 1) / 18.0D;
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
        TELEGRAPHS.add(new Telegraph(payload.center(), payload.direction(), payload.radius(),
                now + Math.max(1, payload.durationTicks()), now * 7919L + TELEGRAPHS.size()));
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
    private record Telegraph(Vec3 center, Vec3 direction, float radius, long expiresAt, long seed) { }

    private static Vec3 bodyCenter(Entity target) {
        return target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
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
