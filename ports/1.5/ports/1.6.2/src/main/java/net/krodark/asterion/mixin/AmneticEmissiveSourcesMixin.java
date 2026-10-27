package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.emissive.EmissiveContext;
import com.meekdev.amnetic.client.emissive.EmissiveSources;
import net.krodark.asterion.client.light.AmneticBoneEmission;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(value = EmissiveSources.class, remap = false)
public abstract class AmneticEmissiveSourcesMixin {
    @Shadow @Final private static Map<Identifier, Consumer<EmissiveContext>> SOURCES;

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private static void asterion$skipIdleSource(CallbackInfoReturnable<Boolean> cir) {
        if (SOURCES.size() == 1 && SOURCES.containsKey(AmneticBoneEmission.SOURCE_ID)
                && !AmneticBoneEmission.hasPending()) cir.setReturnValue(true);
    }
}
