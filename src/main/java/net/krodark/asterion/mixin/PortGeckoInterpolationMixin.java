package net.krodark.asterion.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import net.krodark.asterion.port.client.PortTextureFrameCache;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="software.bernie.geckolib.cache.texture.AnimatableTexture$AnimationContents$Texture",remap=false)
public abstract class PortGeckoInterpolationMixin {
    @Shadow protected int currentFrame;
    @Shadow protected int currentSubframe;
    @Unique private boolean asterion$cached;
    @Unique private long asterion$key() { return (long)currentFrame << 32 | Integer.toUnsignedLong(currentSubframe); }
    @Inject(method="generateInterpolatedFrame",at=@At("HEAD"),cancellable=true)
    private void asterion$cached(int texture,NativeImage source,NativeImage target,CallbackInfo ci) {
        var cached = PortTextureFrameCache.get(this, source, asterion$key());
        asterion$cached = cached != null;
        if (cached != null) {
            com.mojang.blaze3d.platform.TextureUtil.prepareImage(texture,0,target.getWidth(),target.getHeight());
            cached[0].upload(0,0,0,0,0,target.getWidth(),target.getHeight(),false,false);
            ci.cancel();
        }
    }
    @Inject(method="generateInterpolatedFrame",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/TextureUtil;prepareImage(IIII)V",remap=true))
    private void asterion$save(int texture,NativeImage source,NativeImage target,CallbackInfo ci) {
        if (!asterion$cached) PortTextureFrameCache.put(this,source,asterion$key(),new NativeImage[]{target});
    }
    @Inject(method="close",at=@At("HEAD"))
    private void asterion$close(CallbackInfo ci) { PortTextureFrameCache.clear(this); }
}
