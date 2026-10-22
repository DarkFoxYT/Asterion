package net.krodark.asterion.neoforge;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = "asterion", value = Dist.CLIENT)
public final class NativeRenderEvents {
    private NativeRenderEvents() {}

    private static LevelRenderContext context(RenderLevelStageEvent event) {
        return new LevelRenderContext() {
            public net.minecraft.client.renderer.state.level.LevelRenderState levelState() {
                return event.getLevelRenderState();
            }

            public com.mojang.blaze3d.vertex.PoseStack poseStack() {
                return event.getPoseStack();
            }

            public net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource() {
                return Minecraft.getInstance().renderBuffers().bufferSource();
            }
        };
    }

    @SubscribeEvent
    public static void before(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        LevelRenderEvents.before(context(event));
    }

    @SubscribeEvent
    public static void after(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        LevelRenderEvents.after(context(event));
    }

    @SubscribeEvent
    public static void end(RenderLevelStageEvent.AfterLevel event) {
        LevelRenderEvents.end(context(event));
    }

    @SubscribeEvent
    public static void extract(
            net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent event) {
        LevelRenderEvents.extract(
                new net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext() {
                    public net.minecraft.client.multiplayer.ClientLevel level() {
                        return event.getLevel();
                    }

                    public net.minecraft.client.Camera camera() {
                        return event.getCamera();
                    }

                    public net.minecraft.client.DeltaTracker deltaTracker() {
                        return event.getDeltaTracker();
                    }

                    public net.minecraft.client.renderer.state.level.LevelRenderState levelState() {
                        return event.getRenderState();
                    }
                });
    }
}
