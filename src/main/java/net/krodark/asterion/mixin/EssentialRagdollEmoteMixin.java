package net.krodark.asterion.mixin;

import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Essential is optional. A physical knockdown takes precedence over starting an emote. */
@Pseudo
@Mixin(targets = "gg.essential.gui.emotes.EmoteWheel$Companion", remap = false)
abstract class EssentialRagdollEmoteMixin {
    @Inject(method = "canEmote", at = @At("HEAD"), cancellable = true, require = 0)
    private void asterion$blockEmoteWhileTumbling(AbstractClientPlayer player, CallbackInfoReturnable<Boolean> ci) {
        if (DismembermentEngine.INSTANCE.isPlayerTumbling(player.getId())) ci.setReturnValue(false);
    }
}
