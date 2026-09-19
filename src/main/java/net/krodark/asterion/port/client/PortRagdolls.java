package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.port.client.ragdoll.*;
import net.krodark.asterion.network.ragdoll.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Loader-neutral wiring for the main branch's rigid-body solver. */
public final class PortRagdolls {
    private static Vec3 smoothCamera;
    private PortRagdolls() { }
    public static void initialize() {
        DazeOverlay.register();
        WorldRenderEvents.AFTER_ENTITIES.register(RagdollRenderer::submit);
        ClientPlayNetworking.registerGlobalReceiver(RagdollImpulsePayload.TYPE,(payload,context)->context.client().execute(()->
            DismembermentEngine.INSTANCE.forcePlayerTumble(context.client(),payload.source(),payload.impulse(),payload.force())));
        ClientPlayNetworking.registerGlobalReceiver(RagdollExplosionPayload.TYPE,(payload,context)->context.client().execute(()->
            DismembermentEngine.INSTANCE.applyExplosion(context.client(),payload.center(),payload.radius())));
        ClientPlayNetworking.registerGlobalReceiver(RagdollAuthorityPayload.TYPE,(payload,context)->context.client().execute(()->
            DismembermentEngine.INSTANCE.reconcilePlayerAuthority(context.client(),payload.position(),payload.velocity(),payload.serverTick())));
        ClientPlayNetworking.registerGlobalReceiver(RagdollPosePayload.TYPE,(payload,context)->context.client().execute(()->
            DismembermentEngine.INSTANCE.applyRemotePose(context.client(),payload)));
        ClientPlayNetworking.registerGlobalReceiver(RagdollStatePayload.TYPE,(payload,context)->context.client().execute(()->
            DismembermentEngine.INSTANCE.applyRemoteState(context.client(),payload)));
        ClientPlayNetworking.registerGlobalReceiver(net.krodark.asterion.network.DazePayload.TYPE,(payload,context)->context.client().execute(()->DazeOverlay.begin(payload)));
    }
    public static void tick(Minecraft client) { RagdollClientController.tick(client); DazeOverlay.tick(client); }
    public static boolean localMovementLocked() {
        var player=Minecraft.getInstance().player;
        return player!=null && DismembermentEngine.INSTANCE.isPlayerTumbling(player.getId());
    }
    public static boolean isRagdolled(LivingEntity entity) { return DismembermentEngine.INSTANCE.isRagdolled(entity.getId()); }
    public static Vec3 ragdollHandPosition(int entityId,boolean right) { return DismembermentEngine.INSTANCE.ragdollHandPosition(entityId,right); }
    public static Vec3 cameraPosition(Vec3 vanilla,float partial) {
        var client=Minecraft.getInstance();
        if(client.player==null) return null;
        var engine=DismembermentEngine.INSTANCE;
        Vec3 anchor=engine.tumbleCameraAnchor(client.player.getId(),partial);
        if(anchor==null) { smoothCamera=null;return null; }
        Vec3 head=engine.playerTumbleCameraPosition(client.player.getId(),partial);
        Vec3 visualEye=head==null?anchor.add(0,.35,0):anchor.lerp(head,.38).add(0,.18,0);
        Vec3 desired=visualEye.add(vanilla.subtract(client.player.getEyePosition(partial)));
        smoothCamera=smoothCamera==null||smoothCamera.distanceToSqr(desired)>6.25?desired:smoothCamera.lerp(desired,.52);
        smoothCamera=clipCamera(client,anchor,smoothCamera);
        return engine.pushCameraOutsideRagdoll(client.player.getId(),smoothCamera);
    }
    private static Vec3 clipCamera(Minecraft minecraft, Vec3 anchor, Vec3 desired) {
        if (minecraft.level == null || minecraft.player == null) return desired;
        Vec3 travel = desired.subtract(anchor);
        double distance = travel.length(), permitted = distance;
        if (distance > 1.0e-6) for (int corner = 0; corner < 8; corner++) {
            double skin = .09;
            Vec3 offset = new Vec3((corner & 1) == 0 ? -skin : skin,
                    (corner & 2) == 0 ? -skin : skin, (corner & 4) == 0 ? -skin : skin);
            Vec3 from = anchor.add(offset);
            var hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(from, desired.add(offset),
                    net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, minecraft.player));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS)
                permitted = Math.min(permitted, Math.max(0, from.distanceTo(hit.getLocation()) - .11));
        }
        Vec3 clipped = distance <= 1.0e-6 ? desired : anchor.add(travel.scale(permitted / distance));
        for (int step = 0; step <= 12; step++) {
            Vec3 candidate = clipped.lerp(anchor, step / 12.0);
            net.minecraft.world.phys.AABB volume = new net.minecraft.world.phys.AABB(candidate.x-.085,candidate.y-.085,candidate.z-.085,
                    candidate.x+.085,candidate.y+.085,candidate.z+.085);
            if (minecraft.level.noCollision(minecraft.player, volume)) return candidate;
        }
        return anchor;
    }
}
