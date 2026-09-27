package net.krodark.asterion.forge.mixin;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderState.class)
public abstract class ForgeRenderStateMixin implements FabricRenderState {}
