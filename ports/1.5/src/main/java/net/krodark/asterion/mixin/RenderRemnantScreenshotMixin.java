package net.krodark.asterion.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.File;
import java.util.function.Consumer;
import net.krodark.asterion.effect.WitnessCaptureFence;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screenshot.class)
abstract class RenderRemnantScreenshotMixin {
    @Inject(method = "grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private static void asterion$cleanF2(File directory, RenderTarget target, Consumer<Component> message, CallbackInfo ci) {
        if (WitnessCaptureFence.defer(() -> Screenshot.grab(directory, target, message))) ci.cancel();
    }

    @Inject(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private static void asterion$cleanNamedCapture(File directory, String name, RenderTarget target, Consumer<Component> message, CallbackInfo ci) {
        if (WitnessCaptureFence.defer(() -> Screenshot.grab(directory, name, target, message))) ci.cancel();
    }

}
