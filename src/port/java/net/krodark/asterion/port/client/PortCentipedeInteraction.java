package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.entity.CentipedeChain;
import net.krodark.asterion.entity.CentipedeFrame;
import net.krodark.asterion.entity.CentipedeInteraction;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.krodark.asterion.network.CentipedeDriverFramePayload;
import net.krodark.asterion.network.CentipedeMountPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Client-side segment picking and wall-relative driver heading synchronization. */
public final class PortCentipedeInteraction {
    private static int lastEntity = -1, lastSurface = -1, frameTicks;
    private static Vec3 lastHeading = Vec3.ZERO;

    private PortCentipedeInteraction() {}

    public static void tick(Minecraft client) {
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
        boolean changed = lastEntity != centipede.getId() || lastSurface != surface;
        if (changed || frameTicks >= 10 || frameTicks >= 2 && lastHeading.distanceToSqr(forward) > 0.0004D) {
            ClientPlayNetworking.send(new CentipedeDriverFramePayload(centipede.getId(), surface, forward));
            lastEntity = centipede.getId();
            lastSurface = surface;
            lastHeading = forward;
            frameTicks = 0;
        }
    }

    public static boolean tryMount(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null || player.isSpectator()
                || player.isPassenger() || player.isSecondaryUseActive() || player.isUsingItem()
                || !ClientPlayNetworking.canSend(CentipedeMountPayload.TYPE)) return false;
        float partial = 1.0F;
        Vec3 eye = player.getEyePosition(partial);
        Vec3 end = eye.add(player.getViewVector(partial).scale(player.entityInteractionRange()));
        HitResult block = client.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double limit = eye.distanceToSqr(end);
        if (block.getType() != HitResult.Type.MISS) limit = eye.distanceToSqr(block.getLocation());
        if (client.hitResult instanceof EntityHitResult hit && !(hit.getEntity() instanceof ScarletCentipedeEntity))
            limit = Math.min(limit, eye.distanceToSqr(hit.getLocation()));
        ScarletCentipedeEntity target = null;
        CentipedeInteraction.Hit nearest = null;
        AABB search = new AABB(eye, end).inflate(CentipedeChain.MAX_SEGMENTS * CentipedeFrame.LINK_LENGTH + 4.0D);
        for (ScarletCentipedeEntity centipede : client.level.getEntitiesOfClass(ScarletCentipedeEntity.class,
                search, mob -> mob.isAlive() && !mob.isInvisible())) {
            CentipedeInteraction.Hit candidate = CentipedeInteraction.pick(eye, end,
                    centipede.chainSegmentCount(), i -> centipede.chainPose(i, partial));
            if (candidate != null && candidate.distanceSquared() < limit) {
                limit = candidate.distanceSquared();
                nearest = candidate;
                target = centipede;
            }
        }
        if (target == null || nearest == null) return false;
        ClientPlayNetworking.send(new CentipedeMountPayload(target.getId(), nearest.seat(), nearest.point()));
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }
}
