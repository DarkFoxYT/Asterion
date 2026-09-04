package net.krodark.asterion.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.block.ZiplineAnchorBlockEntity;
import net.krodark.asterion.zipline.ZiplineSystem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class ZiplineRenderer implements BlockEntityRenderer<ZiplineAnchorBlockEntity, ZiplineRenderer.State> {
    public static final class State extends BlockEntityRenderState { Vec3 start, end; Identifier texture; boolean visible; }
    public ZiplineRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(ZiplineAnchorBlockEntity anchor, State state, float partial,
                                             Vec3 camera, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(anchor, state, partial, camera, overlay);
        state.visible = anchor.primary() && anchor.other() != null;
        state.end = state.visible ? anchor.otherAttachment().subtract(anchor.getBlockPos().getCenter()) : Vec3.ZERO;
        state.start = state.visible ? anchor.attachment().subtract(anchor.getBlockPos().getCenter()) : Vec3.ZERO;
        Identifier id = Identifier.tryParse(anchor.chainId());
        state.texture = id == null ? Identifier.withDefaultNamespace("textures/block/iron_chain.png")
                : Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/block/" + id.getPath() + ".png");
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible) return;
        Identifier texture = state.texture;
        Vec3 end = state.end;
        collector.submitCustomGeometry(poses, RenderTypes.entityCutout(texture), (pose, out) -> {
            Vec3 start = state.start;
            // One section per block keeps the sag smooth without rebuilding hundreds of
            // translucent quads for every long cable on every frame.
            int segments = Math.clamp((int)Math.ceil(end.length()), 8, 64);
            for (int i = 0; i < segments; i++) {
                Vec3 a = ZiplineSystem.point(start, end, i / (double)segments);
                Vec3 b = ZiplineSystem.point(start, end, (i + 1D) / segments);
                ribbon(pose, out, a, b, i * .5F, (i + 1) * .5F, false);
                ribbon(pose, out, a, b, i * .5F, (i + 1) * .5F, true);
            }
        });
    }
    private static void ribbon(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b,
                               float u0, float u1, boolean crossed) {
        float radius = .055F;
        Vec3 direction = b.subtract(a).normalize();
        Vec3 first = direction.cross(new Vec3(0, 1, 0));
        if (first.lengthSqr() < 1.0E-5D) first = direction.cross(new Vec3(1, 0, 0));
        first = first.normalize().scale(radius);
        Vec3 side = crossed ? direction.cross(first).normalize().scale(radius) : first;
        vertex(pose,out,a.subtract(side),u0,0); vertex(pose,out,a.add(side),u0,1);
        vertex(pose,out,b.add(side),u1,1); vertex(pose,out,b.subtract(side),u1,0);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 p, float u, float v) {
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(255,255,255,255).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(pose,0,1,0);
    }
    // The cable can cross the camera even while its tiny persistence anchor is off-screen.
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 96; }
}
