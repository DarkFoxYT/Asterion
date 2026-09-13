package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortRagdolls;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the solved limb directions after vanilla animation has populated the model. */
@Mixin(HumanoidModel.class)
public abstract class PortHumanoidRagdollMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void asterion$applyRigidPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                         float ageInTicks, float netHeadYaw, float headPitch,
                                         CallbackInfo callback) {
        if (PortRagdolls.isRagdolled(entity))
            PortRagdolls.applyHumanoidPose(entity, (HumanoidModel<?>)(Object)this,
                    net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }
}
