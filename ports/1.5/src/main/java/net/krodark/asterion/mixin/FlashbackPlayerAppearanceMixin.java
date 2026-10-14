package net.krodark.asterion.mixin;

import net.krodark.asterion.compat.FlashbackAppearanceCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractClientPlayer.class, priority = 1100)
abstract class FlashbackPlayerAppearanceMixin {
    @Shadow protected abstract PlayerInfo getPlayerInfo();

    @Inject(method = "getPlayerInfo", at = @At("HEAD"), cancellable = true)
    private void asterion$currentReplayProfile(CallbackInfoReturnable<PlayerInfo> ci) {
        if (!FlashbackAppearanceCompatibility.isPlayingReplay()) return;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        PlayerInfo current = connection.getPlayerInfo(player.getUUID());
        if (current != null) {
            FlashbackAppearanceCompatibility.bindRecordedAppearance(current, connection);
            ci.setReturnValue(current);
        }
    }

    //? if >=1.20.5 {
    @Inject(method = "getSkin", at = @At("HEAD"))
    //? } else {
    /*@Inject(method = "getSkinTextureLocation", at = @At("HEAD"))
    *///? }
    private void asterion$bindReplayFallback(CallbackInfoReturnable<?> ci) {
        if (!FlashbackAppearanceCompatibility.isPlayingReplay()) return;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            PlayerInfo info = getPlayerInfo();
            FlashbackAppearanceCompatibility.bindRecordedAppearance(info, connection);
        }
    }
}
