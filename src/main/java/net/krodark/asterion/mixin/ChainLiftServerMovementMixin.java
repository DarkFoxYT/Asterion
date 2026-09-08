package net.krodark.asterion.mixin;

import net.krodark.asterion.entity.ChainLiftEntity;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ChainLiftServerMovementMixin {
    @Shadow public ServerPlayer player;

    @ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
    private ServerboundMovePlayerPacket asterion$useCurrentDeckHeight(ServerboundMovePlayerPacket packet) {
        if (!player.level().getServer().isSameThread() || !packet.isOnGround()
                || player.isPassenger() || player.isSpectator() || player.getAbilities().flying) return packet;
        double x = packet.getX(player.getX()), y = packet.getY(player.getY()), z = packet.getZ(player.getZ());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return packet;
        ChainLiftEntity lift = ChainLiftEntity.supporting(player);
        if (lift == null || Math.abs(y - lift.getY() - .5) > 2
                || !lift.overlapsDeck(player.getBoundingBox().move(x - player.getX(), 0, z - player.getZ()))) return packet;
        // The client reports an older lift height. Keep normal validation of horizontal movement and rotation.
        return new ServerboundMovePlayerPacket.PosRot(x, lift.getY() + .5, z,
                packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()),
                true, packet.horizontalCollision());
    }
}
