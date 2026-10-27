//? if >=26.3 {
/*package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class NativeAfterblowFirstPersonMixin {
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void asterion$guard(ItemStackRenderState item, PoseStack poses, SubmitNodeCollector collector,
            int light, int overlay, int outline, Operation<Void> original,
            PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState hands,
            float partial, float pitch, InteractionHand hand, float swing, ItemStack stack, float height,
            PoseStack outerPoses, SubmitNodeCollector outerCollector, int outerLight) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem() || player.getUsedItemHand() != hand
                || !stack.is(net.krodark.asterion.Asterion.AFTERBLOW)
                || !player.getUseItem().is(net.krodark.asterion.Asterion.AFTERBLOW)) {
            original.call(item, poses, collector, light, overlay, outline);
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int sign = arm == HumanoidArm.LEFT ? -1 : 1;
        poses.pushPose();
        try {
            poses.translate(-sign * .22, .06, -.15);
            poses.rotate(com.mojang.math.Axis.ZP.rotationDegrees(sign * 65));
            original.call(item, poses, collector, light, overlay, outline);
        } finally { poses.popPose(); }
    }
}
*///?}
