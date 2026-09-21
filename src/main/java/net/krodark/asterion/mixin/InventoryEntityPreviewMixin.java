package net.krodark.asterion.mixin;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.krodark.asterion.client.ragdoll.RagdollRenderData;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class InventoryEntityPreviewMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private static void asterion$markPreview(LivingEntity entity, CallbackInfoReturnable<EntityRenderState> cir) {
        ((FabricRenderState)cir.getReturnValue()).setData(RagdollRenderData.GUI_PREVIEW, true);
    }
}
