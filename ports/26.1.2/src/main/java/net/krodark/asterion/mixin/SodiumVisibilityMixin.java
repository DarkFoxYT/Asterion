package net.krodark.asterion.mixin;

import net.krodark.asterion.client.render.SodiumVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Never query Sodium's section visibility until its terrain setup has completed. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumVisibilityMixin {
    @Inject(method = "setupTerrain", at = @At("RETURN"), require = 0)
    private void asterion$terrainReady(CallbackInfo ci) { SodiumVisibility.terrainReady(); }
    @Inject(method = {"setLevel", "reload"}, at = @At("HEAD"), require = 0)
    private void asterion$resetVisibility(CallbackInfo ci) { SodiumVisibility.reset(); }
}
