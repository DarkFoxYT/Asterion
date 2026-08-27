package net.krodark.labyrinth.mixin;

import net.krodark.labyrinth.client.DimensionTransitionOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void labyrinth$replaceLoadingScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo callback) {
        if (!DimensionTransitionOverlay.isActive()) return;
        DimensionTransitionOverlay.renderLoadingScreen(graphics);
        callback.cancel();
    }
}
