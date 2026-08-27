package net.krodark.labyrinth.client.lightning;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.Lights;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.krodark.labyrinth.Labyrinth;
import net.krodark.labyrinth.LabyrinthConfig;
import net.krodark.labyrinth.client.ragdoll.DismembermentEngine;
import net.krodark.labyrinth.network.MazeZapPayload;
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

    private MazeZapRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MazeZapRenderer::tick);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || !client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL)) return;
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

    private static void tick(Minecraft client) {
        if (client.level == null || !client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL)) {
            STRIKES.forEach(Strike::removeLights);
            STRIKES.clear();
            return;
        }
        long now = client.level.getGameTime();
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
            LabyrinthConfig config = LabyrinthConfig.INSTANCE;
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
