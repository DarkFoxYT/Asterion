package net.krodark.asterion.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.krodark.asterion.port.client.PortRagdolls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntityRenderer.class)
public abstract class PortRagdollRenderMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void asterion$physicalBody(LivingEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo callback) {
        if(PortRagdolls.isRagdolled(entity)) callback.cancel();
    }
}
