package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.optifine.Config", remap = false)
public abstract class OptifineLabyrinthShadersMixin {
    @Inject(method = "isShaders", at = @At("HEAD"), cancellable = true)
    private static void asterion$authoredDimension(CallbackInfoReturnable<Boolean> callback) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.dimension().equals(Asterion.ASTERION_LEVEL)) callback.setReturnValue(false);
    }
}
