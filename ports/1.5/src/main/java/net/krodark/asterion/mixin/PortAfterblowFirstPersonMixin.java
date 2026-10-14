package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandRenderer.class)
public abstract class PortAfterblowFirstPersonMixin {
    @WrapMethod(method="renderItem")
    private void asterion$guard(LivingEntity user, ItemStack stack, ItemDisplayContext context,
            boolean left, PoseStack poses, MultiBufferSource buffers, int light, Operation<Void> original) {
        if (!stack.is(net.krodark.asterion.Asterion.AFTERBLOW) || !user.isUsingItem()
                || !user.getUseItem().is(net.krodark.asterion.Asterion.AFTERBLOW)
                || (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
            original.call(user, stack, context, left, poses, buffers, light);
            return;
        }
        boolean leftGuard = (user.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? user.getMainArm() : user.getMainArm().getOpposite()) == net.minecraft.world.entity.HumanoidArm.LEFT;
        if (leftGuard != (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
            original.call(user, stack, context, left, poses, buffers, light);
            return;
        }
        int sign = leftGuard ? -1 : 1;
        poses.pushPose();
        try {
            // Present the blade across the chest without tipping its face away from the camera.
            poses.translate(-sign * .22, .06, -.15);
            poses.mulPose(Axis.ZP.rotationDegrees(sign * 65));
            original.call(user, stack, context, left, poses, buffers, light);
        } finally { poses.popPose(); }
    }
}
