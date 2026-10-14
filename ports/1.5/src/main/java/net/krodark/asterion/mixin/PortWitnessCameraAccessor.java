package net.krodark.asterion.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface PortWitnessCameraAccessor {
    @Invoker("getFov") double asterion$witnessFov(Camera camera, float partialTick, boolean useSettings);
}
