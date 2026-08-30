package net.krodark.asterion.mixin;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.krodark.asterion.client.ragdoll.RagdollRenderData;
import net.krodark.asterion.client.render.entity.CentipedeRiderRenderData;
import net.krodark.asterion.entity.CentipedeFrame;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.world.phys.Vec3;
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
    private void asterion$attachRagdollIdentity(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        ((FabricRenderState) state).setData(RagdollRenderData.ENTITY_ID, entity.getId());
        if (entity.getVehicle() instanceof ScarletCentipedeEntity centipede) {
            state.passengerOffset = centipede.passengerPosition(entity, partialTicks).subtract(new Vec3(state.x, state.y, state.z));
            ((FabricRenderState)state).setData(CentipedeRiderRenderData.FRAME, CentipedeFrame.rotation(
                    centipede.passengerNormal(entity, partialTicks), centipede.passengerForward(entity, partialTicks)));
        } else ((FabricRenderState)state).setData(CentipedeRiderRenderData.FRAME, null);
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            Vec3 handFeet = net.krodark.asterion.client.render.entity.MinotaurHandAttachment.feet(entity);
            if (handFeet != null) state.passengerOffset = handFeet.subtract(new Vec3(state.x, state.y, state.z));
        }
    }
}
