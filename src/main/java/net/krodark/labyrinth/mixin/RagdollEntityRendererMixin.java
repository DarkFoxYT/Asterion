package net.krodark.labyrinth.mixin;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.krodark.labyrinth.client.ragdoll.RagdollRenderData;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class RagdollEntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void labyrinth$attachRagdollIdentity(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        ((FabricRenderState) state).setData(RagdollRenderData.ENTITY_ID, entity.getId());
    }
}
