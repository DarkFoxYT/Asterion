package net.krodark.asterion.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayerGameMode.class)
public abstract class RagdollUseGuardMixin {
    @Shadow public ServerPlayer player;
    @Inject(method={"useItem", "useItemOn"},at=@At("HEAD"),cancellable=true)
    private void asterion$blockUseWhileThrown(CallbackInfoReturnable<net.minecraft.world.InteractionResult> ci) {
        if (net.krodark.asterion.network.ragdoll.RagdollServerNetworking.isRagdolled(player)
                || net.krodark.asterion.entity.MinotaurEntity.isHeld(player)) {
            player.stopUsingItem(); ci.setReturnValue(net.minecraft.world.InteractionResult.FAIL);
        }
    }
}
