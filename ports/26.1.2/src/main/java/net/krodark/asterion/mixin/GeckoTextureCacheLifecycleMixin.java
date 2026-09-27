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
    @Unique private long asterion$lastUse = System.nanoTime();
    @Override public void asterion$markUsed() { asterion$lastUse = System.nanoTime(); }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void asterion$skipUnusedAnimation(CallbackInfo ci) {
        var texture = (GeckoLibAnimatedTexture)(Object)this;
        if (!texture.resourceId().getNamespace().equals("asterion")) return;
        long idle = System.nanoTime() - asterion$lastUse;
        if (idle > 30_000_000_000L) asterion$clearFrames();
        if (idle > 1_000_000_000L) ci.cancel();
    }
    @Override public void asterion$setFrameCleanup(Runnable cleanup) {
        asterion$cleanup = cleanup;
        net.krodark.asterion.client.render.TextureFrameCaches.track(this);
    }
    @Override public void asterion$releaseFrameCache() { asterion$clearFrames(); }
    @Unique private void asterion$clearFrames() {
        if (asterion$cleanup != null) { asterion$cleanup.run(); asterion$cleanup = null; }
    }
    @Inject(method = "loadContents", at = @At("HEAD"))
    private void asterion$beforeReload(CallbackInfoReturnable<TextureContents> ci) { asterion$clearFrames(); }
    @Inject(method = "close", at = @At("HEAD"))
    private void asterion$beforeClose(CallbackInfo ci) { asterion$clearFrames(); }
}
