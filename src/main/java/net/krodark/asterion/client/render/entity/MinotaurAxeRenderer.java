package net.krodark.asterion.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.client.ragdoll.MinotaurAxeVisual;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;

public final class MinotaurAxeRenderer extends EntityRenderer<MinotaurAxeEntity, MinotaurAxeRenderer.State> {
    public MinotaurAxeRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = .6F; }
    public static final class State extends EntityRenderState {
        Quaternionf rotation = new Quaternionf(); float scale, partial;
        net.minecraft.world.phys.Vec3 releaseOffset = net.minecraft.world.phys.Vec3.ZERO;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(MinotaurAxeEntity axe, State state, float partial) {
        super.extractRenderState(axe, state, partial);
        state.rotation.set(axe.renderRotation(partial)); state.scale = axe.modelScale(); state.partial = partial;
        state.releaseOffset = net.minecraft.world.phys.Vec3.ZERO;
        var release = axe.tickCount < 4 ? MinotaurAxeVisual.release(axe.throwerId()) : null;
        if (release != null) {
            float blend = Math.clamp((axe.tickCount + partial) / 4F, 0, 1);
            blend = blend * blend * (3 - 2 * blend);
            state.releaseOffset = release.center().subtract(new net.minecraft.world.phys.Vec3(state.x, state.y, state.z)).scale(1 - blend);
            state.rotation.set(release.rotation()).slerp(axe.renderRotation(partial), blend);
        }
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector tasks, CameraRenderState camera) {
        poses.pushPose();
        poses.translate(state.releaseOffset.x, state.releaseOffset.y, state.releaseOffset.z);
        poses.mulPose(state.rotation);
        poses.scale(state.scale, state.scale, state.scale);
        poses.translate(0, -MinotaurAxeEntity.CENTER_Y, 0);
        MinotaurAxeVisual.submit(poses, tasks, camera, state.lightCoords, state.partial);
        poses.popPose();
        super.submit(state, poses, tasks, camera);
    }
}
