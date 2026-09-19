package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.level.material.FogType;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

 





@Mixin(FogRenderer.class)
abstract class AsterionFogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void asterion$forceCanonicalFogColor(Camera camera, float partialTick,
                                                  ClientLevel level, int renderDistance,
                                                  float darkenWorldAmount, Vector4f color,
                                                  CallbackInfo ci) {
        if (level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            color.set(0.0F, 0.0F, 0.0F, 1.0F);
        } else if (level.dimension().equals(Asterion.LIMBO_LEVEL)
                && camera.getFluidInCamera() == FogType.WATER) {
            color.set(.003F, .009F, .011F, 1F);
        }
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void asterion$deepSeaVisibility(Camera camera, int renderDistance, DeltaTracker deltaTracker,
                                           float darkenWorldAmount, ClientLevel level,
                                           CallbackInfoReturnable<FogData> result) {
        if (!level.dimension().equals(Asterion.LIMBO_LEVEL)
                || camera.getFluidInCamera() != FogType.WATER) return;
        FogData fog = result.getReturnValue();
        float depth = (float)Math.max(0, UnderworldTerrain.WATER_Y + 8.0 / 9.0 - camera.position().y);
        // A little visibility at the surface, falling to three blocks in the lightless depths.
        float visibility = 3F + 4F * (float)Math.exp(-depth / 5F);
        fog.environmentalStart = 0F;
        fog.environmentalEnd = Math.min(fog.environmentalEnd, visibility);
        fog.color.set(.003F, .009F, .011F, 1F);
    }
}
