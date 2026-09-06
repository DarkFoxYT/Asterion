package net.krodark.asterion.mixin;

import net.krodark.asterion.worldgen.BossArenaEncounter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class CinematicServerMovementMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = {"handleMovePlayer", "handleMoveVehicle", "handlePlayerAction", "handleUseItemOn", "handleUseItem", "handleInteract", "handleAttack"},
            at = @At("HEAD"), cancellable = true)
    private void asterion$lockCinematicBody(CallbackInfo ci) {
         
        if (player.level().getServer().isSameThread() && (BossArenaEncounter.isMovementLocked(player)
                || net.krodark.asterion.entity.MinotaurEntity.controlsPlayer(player))) ci.cancel();
    }

    @Inject(method = "removePlayerFromWorld", at = @At("HEAD"))
    private void asterion$releaseBeforePlayerSave(CallbackInfo ci) {
         
        BossArenaEncounter.releasePlayer(player);
    }
}
