package net.krodark.asterion.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
abstract class ForgePlayerRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void asterion$hideLocalPlayer(Entity entity, Frustum frustum, double x, double y, double z,
                                         CallbackInfoReturnable<Boolean> ci) {
        if (entity == Minecraft.getInstance().player && net.krodark.asterion.client.CrucibleCamera.active())
            ci.setReturnValue(false);
    }
}
