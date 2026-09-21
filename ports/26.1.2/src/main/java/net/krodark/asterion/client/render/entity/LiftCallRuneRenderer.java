package net.krodark.asterion.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AmneticBoneEmission;
import net.krodark.asterion.client.light.EmissiveBoneMesh;
import net.krodark.asterion.entity.LiftCallRuneEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class LiftCallRuneRenderer extends EntityRenderer<LiftCallRuneEntity, EntityRenderState> {
    private static final Identifier TEXTURE = Asterion.id("textures/pin/elevator_call.png");
    private static final Identifier MESH_ID = Asterion.id("lift_call_rune");
    private static final EmissiveBoneMesh MESH = EmissiveBoneMesh.texturedRune();
    public LiftCallRuneRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
    @Override public void submit(EntityRenderState state, PoseStack poses, SubmitNodeCollector tasks, CameraRenderState camera) {
        poses.pushPose();
        poses.translate(0, .35 + Math.sin(state.ageInTicks * .045) * .035, 0);
        poses.mulPose(camera.orientation);
        tasks.submitCustomGeometry(poses, RenderTypes.entityTranslucentEmissive(TEXTURE),
                (pose, out) -> MESH.render(pose, out, 0xFFD9D9D9, 1, 1));
        AmneticBoneEmission.submit(MESH_ID, MESH, TEXTURE, poses.last().pose(), 0xFFFFFFFF, 1, 1, .18F, false);
        poses.popPose();
    }
}
