package net.krodark.asterion.mixin;

//? if <26.1 {
/*import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererFovInvoker {
    @Invoker("getFov") float asterion$fov(Camera camera, float partialTick, boolean useSetting);
}
*///?}
