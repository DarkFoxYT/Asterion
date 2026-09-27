package net.krodark.asterion.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ItemInHandRenderer.class)
public abstract class AfterblowFirstPersonMixin {
    @WrapMethod(method="renderItem")
    private void asterion$parry(LivingEntity user,ItemStack stack,ItemDisplayContext context,PoseStack poses,
                                SubmitNodeCollector output,int light,Operation<Void> original) {
        poses.pushPose();
        try {
            if (stack.is(net.krodark.asterion.Asterion.AFTERBLOW) && user.isUsingItem() && user.getUseItem()==stack
                    && (context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
                int sign=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND?-1:1;
                poses.translate(-sign*.18,.12,-.15);
                poses.mulPose(Axis.YP.rotationDegrees(-sign*45)); poses.mulPose(Axis.ZP.rotationDegrees(sign*65));
            }
            original.call(user,stack,context,poses,output,light);
        } finally { poses.popPose(); }
    }
}
