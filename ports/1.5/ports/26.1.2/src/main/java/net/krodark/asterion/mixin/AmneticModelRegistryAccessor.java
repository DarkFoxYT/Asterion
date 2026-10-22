package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.model.internal.ModelRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ModelRegistry.class, remap = false)
public interface AmneticModelRegistryAccessor {
    @Invoker("clearAllPending") void asterion$discardPending();
}
