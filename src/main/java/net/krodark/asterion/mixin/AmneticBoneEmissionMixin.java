package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.krodark.asterion.client.light.AmneticBoneEmission;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = InstanceMeshRegistry.class, remap = false)
public abstract class AmneticBoneEmissionMixin {
    @WrapMethod(method = "renderEmissive")
    private void asterion$captureBones(InstancePhase phase, LevelRenderContext context,
                                     Framebuffer target, boolean all, Operation<Void> original) {
        boolean previous = AmneticBoneEmission.beginCapture();
        try { original.call(phase, context, target, all); }
        finally { AmneticBoneEmission.endCapture(previous); }
    }
}
