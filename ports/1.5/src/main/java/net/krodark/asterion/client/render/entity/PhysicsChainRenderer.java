package net.krodark.asterion.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.PhysicsChainEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public final class PhysicsChainRenderer extends EntityRenderer<PhysicsChainEntity, PhysicsChainRenderer.State> {
    public static final class State extends EntityRenderState { Vec3[] points; }
    public PhysicsChainRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(PhysicsChainEntity chain) { return chain.getBoundingBox().inflate(.5); }
    @Override public void extractRenderState(PhysicsChainEntity chain, State state, float partial) {
        super.extractRenderState(chain, state, partial); state.points = chain.points(partial);
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector tasks, CameraRenderState camera) {
        Vec3 offset = new Vec3(-state.x, -state.y, -state.z);
        Vec3[] points = state.points;
        tasks.submitCustomGeometry(poses, RenderTypes.entityCutout(Asterion.id("textures/block/mazesteel_chain.png")),
                (pose, out) -> ChainGeometry.drawHanging(pose, out, points, offset, camera.pos.add(offset), state.lightCoords));
    }
}
