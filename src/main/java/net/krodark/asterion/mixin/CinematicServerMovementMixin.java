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
        // Let vanilla dispatch packets off the network thread before touching encounter state.
        if (player.level().getServer().isSameThread() && (BossArenaEncounter.isMovementLocked(player)
                || net.krodark.asterion.entity.MinotaurEntity.controlsPlayer(player))) ci.cancel();
    }

    @Inject(method = "removePlayerFromWorld", at = @At("HEAD"))
    private void asterion$releaseBeforePlayerSave(CallbackInfo ci) {
        // Run on the server thread before logout saves temporary cinematic flags to player data.
        BossArenaEncounter.releasePlayer(player);
    }
}
