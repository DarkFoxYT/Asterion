package net.krodark.asterion.mixin;

import com.geckolib.renderer.texture.GeckoLibAnimatedTexture;
import net.krodark.asterion.client.render.TextureCacheOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.texture.TextureContents;

@Mixin(value = GeckoLibAnimatedTexture.class, remap = false)
public abstract class GeckoTextureCacheLifecycleMixin implements TextureCacheOwner {
    @Unique private Runnable asterion$cleanup;
    @Override public void asterion$setFrameCleanup(Runnable cleanup) { asterion$cleanup = cleanup; }
    @Unique private void asterion$clearFrames() {
        if (asterion$cleanup != null) { asterion$cleanup.run(); asterion$cleanup = null; }
    }
    @Inject(method = "loadContents", at = @At("HEAD"))
    private void asterion$beforeReload(CallbackInfoReturnable<TextureContents> ci) { asterion$clearFrames(); }
    @Inject(method = "close", at = @At("HEAD"))
    private void asterion$beforeClose(CallbackInfo ci) { asterion$clearFrames(); }
}
