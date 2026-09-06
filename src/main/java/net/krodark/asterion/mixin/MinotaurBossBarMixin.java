package net.krodark.asterion.mixin;

import net.krodark.asterion.client.MinotaurBossBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class MinotaurBossBarMixin {
    @Inject(method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V", at = @At("HEAD"), cancellable = true)
    private void asterion$bar(GuiGraphicsExtractor graphics, int x, int y, BossEvent event, CallbackInfo ci) {
        if (!event.getName().getString().equals("THE MINOTAUR")) return;
        MinotaurBossBar.render(graphics, x, y, event);
        ci.cancel();
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void asterion$reset(CallbackInfo ci) { MinotaurBossBar.reset(); }
    @org.spongepowered.asm.mixin.injection.Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void asterion$nameBelow(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                                    net.minecraft.network.chat.Component name, int x, int y, int color) {
        if (name.getString().equals("THE MINOTAUR"))
            y += 9 + Math.round(48 * MinotaurBossBar.scale(graphics)) - 5;
        graphics.text(font, name, x, y, color);
    }
}
