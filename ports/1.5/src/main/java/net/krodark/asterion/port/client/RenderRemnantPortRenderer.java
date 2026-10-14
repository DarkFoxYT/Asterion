package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Marker;

public final class RenderRemnantPortRenderer extends EntityRenderer<Marker> {
    //? if >=1.20.5 {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    //? } else {
    /*private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
    *///? }
    public RenderRemnantPortRenderer(EntityRendererProvider.Context context) {
        super(context); shadowRadius = 0; RenderResiduePass.initialize();
    }
    @Override public ResourceLocation getTextureLocation(Marker entity) { return TEXTURE; }
    @Override public void render(Marker entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {}
}
