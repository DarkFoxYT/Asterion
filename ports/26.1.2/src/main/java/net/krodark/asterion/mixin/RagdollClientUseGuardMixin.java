package net.krodark.asterion.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class RagdollClientUseGuardMixin {
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void asterion$blockUseWhileThrown(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && (net.krodark.asterion.client.ragdoll.DismembermentEngine.INSTANCE.isPlayerTumbling(player.getId()) || net.krodark.asterion.entity.MinotaurEntity.isHeld(player))) {
            player.stopUsingItem(); ci.cancel();
        }
    }
}
