package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.level.material.FogType;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;
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
            color.set(.008F, .020F, .026F, 1F);
        }
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void asterion$deepSeaVisibility(Camera camera, int renderDistance, DeltaTracker deltaTracker,
                                           float darkenWorldAmount, ClientLevel level,
                                           CallbackInfoReturnable<FogData> result) {
        if (!level.dimension().equals(Asterion.LIMBO_LEVEL)) return;
        FogData fog = result.getReturnValue();
        if (camera.getFluidInCamera() == FogType.NONE) {
            // Let the depth-tested volume shape the near and middle distances.
            // Keep native fog as a far safety net so large structures retain a silhouette.
            Vec3 light = net.krodark.asterion.client.light.LedAmneticLight.nearestAttractor(camera.position(), 24);
            float relief = light == null ? 0F : (float)(1D - Math.clamp(
                    camera.position().distanceTo(light) / 24D, 0D, 1D));
            fog.environmentalStart = 52F + relief * 10F;
            fog.environmentalEnd = Math.min(fog.environmentalEnd, 136F + relief * 20F);
            fog.color.set(.075F, .079F, .084F, 1F);
            return;
        }
        if (camera.getFluidInCamera() != FogType.WATER) return;
        float depth = (float)Math.max(0, UnderworldTerrain.WATER_Y + 8.0 / 9.0 - camera.position().y);
        float blockLight = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(camera.position())) / 15F;
        var dynamic = net.krodark.asterion.client.light.LedAmneticLight.nearestAttractor(camera.position(), 18);
        float dynamicLight = dynamic == null ? 0F : (float)(1D - Math.clamp(camera.position().distanceTo(dynamic) / 18D, 0D, 1D));
        float light = Math.max(blockLight, dynamicLight);
        // Leave the distant water dim while allowing lit objects and Amnetic lights
        // to remain visible through the near field.
        float visibility = 13F + 13F * (float)Math.exp(-depth / 14F) + light * 10F;
        fog.environmentalStart = 2F;
        fog.environmentalEnd = Math.max(fog.environmentalEnd, visibility);
        fog.color.set(.007F + light * .020F, .019F + light * .027F,
                .024F + light * .030F, 1F);
    }
}
