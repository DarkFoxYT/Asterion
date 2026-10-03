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
        boolean sword;
        double centerY;
        net.minecraft.world.phys.Vec3[] chain;
        net.minecraft.world.phys.Vec3[][] trail;
        net.minecraft.world.phys.Vec3 releaseOffset = net.minecraft.world.phys.Vec3.ZERO;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(MinotaurAxeEntity axe, State state, float partial) {
        super.extractRenderState(axe, state, partial);
        state.rotation.set(axe.renderRotation(partial)); state.scale = axe.modelScale(); state.partial = partial;
        state.sword = axe.isSword(); state.centerY = axe.modelCenterY();
        state.chain = axe.chainPoints(partial);
        state.trail = axe.trailPoints(partial);
        if (state.chain != null) {
            var hand = WeaponChainAttachments.hand(axe.throwerId(), axe.chainSide());
            if (hand != null) {
                var correction = hand.subtract(state.chain[0]);
                for (int i = 0; i < state.chain.length; i++)
                    state.chain[i] = state.chain[i].add(correction.scale(Math.pow(1-i/(double)(state.chain.length-1),2)));
            }
        }
        state.releaseOffset = net.minecraft.world.phys.Vec3.ZERO;
        var release = axe.tickCount < 4 ? state.sword
                ? net.krodark.asterion.client.ragdoll.MinotaurSwordVisual.release(axe.throwerId(), axe.chainSide())
                : MinotaurAxeVisual.release(axe.throwerId()) : null;
        if (release != null) {
            float blend = net.krodark.asterion.port.compat.MathCompat.clamp((axe.tickCount + partial) / 4F, 0, 1);
            blend = blend * blend * (3 - 2 * blend);
            state.releaseOffset = release.center().subtract(new net.minecraft.world.phys.Vec3(state.x, state.y, state.z)).scale(1 - blend);
            state.rotation.set(release.rotation()).slerp(axe.renderRotation(partial), blend);
        }
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector tasks, CameraRenderState camera) {
        if(state.trail.length>1) {
            var samples=state.trail; var view=camera.pos;
            tasks.submitCustomGeometry(new PoseStack(),net.minecraft.client.renderer.rendertype.RenderTypes.lightning(),(pose,out)->{
                for(int i=1;i<samples.length;i++) {
                    int alpha=(int)(135*Math.pow(i/(double)(samples.length-1),2));
                    for(int index:new int[]{0,1,2,3,3,2,1,0}) {
                        var point=switch(index) {case 0->samples[i-1][0];case 1->samples[i-1][1];case 2->samples[i][1];default->samples[i][0];};
                        var v=point.subtract(view);
                        out.addVertex(pose,(float)v.x,(float)v.y,(float)v.z).setColor(255,state.sword?205:160,state.sword?135:70,
                                index==0||index==3?alpha/3:alpha);
                    }
                }
            });
        }
        if (state.chain != null) {
            var points = state.chain;
            var offset = new net.minecraft.world.phys.Vec3(-state.x, -state.y, -state.z);
            tasks.submitCustomGeometry(poses, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(
                    net.krodark.asterion.Asterion.id("textures/block/mazesteel_chain.png")),
                    (pose, out) -> ChainGeometry.drawWeapon(pose, out, points, offset, state.lightCoords));
        }
        poses.pushPose();
        poses.translate(state.releaseOffset.x, state.releaseOffset.y, state.releaseOffset.z);
        poses.mulPose(state.rotation);
        poses.scale(state.scale, state.scale, state.scale);
        poses.translate(0, -state.centerY, 0);
        if (state.sword) net.krodark.asterion.client.ragdoll.MinotaurSwordVisual.submit(poses, tasks, camera, state.lightCoords);
        else MinotaurAxeVisual.submitAligned(poses, tasks, camera, state.lightCoords, state.partial);
        poses.popPose();
        super.submit(state, poses, tasks, camera);
    }
}
