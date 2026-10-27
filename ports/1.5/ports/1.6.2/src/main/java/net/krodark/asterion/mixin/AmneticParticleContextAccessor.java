package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.particle.ParticleContext;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ParticleContext.class, remap = false)
public interface AmneticParticleContextAccessor {
    @Accessor("world") void asterion$setWorld(ClientLevel world);
}
