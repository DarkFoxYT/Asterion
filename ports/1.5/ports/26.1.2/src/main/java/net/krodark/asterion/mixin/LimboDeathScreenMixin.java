package net.krodark.asterion.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the first-death Limbo handoff cinematic: no menu click interrupts the transition. */
@Mixin(DeathScreen.class)
abstract class LimboDeathScreenMixin {
    @Unique private int asterion$deathTicks;

    @Inject(method = "tick", at = @At("TAIL"))
    private void asterion$automaticPassage(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (++asterion$deathTicks < 24 || client.player == null || client.level == null) return;
        // Labyrinth deaths have their own obelisk recovery. Limbo deaths remain conventional.
        if (client.level.dimension().equals(net.krodark.asterion.Asterion.ASTERION_LEVEL)
                || client.level.dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL)) return;
        client.player.respawn();
        asterion$deathTicks = Integer.MIN_VALUE / 2;
    }
}
