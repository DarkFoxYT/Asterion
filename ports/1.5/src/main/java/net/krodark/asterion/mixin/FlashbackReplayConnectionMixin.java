package net.krodark.asterion.mixin;

import net.krodark.asterion.compat.FlashbackEssentialNetworkCompatibility;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class FlashbackReplayConnectionMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void asterion$protectRecordedOutfits(CallbackInfo ci) {
        FlashbackEssentialNetworkCompatibility.protectReplayConnection(
                ((ClientPacketListener) (Object) this).getConnection());
    }
}
