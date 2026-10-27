package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class LabyrinthCloudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void asterion$hideClouds(CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.dimension().equals(Asterion.ASTERION_LEVEL)) ci.cancel();
    }
}
