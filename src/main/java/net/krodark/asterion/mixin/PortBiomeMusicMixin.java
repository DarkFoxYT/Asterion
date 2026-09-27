package net.krodark.asterion.mixin;

import net.krodark.asterion.port.client.PortAudio;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class PortBiomeMusicMixin {
    @Unique private boolean asterion$ownedLastTick;
    @Shadow public abstract void stopPlaying();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void asterion$ownDimensionMusic(CallbackInfo callback) {
        boolean owned = PortAudio.ownsMusic();
        if (owned) {
            if (!asterion$ownedLastTick) stopPlaying();
            callback.cancel();
        }
        asterion$ownedLastTick = owned;
    }
}
