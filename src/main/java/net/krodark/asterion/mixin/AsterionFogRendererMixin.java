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

/**
 * Keeps Asterion's framebuffer clear/fog colour independent of the client's
 * biome blending, resource packs, and graphics implementation. The custom
 * atmosphere post effect is authored on top of a black world background; if
 * vanilla supplies its blue fallback instead, the entire sky is tinted blue.
 */
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
