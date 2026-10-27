package net.krodark.asterion.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.client.RenderResiduePass;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Marker;

public final class RenderRemnantRenderer extends EntityRenderer<Marker, EntityRenderState> {
    public RenderRemnantRenderer(EntityRendererProvider.Context context) {
        super(context); shadowRadius = 0; RenderResiduePass.initialize();
    }
    @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
    @Override public void submit(EntityRenderState state, PoseStack poses, SubmitNodeCollector tasks, CameraRenderState camera) {}
}
