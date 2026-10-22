package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import net.krodark.asterion.port.client.AmneticFramebufferState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayDeque;

@Mixin(value = MainTargetFramebuffer.class, remap = false)
public abstract class AmneticMainFramebufferMixin {
    @Unique private static final ArrayDeque<AmneticFramebufferState> asterion$bindings = new ArrayDeque<>();

    @WrapMethod(method = "bind")
    private static int asterion$saveShaderPass(Operation<Integer> original) {
        var state = AmneticFramebufferState.capture();
        try {
            int result = original.call();
            if (result < 0) { state.restore(); return result; }
            asterion$bindings.push(state);
            return state.draw();
        } catch (Throwable failure) { state.restore(); throw failure; }
    }

    @WrapMethod(method = "restore")
    private static void asterion$restoreShaderPass(int previous, Operation<Void> original) {
        var state = previous < 0 ? null : asterion$bindings.poll();
        try { original.call(previous); }
        finally { if (state != null) state.restore(); }
    }
}
