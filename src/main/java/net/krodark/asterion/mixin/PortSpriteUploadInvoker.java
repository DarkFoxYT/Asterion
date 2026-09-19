package net.krodark.asterion.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(SpriteContents.class)
public interface PortSpriteUploadInvoker {
    @Invoker("upload") void asterion$upload(int x, int y, int sx, int sy, NativeImage[] images);
}
