package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortDimensionTransitionOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class PortLevelLoadingScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void asterion$replaceLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo callback) {
        if (!PortDimensionTransitionOverlay.shouldReplaceLoadingScreen()) return;
        PortDimensionTransitionOverlay.renderLoadingScreen(graphics);
        callback.cancel();
    }
}
