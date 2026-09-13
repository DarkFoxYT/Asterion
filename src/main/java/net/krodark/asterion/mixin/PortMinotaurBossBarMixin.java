package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortMinotaurBossBar;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class PortMinotaurBossBarMixin {
    @Inject(method = "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void asterion$customBar(GuiGraphics graphics, int x, int y, BossEvent event,
                                    CallbackInfo callback) {
        if (!PortMinotaurBossBar.isMinotaur(event)) return;
        PortMinotaurBossBar.render(graphics, x, y, event);
        callback.cancel();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int asterion$hideVanillaName(GuiGraphics graphics, Font font, Component name,
                                        int x, int y, int color) {
        return "THE MINOTAUR".equals(name.getString()) ? 0 : graphics.drawString(font, name, x, y, color);
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void asterion$reset(CallbackInfo callback) { PortMinotaurBossBar.reset(); }
}
