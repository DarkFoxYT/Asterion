package net.krodark.asterion.mixin;

import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshEntry;
import net.krodark.asterion.client.light.EmissivePassFrame;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = InstanceMeshEntry.class, remap = false)
public abstract class AmneticEmissiveInstanceMixin {
    @Shadow @Final private Identifier id;
    @Shadow @Final private Matrix4f projViewScratch;
    @Shadow private int lastInstanceCount;
    @Unique private long asterion$preparedFrame = -1;
    @Unique private ClientLevel asterion$world;
    @Unique private Vec3 asterion$camera;
    @Unique private final Matrix4f asterion$projection = new Matrix4f();
    @Unique private final Matrix4f asterion$view = new Matrix4f();

    @Invoker("drawNow")
    protected abstract void asterion$drawPrepared(Matrix4f combined, Matrix4fc projection, Matrix4fc view,
                                                float time, Vec3 camera, int count);

    @Inject(method = "renderCpu", at = @At("HEAD"), cancellable = true)
    private void asterion$reuse(InstanceRenderContext context, CallbackInfo ci) {
        if (!EmissivePassFrame.replay || asterion$preparedFrame != EmissivePassFrame.frame
                || context.world() != asterion$world || !context.cameraPos().equals(asterion$camera)
                || !asterion$projection.equals(context.projectionMatrix())
                || !asterion$view.equals(context.viewMatrix())) return;
        if (lastInstanceCount > 0) asterion$drawPrepared(projViewScratch, context.projectionMatrix(),
                context.viewMatrix(), context.gameTime(), context.cameraPos(), lastInstanceCount);
        ci.cancel();
    }

    @Inject(method = "renderCpu", at = @At("RETURN"))
    private void asterion$remember(InstanceRenderContext context, CallbackInfo ci) {
        if (EmissivePassFrame.replay || !id.getNamespace().equals("asterion")) return;
        asterion$preparedFrame = EmissivePassFrame.frame;
        asterion$world = context.world();
        asterion$camera = context.cameraPos();
        asterion$projection.set(context.projectionMatrix());
        asterion$view.set(context.viewMatrix());
    }

    @Inject(method = {"invalidate", "invalidateShader", "close"}, at = @At("HEAD"))
    private void asterion$invalidate(CallbackInfo ci) {
        asterion$preparedFrame = -1;
        asterion$world = null;
    }
}
