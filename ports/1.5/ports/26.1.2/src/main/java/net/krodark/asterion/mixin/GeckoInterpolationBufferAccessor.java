package net.krodark.asterion.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.geckolib.renderer.texture.GeckoLibAnimatedTexture$AnimationInfo$InterpolationData", remap = false)
public interface GeckoInterpolationBufferAccessor {
    @Accessor("buffer") NativeImage asterion$buffer();
}
