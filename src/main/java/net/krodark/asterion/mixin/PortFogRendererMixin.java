package net.krodark.asterion.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the dimension clear/black underneath its depth-aware Veil atmosphere. */
@Mixin(FogRenderer.class)
public abstract class PortFogRendererMixin {
    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void asterion$spatialFogColor(Camera camera, float partialTick, ClientLevel level,
                                                 int renderDistance, float darkenWorldAmount,
                                                 CallbackInfo callback) {
        if (level.dimension().equals(Asterion.ASTERION_LEVEL) && camera.getFluidInCamera() == FogType.NONE)
            RenderSystem.setShaderFogColor(0.0F, 0.0F, 0.0F);
    }
}
