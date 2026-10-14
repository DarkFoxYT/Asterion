package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PeripheralWitnessController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class RenderRemnantClientTickMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void asterion$personalWitness(CallbackInfo ci) { PeripheralWitnessController.tick((Minecraft) (Object) this); }
}
