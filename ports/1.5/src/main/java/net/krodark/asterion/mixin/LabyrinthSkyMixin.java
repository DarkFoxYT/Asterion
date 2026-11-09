package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep vanilla celestial bodies out of the authored, enclosed dimension sky. */
@Mixin(LevelRenderer.class)
public abstract class LabyrinthSkyMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void asterion$authoredSkyOnly(CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.dimension().equals(Asterion.ASTERION_LEVEL)) callback.cancel();
    }
}
