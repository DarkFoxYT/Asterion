package net.krodark.asterion.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface PortGameRendererAccessor {
    @Invoker("getFov") double asterion$currentFov(Camera camera, float partialTick, boolean useSetting);
}
