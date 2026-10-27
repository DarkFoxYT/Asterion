package net.krodark.asterion.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.ByteBuffer;
import net.krodark.asterion.client.light.StreamingInstanceUpload;
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.meekdev.amnetic.client.instanced.internal.InstanceBuffer", remap = false)
public abstract class AmneticInstanceBufferMixin {
    @Shadow @Final private int stride;
    @Shadow private int id;
    @Shadow private int capacityInstances;
    @Shadow abstract void ensureCapacity(int count);

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void asterion$streamRetainedBuffer(ByteBuffer data, int count, CallbackInfo ci) {
        if (!data.isDirect() || count < 0 || (long)count * stride < data.remaining()) return;
        ensureCapacity(count);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        try {
            StreamingInstanceUpload.upload(data, (long)capacityInstances * stride);
        } finally {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
        ci.cancel();
    }
}
