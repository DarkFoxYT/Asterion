package net.krodark.asterion.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.krodark.asterion.port.client.PortTextureFrameCache;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.minecraft.client.renderer.texture.SpriteContents$InterpolationData")
public abstract class PortSpriteInterpolationMixin {
    @Shadow @Final private NativeImage[] activeFrame;
    @Unique private SpriteContents asterion$sprite;
    @Inject(method="<init>",at=@At("RETURN"))
    private void asterion$captureOwner(SpriteContents sprite,CallbackInfo ci) { asterion$sprite = sprite; }
    @Unique private long asterion$key(Object ticker) {
        var t = (PortSpriteTickerAccessor)ticker;
        return (long)t.asterion$frame() << 32 | Integer.toUnsignedLong(t.asterion$subFrame());
    }
    @Inject(method="uploadInterpolatedFrame",at=@At("HEAD"),cancellable=true)
    private void asterion$cached(int x,int y,@Coerce Object ticker,CallbackInfo ci) {
        if (!asterion$sprite.name().getNamespace().equals("asterion")) return;
        var cached = PortTextureFrameCache.get(this, asterion$sprite, asterion$key(ticker));
        if (cached != null) { ((PortSpriteUploadInvoker)asterion$sprite).asterion$upload(x,y,0,0,cached); ci.cancel(); }
    }
    @Inject(method="uploadInterpolatedFrame",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/texture/SpriteContents;upload(IIII[Lcom/mojang/blaze3d/platform/NativeImage;)V"))
    private void asterion$save(int x,int y,@Coerce Object ticker,CallbackInfo ci) {
        if (asterion$sprite.name().getNamespace().equals("asterion")) PortTextureFrameCache.put(this,asterion$sprite,asterion$key(ticker),activeFrame);
    }
    @Inject(method="close",at=@At("HEAD"))
    private void asterion$close(CallbackInfo ci) { PortTextureFrameCache.clear(this); }
}
