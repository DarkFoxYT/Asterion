package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.entity.CentipedeChain;
import net.krodark.asterion.entity.CentipedeFrame;
import net.krodark.asterion.entity.CentipedeInteraction;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.krodark.asterion.network.CentipedeMountPayload;
import net.krodark.asterion.network.CentipedeDriverFramePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** On-demand virtual multipart picking: no invisible entities, ticking hitboxes, or orphaned parts. */
public final class CentipedeInteractionClient {
    private static int lastEntity = -1, lastSurface = -1, frameTicks;
    private static Vec3 lastHeading = Vec3.ZERO;
    private CentipedeInteractionClient() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !(client.player.getVehicle() instanceof ScarletCentipedeEntity centipede)
                    || centipede.getControllingPassenger() != client.player) {
                lastEntity = lastSurface = -1;
                frameTicks = 0;
                return;
            }
            if (!ClientPlayNetworking.canSend(CentipedeDriverFramePayload.TYPE)) return;
            int surface = centipede.attachedSurface().ordinal();
            Vec3 forward = centipede.surfaceForward();
            frameTicks++;
            boolean changedFace = lastEntity != centipede.getId() || lastSurface != surface;
            if (changedFace || frameTicks >= 10 || frameTicks >= 2 && lastHeading.distanceToSqr(forward) > .0004) {
                ClientPlayNetworking.send(new CentipedeDriverFramePayload(centipede.getId(), surface, forward));
                lastEntity = centipede.getId();
                lastSurface = surface;
                lastHeading = forward;
                frameTicks = 0;
            }
        });
    }

    public static boolean tryMount(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null || player.isSpectator()
                || player.isPassenger() || player.isSecondaryUseActive() || player.isUsingItem()
                || !ClientPlayNetworking.canSend(CentipedeMountPayload.TYPE)) return false;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(partial);
        Vec3 end = eye.add(player.getViewVector(partial).scale(player.entityInteractionRange()));
        var block = client.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limit = eye.distanceToSqr(end);
        if (block.getType() != HitResult.Type.MISS) limit = eye.distanceToSqr(block.getLocation());
        if (client.hitResult instanceof EntityHitResult entityHit
                && !(entityHit.getEntity() instanceof ScarletCentipedeEntity))
            limit = Math.min(limit, eye.distanceToSqr(entityHit.getLocation()));
        ScarletCentipedeEntity target = null;
        CentipedeInteraction.Hit nearest = null;
        // Entity spatial indexing only knows about the head; include all possible tail lengths.
        AABB search = new AABB(eye, end).inflate(CentipedeChain.MAX_SEGMENTS * CentipedeFrame.LINK_LENGTH + 4);
        for (var centipede : client.level.getEntitiesOfClass(ScarletCentipedeEntity.class, search,
                mob -> mob.isAlive() && !mob.isInvisible())) {
            var hit = CentipedeInteraction.pick(eye, end, centipede.chainSegmentCount(), i -> centipede.chainPose(i, partial));
            if (hit != null && hit.distanceSquared() < limit) {
                limit = hit.distanceSquared();
                nearest = hit;
                target = centipede;
            }
        }
        if (target == null) return false;
        ClientPlayNetworking.send(new CentipedeMountPayload(target.getId(), nearest.seat(), nearest.point()));
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }
}
