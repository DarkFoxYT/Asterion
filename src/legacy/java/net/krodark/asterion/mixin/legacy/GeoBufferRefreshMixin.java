package net.krodark.asterion.mixin.legacy;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Backport GeckoLib 4.9's buffer refresh after a per-bone layer changes material. */
@Mixin(targets={"software.bernie.geckolib.renderer.GeoEntityRenderer","software.bernie.geckolib.renderer.GeoBlockRenderer","software.bernie.geckolib.renderer.GeoItemRenderer"},remap=false)
public abstract class GeoBufferRefreshMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @ModifyVariable(method="renderRecursively",at=@At("HEAD"),argsOnly=true)
    private VertexConsumer asterion$restoreMaterial(VertexConsumer previous,
            @Local(argsOnly=true) MultiBufferSource buffers,
            @Local(argsOnly=true) RenderType material,
            @Local(argsOnly=true) boolean rerender) {
        var renderer = (software.bernie.geckolib.renderer.GeoRenderer)(Object)this;
        var texture = renderer.getTextureLocation(renderer.getAnimatable());
        return !rerender && material!=null && texture!=null && texture.getNamespace().equals("asterion")
                ? buffers.getBuffer(material) : previous;
    }
}
