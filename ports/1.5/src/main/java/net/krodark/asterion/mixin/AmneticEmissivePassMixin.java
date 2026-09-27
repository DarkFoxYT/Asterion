package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshEntry;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.render.LevelCamera;
import net.krodark.asterion.port.client.light.EmissivePassFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InstanceMeshRegistry.class, remap = false)
public abstract class AmneticEmissivePassMixin {
    @Inject(method = "renderAll(Lcom/meekdev/amnetic/client/instanced/InstancePhase;Lcom/meekdev/amnetic/client/render/LevelCamera;)V", at = @At("HEAD"))
    private void asterion$beginFrame(InstancePhase phase, LevelCamera context, CallbackInfo ci) {
        if (phase == InstancePhase.WORLD_LAST) EmissivePassFrame.frame++;
    }

    @WrapOperation(method = "renderEmissive", at = @At(value = "INVOKE", target =
            "Lcom/meekdev/amnetic/client/instanced/internal/InstanceMeshEntry;render(Lcom/meekdev/amnetic/client/instanced/InstanceRenderContext;)V"))
    private void asterion$reuseInstances(InstanceMeshEntry<?> entry, InstanceRenderContext context, Operation<Void> original) {
        boolean previous = EmissivePassFrame.replay;
        EmissivePassFrame.replay = true;
        try { original.call(entry, context); }
        finally { EmissivePassFrame.replay = previous; }
    }
}
