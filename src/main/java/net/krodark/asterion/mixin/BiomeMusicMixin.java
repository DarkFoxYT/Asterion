package net.krodark.asterion.mixin;

import net.krodark.asterion.client.BiomeMusic;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class BiomeMusicMixin {
    @Unique private boolean asterion$wasInMaze;
    @Shadow public abstract void stopPlaying();
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void asterion$ownMazeMusic(CallbackInfo ci) {
        boolean maze = BiomeMusic.ownsMusic();
        if (maze) {
            if (!asterion$wasInMaze) stopPlaying();
            ci.cancel();
        }
        asterion$wasInMaze = maze;
    }
}
