package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.krodark.asterion.port.client.PortTextureFrameCache;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

/** Scope legacy GeckoLib optimizations to the texture's owning mod, including nested calls. */
@Mixin(value = software.bernie.geckolib.cache.texture.AnimatableTexture.class, remap = false)
public abstract class PortGeckoTextureScopeMixin extends SimpleTexture {
    protected PortGeckoTextureScopeMixin(ResourceLocation location) { super(location); }

    @WrapMethod(method = "setAnimationFrame")
    private void asterion$scopeAnimation(int tick, Operation<Void> original) {
        PortTextureFrameCache.withTexture("asterion".equals(location.getNamespace()), () -> original.call(tick));
    }
}
