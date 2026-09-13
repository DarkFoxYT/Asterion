package net.krodark.asterion.mixin;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Depth-based Asterion fog for 1.21.1; never replaces the scene framebuffer. */
@Mixin(FogRenderer.class)
public abstract class PortFogRendererMixin {
    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void asterion$spatialFogColor(Camera camera, float partialTick, ClientLevel level,
                                                 int renderDistance, float darkenWorldAmount,
                                                 CallbackInfo callback) {
        if (level.dimension().equals(Asterion.ASTERION_LEVEL) && camera.getFluidInCamera() == FogType.NONE)
            RenderSystem.setShaderFogColor(.058F, .041F, .030F);
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void asterion$spatialFogDistance(Camera camera, FogRenderer.FogMode mode,
                                                    float viewDistance, boolean thickFog,
                                                    float partialTick, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)
                || camera.getFluidInCamera() != FogType.NONE) return;
        float end = Math.max(48.0F, Math.min(104.0F, viewDistance * .82F));
        RenderSystem.setShaderFogStart(Math.max(14.0F, end * .28F));
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
