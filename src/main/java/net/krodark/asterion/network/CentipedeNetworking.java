package net.krodark.asterion.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.entity.CentipedeInteraction;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CentipedeNetworking {
    private CentipedeNetworking() {}

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(CentipedeDriverFramePayload.TYPE, CentipedeDriverFramePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CentipedeDriverFramePayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    if (context.player().level().getEntity(payload.entityId()) instanceof ScarletCentipedeEntity centipede)
                        centipede.receiveDriverFrame(context.player(), payload.surface(), payload.forward());
                }));
        PayloadTypeRegistry.serverboundPlay().register(CentipedeMountPayload.TYPE, CentipedeMountPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CentipedeMountPayload.TYPE, (payload, context) ->
                context.server().execute(() -> mount(context.player(), payload)));
    }

    private static void mount(ServerPlayer player, CentipedeMountPayload request) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSecondaryUseActive()) return;
        if (!(player.level().getEntity(request.entityId()) instanceof ScarletCentipedeEntity centipede)
                || !centipede.isAlive() || request.seat() < 0 || request.seat() >= centipede.chainSegmentCount()) return;
        Vec3 point = request.point(), eye = player.getEyePosition();
        double reach = player.entityInteractionRange() + .35;
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || eye.distanceToSqr(point) > reach * reach) return;
        // Validate against actual moving segment geometry, not distance to the head (a long
        // centipede's last seat can legitimately be sixty blocks from its entity origin).
        boolean inside = false;
        for (float partial : new float[]{0, .5F, 1})
            inside |= CentipedeInteraction.contains(point, request.seat(), centipede.chainPose(request.seat(), partial), .35);
        if (!inside) return;
        var block = player.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS && eye.distanceToSqr(block.getLocation()) + .0025 < eye.distanceToSqr(point)) return;
        centipede.mountSegment(player, request.seat());
    }
}
