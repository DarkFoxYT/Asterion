package net.krodark.asterion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium 0.9.1 can queue the same asynchronous chunk-removal event twice after
 * long sessions or rapid world changes. The second event is harmless: its
 * section has already been detached. Keep that one path idempotent while still
 * allowing Sodium's "wrong section" check to expose real region corruption.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion", remap = false)
abstract class SodiumRenderRegionMixin {
    @Inject(method = "removeSection",
            at = @At(value = "CONSTANT",
                    args = "stringValue=Section was not loaded within the region"),
            cancellable = true,
            require = 0,
            remap = false)
    private void asterion$ignoreDuplicateRemoval(@Coerce Object section, CallbackInfo callback) {
        callback.cancel();
    }
}
