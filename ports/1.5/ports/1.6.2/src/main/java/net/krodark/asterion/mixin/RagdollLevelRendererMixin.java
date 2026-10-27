package net.krodark.asterion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.client.ragdoll.RagdollRenderer;
import net.krodark.asterion.client.ragdoll.PhysicsDebrisSystem;
import net.krodark.asterion.client.light.HeldItemDynamicLights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class RagdollLevelRendererMixin {
    //? if <26.1 {
    /*@Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void asterion$cullingFrustum(net.minecraft.client.Camera camera,
            net.minecraft.client.renderer.culling.Frustum frustum, net.minecraft.client.DeltaTracker delta,
            LevelRenderState state, CallbackInfo callback) {
        net.krodark.asterion.client.render.FrameRenderAccess.recordFrustum(frustum);
    }
    *///?}

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void asterion$submitRagdolls(PoseStack poses, LevelRenderState state, SubmitNodeCollector output, CallbackInfo ci) {
        net.krodark.asterion.client.PerformanceGovernor.frame(Minecraft.getInstance());
        RagdollRenderer.submit(poses, state, output);
        PhysicsDebrisSystem.submit(poses, state, output);

        net.krodark.asterion.client.forge.ForgeItemFlights.submit(poses, state, output);
        net.krodark.asterion.client.cinematic.studio.CutsceneStudio.submit(poses, state, output);
        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer != null)
            HeldItemDynamicLights.renderFrame(client,
                    client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }
}
