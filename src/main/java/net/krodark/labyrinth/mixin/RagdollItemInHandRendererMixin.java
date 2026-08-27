package net.krodark.labyrinth.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.labyrinth.client.ragdoll.DismembermentEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class RagdollItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void labyrinth$hideHands(float partial, PoseStack poses, SubmitNodeCollector output,
                                     LocalPlayer player, int light, CallbackInfo ci) {
        if (DismembermentEngine.INSTANCE.isPlayerTumbling(player.getId())) ci.cancel();
    }
}
