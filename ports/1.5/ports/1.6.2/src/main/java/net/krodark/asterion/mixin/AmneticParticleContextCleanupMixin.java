package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.particle.ParticleContext;
import com.meekdev.amnetic.client.particle.ParticleSimulation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The reusable simulation context needs its world only while stepping particles. */
@Mixin(value = ParticleSimulation.class, remap = false)
public abstract class AmneticParticleContextCleanupMixin {
    @Shadow @Final private ParticleContext ctx;

    @WrapMethod(method = "preRender")
    private void asterion$releaseSimulationWorld(InstanceRenderContext context, Operation<Void> original) {
        try {
            original.call(context);
        } finally {
            ((AmneticParticleContextAccessor)(Object)ctx).asterion$setWorld(null);
        }
    }
}
