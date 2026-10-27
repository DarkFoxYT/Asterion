//? if <26.1 {
package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.compat.LegacyEnvironmentAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LightTexture.class)
abstract class LegacyAmbientLightMixin {
    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;",
            ordinal = 1), index = 0)
    private Vector3fc asterion$ambientColor(Vector3fc original) {
        var client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return original;
        int color = client.gameRenderer.getMainCamera().attributeProbe().getValue(
                LegacyEnvironmentAttributes.AMBIENT_LIGHT_COLOR,
                client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        return net.minecraft.util.ARGB.vector3fFromRGB24(color);
    }
}
//?}
