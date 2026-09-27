package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.framebuffer.internal.Attachment;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.textures.TextureFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** OpenGL depth blits require identical source and destination depth formats. */
@Mixin(value = Attachment.class, remap = false)
public abstract class AmneticDepthAttachmentMixin {
    @ModifyConstant(method = "allocate", constant = @Constant(intValue = 33190), require = 2, expect = 2)
    private int asterion$matchMinecraftDepth(int original) {
        // Apply to both texture and renderbuffer allocation. DEPTH_COMPONENT24 is
        // incompatible with Minecraft's DEPTH32 even if a driver happens to accept it.
        return GlConst.toGlInternalId(TextureFormat.DEPTH32);
    }
}
