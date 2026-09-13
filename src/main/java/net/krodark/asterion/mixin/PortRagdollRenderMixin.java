package net.krodark.asterion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.port.client.PortRagdolls;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
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
        PortRagdolls.RenderPose pose = PortRagdolls.renderPose(entity, partialTick);
        if (pose == null) return;
        Vec3 base = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() * .52D,
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
        Vec3 delta = pose.pivot().subtract(base);
        poses.translate(delta.x, delta.y + entity.getBbHeight() * .52D, delta.z);
        poses.mulPose(pose.orientation());
        poses.translate(0, -entity.getBbHeight() * .52D, 0);
    }

    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void asterion$usePhysicsRotation(LivingEntity entity, PoseStack poses, float bob,
                                             float bodyRot, float partialTick, float scale,
                                             CallbackInfo callback) {
        if (PortRagdolls.isRagdolled(entity)) callback.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void asterion$endRagdoll(LivingEntity entity, float yaw, float partialTick,
                                    PoseStack poses, MultiBufferSource buffers, int light,
                                    CallbackInfo callback) {
        if (asterion$posed) poses.popPose();
        asterion$posed = false;
    }
}
