package net.krodark.asterion.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.network.IgniteGasPayload;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class FlamethrowerInputMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void asterion$igniteWhileSpraying(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Minecraft client = (Minecraft)(Object)this;
        if (client.player != null && client.player.isUsingItem() && client.player.getMainHandItem().is(GameplayContent.FLAMETHROWER)
                && client.options.keyAttack.consumeClick()) {
            if (client.gameMode != null) client.gameMode.releaseUsingItem(client.player);
            if (ClientPlayNetworking.canSend(IgniteGasPayload.TYPE)) ClientPlayNetworking.send(IgniteGasPayload.INSTANCE);
        }
    }
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void asterion$igniteGas(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft)(Object)this;
        if (client.player != null && client.player.getMainHandItem().is(GameplayContent.FLAMETHROWER)) {
            if (ClientPlayNetworking.canSend(IgniteGasPayload.TYPE)) ClientPlayNetworking.send(IgniteGasPayload.INSTANCE);
            cir.setReturnValue(false);
        }
    }
}
