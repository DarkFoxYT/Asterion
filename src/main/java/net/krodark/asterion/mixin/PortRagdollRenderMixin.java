package net.krodark.asterion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.port.client.PortRagdolls;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PortRagdollRenderMixin {
    @Unique private boolean asterion$posed;

    @Inject(method = "render", at = @At("HEAD"))
    private void asterion$beginRagdoll(LivingEntity entity, float yaw, float partialTick,
                                      PoseStack poses, MultiBufferSource buffers, int light,
                                      CallbackInfo callback) {
        asterion$posed = PortRagdolls.isRagdolled(entity);
        if (!asterion$posed) return;
        poses.pushPose();
        poses.translate(0, .28, 0);
        poses.mulPose(Axis.ZP.rotationDegrees(86.0F));
        poses.mulPose(Axis.YP.rotationDegrees(entity.getId() % 2 == 0 ? 10.0F : -10.0F));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void asterion$endRagdoll(LivingEntity entity, float yaw, float partialTick,
                                    PoseStack poses, MultiBufferSource buffers, int light,
                                    CallbackInfo callback) {
        if (asterion$posed) poses.popPose();
        asterion$posed = false;
    }
}
