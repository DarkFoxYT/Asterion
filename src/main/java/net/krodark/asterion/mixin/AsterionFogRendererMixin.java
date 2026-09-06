package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

 





@Mixin(FogRenderer.class)
abstract class AsterionFogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void asterion$forceCanonicalFogColor(Camera camera, float partialTick,
                                                  ClientLevel level, int renderDistance,
                                                  float darkenWorldAmount, Vector4f color,
                                                  CallbackInfo ci) {
        if (level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            color.set(0.0F, 0.0F, 0.0F, 1.0F);
        }
    }
}
