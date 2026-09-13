package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.network.ragdoll.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Network-backed 1.21.1 ragdoll compatibility controller. */
public final class PortRagdolls {
    private static final Map<Integer, Integer> ACTIVE = new HashMap<>();
    private static CameraType previousCamera;

    private PortRagdolls() {}

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(RagdollImpulsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().player == null) return;
                    context.client().player.setDeltaMovement(payload.impulse());
                    activate(context.client().player.getId(), 90);
                }));
        ClientPlayNetworking.registerGlobalReceiver(RagdollExplosionPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().level == null) return;
                    double radius = payload.radius() + 3.0D;
                    for (LivingEntity entity : context.client().level.getEntitiesOfClass(LivingEntity.class,
                            new net.minecraft.world.phys.AABB(payload.center(), payload.center()).inflate(radius)))
                        activate(entity.getId(), 70);
                }));
        ClientPlayNetworking.registerGlobalReceiver(RagdollStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.active()) activate(payload.entityId(), 240);
                    else ACTIVE.remove(payload.entityId());
                }));
        ClientPlayNetworking.registerGlobalReceiver(RagdollPosePayload.TYPE, (payload, context) ->
                context.client().execute(() -> activate(payload.entityId(), 40)));
        ClientPlayNetworking.registerGlobalReceiver(RagdollAuthorityPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().player != null) activate(context.client().player.getId(), 60);
                }));
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            ACTIVE.clear();
            restoreCamera(client);
            return;
        }
        Iterator<Map.Entry<Integer, Integer>> entries = ACTIVE.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, Integer> entry = entries.next();
            if (client.level.getEntity(entry.getKey()) == null || entry.setValue(entry.getValue() - 1) <= 1)
                entries.remove();
        }
        if (client.player != null && ACTIVE.containsKey(client.player.getId())) {
            if (previousCamera == null) previousCamera = client.options.getCameraType();
            if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK)
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else restoreCamera(client);
    }

    public static boolean isRagdolled(LivingEntity entity) {
        return ACTIVE.containsKey(entity.getId()) || (!entity.isAlive() && entity.deathTime > 0);
    }

    private static void activate(int entityId, int ticks) {
        ACTIVE.merge(entityId, ticks, Math::max);
    }

    private static void restoreCamera(Minecraft client) {
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        previousCamera = null;
    }
}
