package net.krodark.labyrinth.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.krodark.labyrinth.client.ragdoll.DismembermentEngine;
import net.krodark.labyrinth.client.ragdoll.RagdollRenderData;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@Mixin(LivingEntityRenderer.class)
abstract class RagdollLivingEntityRendererMixin {
    @Shadow protected EntityModel<?> model;
    @Unique private final Map<ModelPart, Boolean> labyrinth$visibility = new IdentityHashMap<>();

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void labyrinth$hidePhysicalBody(LivingEntityRenderState state, PoseStack poses,
                                             SubmitNodeCollector output, CameraRenderState camera, CallbackInfo ci) {
        Integer id = ((FabricRenderState) state).getData(RagdollRenderData.ENTITY_ID);
        if (id == null) return;
        if (DismembermentEngine.INSTANCE.isPlayerTumbling(id)) { ci.cancel(); return; }
        Set<Integer> hidden = DismembermentEngine.INSTANCE.hiddenRegions(id);
        labyrinth$visibility.clear();
        if (!hidden.isEmpty() && DismembermentEngine.INSTANCE.isRagdolled(id)) { mask(model.root()); return; }
        if (model instanceof PlayerModel player) for (int region : hidden) maskPlayer(player, region);
        else if (model instanceof HumanoidModel<?> humanoid) for (int region : hidden) maskHumanoid(humanoid, region);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void labyrinth$restoreBody(LivingEntityRenderState state, PoseStack poses,
                                        SubmitNodeCollector output, CameraRenderState camera, CallbackInfo ci) {
        Integer id = ((FabricRenderState) state).getData(RagdollRenderData.ENTITY_ID);
        if (id != null) DismembermentEngine.INSTANCE.captureRenderedPose(id);
        labyrinth$visibility.forEach((part, visible) -> part.visible = visible);
        labyrinth$visibility.clear();
    }

    @Unique private void mask(ModelPart part) { labyrinth$visibility.putIfAbsent(part, part.visible); part.visible = false; }
    @Unique private void maskPlayer(PlayerModel p, int r) {
        switch (r) {
            case 0 -> { mask(p.head); mask(p.hat); } case 2 -> { mask(p.rightArm); mask(p.rightSleeve); }
            case 3 -> { mask(p.leftArm); mask(p.leftSleeve); } case 4 -> { mask(p.rightLeg); mask(p.rightPants); }
            case 5 -> { mask(p.leftLeg); mask(p.leftPants); } default -> { mask(p.body); mask(p.jacket); }
        }
    }
    @Unique private void maskHumanoid(HumanoidModel<?> p, int r) {
        switch (r) { case 0 -> { mask(p.head); mask(p.hat); } case 2 -> mask(p.rightArm);
            case 3 -> mask(p.leftArm); case 4 -> mask(p.rightLeg); case 5 -> mask(p.leftLeg); default -> mask(p.body); }
    }
}
