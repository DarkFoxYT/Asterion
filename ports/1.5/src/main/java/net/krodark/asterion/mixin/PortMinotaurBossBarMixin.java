package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortMinotaurBossBar;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
    private Component asterion$hideVanillaName(Component name) {
        // Leave the draw call available to other mods' boss-overlay hooks.
        return "THE MINOTAUR".equals(name.getString()) ? Component.empty() : name;
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void asterion$reset(CallbackInfo callback) { PortMinotaurBossBar.reset(); }
}
