package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Override only rendered gamma, preserving options.txt and vanilla darkness/night-vision effects. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class MoodyBrightnessMixin {
    @ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE",
            target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
    private float asterion$brightness(float vanillaBrightness) {
        int brightness = AsterionConfig.INSTANCE.brightnessPercent;
        return brightness < 0 ? vanillaBrightness : Math.clamp(brightness, 0, 100) / 100F;
    }
}
