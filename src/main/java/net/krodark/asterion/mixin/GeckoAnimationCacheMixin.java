package net.krodark.asterion.mixin;

import com.geckolib.renderer.texture.GeckoLibAnimatedTexture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.Map;

/** Cache exact interpolated frames; texture timing and GPU uploads remain GeckoLib's. */
@Mixin(targets = "com.geckolib.renderer.texture.GeckoLibAnimatedTexture$AnimationInfo", remap = false)
public abstract class GeckoAnimationCacheMixin {
    @Shadow @Final java.util.List<?> frames;
    @Shadow int currentFrame;
    @Shadow int subFrame;
    @Shadow @Final GeckoLibAnimatedTexture this$0;
    @Unique private final Map<Long, NativeImage> asterion$frames = new HashMap<>();
    @Unique private long asterion$bytes;
    @Unique private final java.util.Set<Long> asterion$verified = new java.util.HashSet<>();

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/geckolib/renderer/texture/GeckoLibAnimatedTexture$AnimationInfo$InterpolationData;tickAndUpload(Lcom/mojang/blaze3d/platform/NativeImage;Lcom/mojang/blaze3d/textures/GpuTexture;)V"))
    private void asterion$reuseFrame(@Coerce Object interpolation, NativeImage source, GpuTexture texture,
                                    Operation<Void> original) {
        if (!this$0.resourceId().getNamespace().equals("asterion")) {
            original.call(interpolation, source, texture);
            return;
        }
        int current = ((GeckoAnimationFrameAccessor)frames.get(currentFrame)).asterion$index();
        int next = ((GeckoAnimationFrameAccessor)frames.get((currentFrame + 1) % frames.size())).asterion$index();
        if (current == next) { original.call(interpolation, source, texture); return; }
        if (asterion$frames.isEmpty())
            ((net.krodark.asterion.client.render.TextureCacheOwner)this$0).asterion$setFrameCleanup(this::asterion$clearFrames);
        long key = (long)currentFrame << 32 | Integer.toUnsignedLong(subFrame);
        NativeImage cached = asterion$frames.get(key);
        if (cached != null) {
            if (Boolean.getBoolean("asterion.verifyTextureFrames") && asterion$verified.add(key)) {
                original.call(interpolation, source, texture);
                var expected = ((GeckoInterpolationBufferAccessor)interpolation).asterion$buffer();
                for (int y = 0; y < cached.getHeight(); y++) for (int x = 0; x < cached.getWidth(); x++)
                    if (cached.getPixel(x, y) != expected.getPixel(x, y))
                        throw new AssertionError("Cached animation pixel differs: " + this$0.resourceId());
                System.setProperty("asterion.verifiedTextureFrames",
                        Integer.toString(Integer.getInteger("asterion.verifiedTextureFrames", 0) + 1));
            }
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, cached,
                    0, 0, 0, 0, cached.getWidth(), cached.getHeight(), 0, 0);
            return;
        }
        original.call(interpolation, source, texture);
        NativeImage buffer = ((GeckoInterpolationBufferAccessor)interpolation).asterion$buffer();
        long bytes = (long)buffer.getWidth() * buffer.getHeight() * 4;
        // Resource packs can supply enormous animations. Fall back once this texture reaches 32 MiB.
        if (asterion$bytes + bytes > 32L * 1024 * 1024) return;
        cached = new NativeImage(buffer.getWidth(), buffer.getHeight(), false);
        buffer.copyRect(cached, 0, 0, 0, 0, buffer.getWidth(), buffer.getHeight(), false, false);
        asterion$frames.put(key, cached);
        asterion$bytes += bytes;
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void asterion$releaseFrames(CallbackInfo ci) {
        asterion$clearFrames();
    }
    @Unique private void asterion$clearFrames() {
        asterion$frames.values().forEach(NativeImage::close);
        asterion$frames.clear();
        asterion$bytes = 0;
        asterion$verified.clear();
    }
}
