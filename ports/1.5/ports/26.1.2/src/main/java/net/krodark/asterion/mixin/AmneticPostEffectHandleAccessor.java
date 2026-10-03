package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.post.PostEffectHandle;
import com.meekdev.amnetic.client.post.internal.PostEffectEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PostEffectHandle.class, remap = false)
public interface AmneticPostEffectHandleAccessor {
    @Accessor("entry") PostEffectEntry asterion$entry();
}
