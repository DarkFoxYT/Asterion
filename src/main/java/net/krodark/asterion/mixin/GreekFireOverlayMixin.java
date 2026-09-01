package net.krodark.asterion.mixin;

import net.krodark.asterion.effect.GreekFireBurn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Greek fire trails behind the body; it must not cover the player's camera or face. */
@Mixin(ScreenEffectRenderer.class)
public abstract class GreekFireOverlayMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void asterion$hideGreekFireOverlay(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(GreekFireBurn.TYPE)) ci.cancel();
    }
}
