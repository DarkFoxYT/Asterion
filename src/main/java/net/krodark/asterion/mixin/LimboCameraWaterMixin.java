package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.world.UnderworldWaterPhysics;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class LimboCameraWaterMixin {
    @Inject(method = "getFluidInCamera", at = @At("RETURN"), cancellable = true)
    private void asterion$waveCamera(CallbackInfoReturnable<FogType> cir) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !client.level.dimension().equals(Asterion.LIMBO_LEVEL)
                || cir.getReturnValue() == FogType.LAVA || cir.getReturnValue() == FogType.POWDER_SNOW) return;
        if (UnderworldWaterPhysics.sheltered(client.player)) { cir.setReturnValue(FogType.NONE); return; }
        var position = ((Camera)(Object)this).position();
        double ticks = client.level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double surface = UnderworldWaterPhysics.surfaceAt(client.level, position, ticks);
        if (Double.isFinite(surface)) cir.setReturnValue(position.y < surface ? FogType.WATER : FogType.NONE);
    }
}
