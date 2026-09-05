package net.krodark.asterion.client.render.entity;

import net.krodark.asterion.Asterion;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.resources.Identifier;

public final class AncientSkeletonRenderer extends SkeletonRenderer {
    public AncientSkeletonRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public Identifier getTextureLocation(SkeletonRenderState state) {
        return Asterion.id("textures/block/skeleton.png");
    }
}
