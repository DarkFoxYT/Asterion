package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.entity.MinotaurEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A rendered limb can extend beyond the entity's navigation box. */
public record MinotaurBodyPayload(int entityId, Vec3 point, boolean attack, int part) implements CustomPacketPayload {
    public MinotaurBodyPayload(int entityId, Vec3 point, boolean attack) { this(entityId, point, attack, -1); }
    public static final Type<MinotaurBodyPayload> TYPE = new Type<>(Asterion.id("minotaur_body"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinotaurBodyPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeDouble(payload.point.x); buffer.writeDouble(payload.point.y); buffer.writeDouble(payload.point.z);
                buffer.writeBoolean(payload.attack);
                buffer.writeVarInt(payload.part);
            }, buffer -> new MinotaurBodyPayload(buffer.readVarInt(),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readBoolean(), buffer.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
    }

    public static void handle(ServerPlayer player, MinotaurBodyPayload request) {
        if (!player.isAlive() || player.isSpectator() || player.isUsingItem()
                || !(player.level().getEntity(request.entityId) instanceof MinotaurEntity boss) || !boss.isAlive()) return;
        Vec3 eye = player.getEyePosition(), point = request.point;
        double reach = player.entityInteractionRange() + .35;
        if (!Double.isFinite(point.lengthSqr()) || eye.distanceToSqr(point) > reach * reach) return;
        // Bound the client pose to the authored body's maximum extension. Do not expand movement collision.
        if (!boss.animatedBodyBounds().contains(point)) return;
        var block = player.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS && eye.distanceToSqr(block.getLocation()) + .0025 < eye.distanceToSqr(point)) return;
        if (boss.isDefeatedBoss() && boss.isHarvested()) {
            if (request.part < -1 || request.part > 5) return;
            boss.dismember(player, InteractionHand.MAIN_HAND, net.krodark.asterion.entity.MinotaurRemains.fromId(request.part), point);
        } else if (request.attack) {
            if (player.cannotAttackWithItem(player.getMainHandItem(), 0)) return;
            player.attack(boss);
            player.resetLastActionTime();
        } else if (boss.isDefeatedBoss()) boss.mobInteract(player, InteractionHand.MAIN_HAND);
    }
}
