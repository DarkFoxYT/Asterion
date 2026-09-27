package net.krodark.asterion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.geckolib.renderer.texture.GeckoLibAnimatedTexture$FrameInfo", remap = false)
public interface GeckoAnimationFrameAccessor {
    @Accessor("index") int asterion$index();
}
