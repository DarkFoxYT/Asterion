package net.krodark.asterion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Limbo uses world-space underwater fog instead of a camera-locked texture. */
@Mixin(ScreenEffectRenderer.class)
abstract class LimboUnderwaterOverlayMixin {
    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void asterion$noCameraLockedOverlay(Minecraft client, PoseStack pose,
                                                         MultiBufferSource buffers, CallbackInfo ci) {
        if (client.level != null && client.level.dimension().equals(Asterion.LIMBO_LEVEL)) ci.cancel();
    }
}
