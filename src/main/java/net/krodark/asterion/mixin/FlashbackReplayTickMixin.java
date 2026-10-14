package net.krodark.asterion.mixin;

import net.krodark.asterion.compat.FlashbackAppearanceCompatibility;
import net.krodark.asterion.compat.FlashbackEssentialNetworkCompatibility;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 1100)
abstract class FlashbackReplayTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void asterion$protectReplayBeforeLiveOutfitTick(CallbackInfo ci) {
        if (!FlashbackAppearanceCompatibility.isPlayingReplay()) return;
        var connection = ((Minecraft) (Object) this).getConnection();
        if (connection != null) FlashbackEssentialNetworkCompatibility.protectReplayConnection(connection.getConnection());
    }
}
