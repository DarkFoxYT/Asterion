package net.krodark.asterion.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.client.renderer.texture.SpriteContents$Ticker")
public interface PortSpriteTickerAccessor {
    @Accessor("frame") int asterion$frame();
    @Accessor("subFrame") int asterion$subFrame();
}
