package net.krodark.asterion.mixin;

import net.krodark.asterion.effect.GreekFireBurn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Preserve vanilla's animated flame shape, transparency and camera placement, tinted Greek green. */
@Mixin(ScreenEffectRenderer.class)
public abstract class GreekFireOverlayMixin {
    @ModifyArgs(method = "renderFire", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static void asterion$greenFire(Args args) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(GreekFireBurn.TYPE)) {
            args.set(0, .12F);
            args.set(1, 1F);
            args.set(2, .18F);
        }
    }
}
