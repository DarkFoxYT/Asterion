package net.krodark.asterion.mixin;

import net.krodark.asterion.client.PeripheralWitnessController;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class RenderRemnantCommandMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void asterion$localWitnessCommand(String command, CallbackInfo ci) {
        if (PeripheralWitnessController.command(command)) ci.cancel();
    }
}
