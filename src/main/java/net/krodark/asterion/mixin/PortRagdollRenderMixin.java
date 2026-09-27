package net.krodark.asterion.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.krodark.asterion.port.client.PortRagdolls;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(LivingEntityRenderer.class)
public abstract class PortRagdollRenderMixin {
    @WrapMethod(method="render")
    private void asterion$physicalBody(LivingEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light,Operation<Void> original) {
        if(PortRagdolls.isRagdolled(entity)) return;
        poses.pushPose();
        try {
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            var feet = net.krodark.asterion.port.client.ragdoll.MinotaurHandAttachment.feet(entity);
            if (feet != null) {
                var offset = feet.subtract(entity.getPosition(partial));
                poses.translate(offset.x, offset.y, offset.z);
            } else {
                var lift = net.krodark.asterion.entity.ChainLiftEntity.renderSupport(entity);
                if (lift != null) poses.translate(0, lift.renderedDeckY(partial) - entity.getPosition(partial).y, 0);
            }
        }
            original.call(entity,yaw,partial,poses,buffers,light);
        } finally {
            poses.popPose();
        }
    }
}
