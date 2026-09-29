package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

 
@Mixin(LightmapRenderStateExtractor.class)
public abstract class MoodyBrightnessMixin {
    @ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE",
            target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
    private float asterion$brightness(float vanillaBrightness) {
        var client = net.minecraft.client.Minecraft.getInstance();
        var level = client == null ? null : client.level;
        boolean insideAsterion = level != null && (level.dimension().equals(net.krodark.asterion.Asterion.ASTERION_LEVEL)
                || level.dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL));
        return net.krodark.asterion.util.LightingPolicy.brightness(
                insideAsterion, AsterionConfig.INSTANCE.brightnessPercent, vanillaBrightness);
    }
}
