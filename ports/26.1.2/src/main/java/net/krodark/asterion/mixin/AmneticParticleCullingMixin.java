package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.meekdev.amnetic.client.instanced.internal.CullTargets;
import com.meekdev.amnetic.client.instanced.internal.GpuCuller;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshEntry;
import net.krodark.asterion.client.particle.AnimatedEmissiveParticle;
import net.krodark.asterion.client.render.ParticleCulling;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = InstanceMeshEntry.class, remap = false)
public abstract class AmneticParticleCullingMixin {
    @Shadow @Final private Identifier id;

    @Inject(method = "gpuCullEnabled", at = @At("RETURN"), cancellable = true)
    private void asterion$chooseCulling(CallbackInfoReturnable<Boolean> result) {
        if (!id.equals(AnimatedEmissiveParticle.MESH_ID)) return;
        boolean enabled = result.getReturnValueZ() && ParticleCulling.available();
        AnimatedEmissiveParticle.setGpuFrame(enabled);
        result.setReturnValue(enabled);
    }

    @WrapOperation(method = "renderGpuCulled", at = @At(value = "INVOKE", target =
            "Lcom/meekdev/amnetic/client/instanced/internal/GpuCuller;cull(Lcom/meekdev/amnetic/client/instanced/internal/CullTargets;IILorg/joml/Matrix4fc;DDD)V"))
    private void asterion$stableParticles(GpuCuller instance, CullTargets targets, int count, int stride,
                                         Matrix4fc matrix, double x, double y, double z, Operation<Void> original) {
        if (id.equals(AnimatedEmissiveParticle.MESH_ID)) ParticleCulling.cull(targets, count, stride, matrix);
        else original.call(instance, targets, count, stride, matrix, x, y, z);
    }

    @Inject(method = {"close", "invalidateShader"}, at = @At("TAIL"))
    private void asterion$releaseCulling(CallbackInfo callback) {
        if (id.equals(AnimatedEmissiveParticle.MESH_ID)) ParticleCulling.reset();
    }
}
