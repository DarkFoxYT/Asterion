package net.krodark.asterion.mixin;

import com.geckolib.renderer.GeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.client.ragdoll.RagdollRenderData;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GeoEntityRenderer.class)
abstract class RagdollGeoEntityRendererMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void asterion$hidePhysicalBody(EntityRenderState state, PoseStack poses,
                                          SubmitNodeCollector output, CameraRenderState camera, CallbackInfo ci) {
        FabricRenderState renderState = (FabricRenderState) state;
        if (Boolean.TRUE.equals(renderState.getData(RagdollRenderData.GUI_PREVIEW))) return;
        Integer id = renderState.getData(RagdollRenderData.ENTITY_ID);
        if (id != null && DismembermentEngine.INSTANCE.isRagdolled(id)) ci.cancel();
    }
}
