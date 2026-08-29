package net.krodark.asterion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds tumbling only to the short-lived debris inside Asterion's boss arena. */
@Mixin(FallingBlockRenderer.class)
abstract class FallingBlockRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/FallingBlockRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void asterion$rotateBossRubble(FallingBlockRenderState state, PoseStack poseStack,
                                           SubmitNodeCollector collector, CameraRenderState camera,
                                           CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.level.dimension() != Asterion.ASTERION_LEVEL
                || state.x * state.x + state.z * state.z > 58.0D * 58.0D) return;

        long seed = Double.doubleToLongBits(state.x * 31.0D + state.y * 13.0D + state.z * 17.0D);
        float direction = (seed & 1L) == 0L ? 1.0F : -1.0F;
        float speed = 0.72F + (float)Math.floorMod(seed >>> 9, 29L) * 0.026F;
        float age = Math.max(0.0F, state.ageInTicks);
        float dampedAngle = speed * (float)(18.0D * Math.log1p(age / 18.0D));
        // Vanilla renders from the block's bottom-center. Rotate around its center of mass.
        poseStack.translate(0.0D, 0.5D, 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(dampedAngle * direction));
        poseStack.mulPose(Axis.ZP.rotationDegrees(dampedAngle * 0.54F));
        poseStack.mulPose(Axis.YP.rotationDegrees(dampedAngle * 0.24F * direction));
        poseStack.translate(0.0D, -0.5D, 0.0D);
    }
}
