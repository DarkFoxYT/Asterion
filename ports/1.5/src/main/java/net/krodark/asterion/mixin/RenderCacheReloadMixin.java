package net.krodark.asterion.mixin;

import net.minecraft.client.Minecraft;
import net.krodark.asterion.port.client.PortRenderCaches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class RenderCacheReloadMixin {
    @Inject(method = "reloadResourcePacks", at = @At("HEAD"))
    private void asterion$releaseCaches(CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        PortRenderCaches.clear();
    }
}
