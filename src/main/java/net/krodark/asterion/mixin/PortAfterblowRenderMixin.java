package net.krodark.asterion.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ItemRenderer.class)
public abstract class PortAfterblowRenderMixin {
    @WrapMethod(method="render")
    private void asterion$afterblow(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack poses,
            MultiBufferSource buffers,int light,int overlay,BakedModel model,Operation<Void> original) {
        if (!stack.is(Asterion.AFTERBLOW)) { original.call(stack,context,left,poses,buffers,light,overlay,model); return; }
        var client=Minecraft.getInstance();
        if (context==ItemDisplayContext.GUI) {
            var icon=stack.copy();
            int value=net.krodark.asterion.item.AfterblowItem.storedAt(stack,client.level==null?0:client.level.getGameTime())>.001F?3:2;
            //? if >=1.20.5 {
            icon.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,new net.minecraft.world.item.component.CustomModelData(value));
            //?} else {
            /*icon.getOrCreateTag().putInt("CustomModelData",value);*/
            //?}
            var base=client.getItemRenderer().getItemModelShaper().getItemModel(Asterion.AFTERBLOW);
            model=base.getOverrides().resolve(base,icon,client.level,client.player,0);
        }
        poses.pushPose();
        try {
            if ((context==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    && client.player!=null && client.player.isUsingItem() && client.player.getUseItem()==stack) {
                int sign=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND?-1:1;
                poses.translate(-sign*.18,.12,-.15);
                poses.mulPose(Axis.YP.rotationDegrees(-sign*45));
                poses.mulPose(Axis.ZP.rotationDegrees(sign*65));
            }
            original.call(stack,context,left,poses,buffers,light,overlay,model);
        } finally { poses.popPose(); }
    }
}

