package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.world.UnderworldWaterPhysics;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The water tracker, drowning, drag and splash decisions share one surface sample per tick. */
@Mixin(EntityFluidInteraction.class)
public abstract class LimboFluidInteractionMixin {
    @Unique private double asterion$depth = Double.NaN;
    @Unique private boolean asterion$eyes;

    @Inject(method = "update", at = @At("HEAD"))
    private void asterion$sampleSea(Entity entity, boolean ignoreCurrent, CallbackInfo ci) {
        asterion$depth = Double.NaN;
        if (!(entity instanceof Player) || !entity.level().dimension().equals(Asterion.LIMBO_LEVEL)) return;
        if (UnderworldWaterPhysics.sheltered(entity)) {
            asterion$depth = 0;
            asterion$eyes = false;
            return;
        }
        double surface = UnderworldWaterPhysics.surfaceAt(entity, entity.level().getGameTime());
        if (!Double.isFinite(surface)) return;
        asterion$depth = Math.max(0, surface - entity.getY());
        asterion$eyes = entity.getEyeY() < surface;
    }

    @Inject(method = "getFluidHeight", at = @At("HEAD"), cancellable = true)
    private void asterion$waveDepth(TagKey<Fluid> fluid, CallbackInfoReturnable<Double> cir) {
        if (fluid.equals(FluidTags.WATER) && Double.isFinite(asterion$depth)) cir.setReturnValue(asterion$depth);
    }
    @Inject(method = "isEyeInFluid", at = @At("HEAD"), cancellable = true)
    private void asterion$waveEyes(TagKey<Fluid> fluid, CallbackInfoReturnable<Boolean> cir) {
        if (fluid.equals(FluidTags.WATER) && Double.isFinite(asterion$depth)) cir.setReturnValue(asterion$eyes);
    }
    @Inject(method = "applyCurrentTo", at = @At("HEAD"), cancellable = true)
    private void asterion$dryDeck(TagKey<Fluid> fluid, Entity entity, double scale, CallbackInfo ci) {
        if (fluid.equals(FluidTags.WATER) && asterion$depth == 0) ci.cancel();
    }
}
