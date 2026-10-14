package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PeripheralWitnessController;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPacketListener.class)
abstract class RenderRemnantCommandMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void asterion$localWitnessCommand(String command, CallbackInfo ci) {
        if (PeripheralWitnessController.command(command)) ci.cancel();
    }

    @Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
    private void asterion$localUnsignedWitnessCommand(String command, CallbackInfoReturnable<Boolean> ci) {
        if (PeripheralWitnessController.command(command)) ci.setReturnValue(true);
    }
}
