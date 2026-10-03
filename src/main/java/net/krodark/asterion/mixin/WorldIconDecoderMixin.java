package net.krodark.asterion.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets="net.minecraft.client.gui.screens.worldselection.WorldSelectionList$WorldListEntry")
abstract class WorldIconDecoderMixin {
    @Redirect(method="loadIcon",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/NativeImage;read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private NativeImage asterion$safeWorldIcon(InputStream input) throws IOException {
        return net.krodark.asterion.client.render.SafeWorldIcon.read(input);
    }
}
