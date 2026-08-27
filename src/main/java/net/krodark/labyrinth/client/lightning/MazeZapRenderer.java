package net.krodark.labyrinth.client.lightning;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.Lights;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.krodark.labyrinth.Labyrinth;
import net.krodark.labyrinth.LabyrinthConfig;
import net.krodark.labyrinth.network.MazeZapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
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
                Vec3 start = strike.source.subtract(camera);
                Vec3 end = bodyCenter(target).subtract(camera);
                float charge = Math.min(1.0F, (strike.expiresAt - now + 1) / 4.0F);
                BetterLightningRenderer.draw(context.bufferSource().getBuffer(RenderTypes.lightning()),
                        context.poseStack().last(), Vec3.ZERO, start, end, charge, now / 2L);
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
        STRIKES.add(new Strike(payload.targetEntityId(), payload.source(), now + payload.durationTicks()));
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
            strike.updateLights(bodyCenter(target), now);
        }
    }

    private static Vec3 bodyCenter(Entity target) {
        // Chest/torso center rather than eye position, including correctly scaled entities.
        return target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
    }

    private static final class Strike {
        private final int entityId;
        private final Vec3 source;
        private final long expiresAt;
        private Light bodyLight;
        private Light channelLight;

        private Strike(int entityId, Vec3 source, long expiresAt) {
            this.entityId = entityId;
            this.source = source;
            this.expiresAt = expiresAt;
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
