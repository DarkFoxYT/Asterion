package net.krodark.asterion.neoforge.mixin;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({EntityRenderState.class, BlockEntityRenderState.class})
public abstract class NativeRenderStateMixin implements FabricRenderState {
    @Unique private java.util.Map<RenderStateDataKey<?>, Object> asterion$nativeRenderData;

    @Override
    public java.util.Map<RenderStateDataKey<?>, Object> asterion$nativeData(boolean create) {
        if (asterion$nativeRenderData == null && create)
            asterion$nativeRenderData = new java.util.IdentityHashMap<>();
        return asterion$nativeRenderData;
    }
}
