package net.krodark.asterion.port.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;

/** Client gameplay updates that are independent of the render pipeline. */
@EventBusSubscriber(modid = Asterion.MOD_ID, value = Dist.CLIENT)
public final class NativeWorldEvents {
    private NativeWorldEvents() {}

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        var context = new ForgeWorldContext(event, client);
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            WorldRenderEvents.fireStart(context);
            WorldRenderEvents.fireAfterSetup(context);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            WorldRenderEvents.fireAfterEntities(context);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            WorldRenderEvents.fireBeforeEntities(context);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            WorldRenderEvents.fireBeforeBlockOutline(context, client.hitResult);
            WorldRenderEvents.fireDebug(context);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            WorldRenderEvents.fireTranslucent(context);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            WorldRenderEvents.fireEnd(context);
        }
    }

    private record ForgeWorldContext(
            LevelRenderer worldRenderer,
            PoseStack matrixStack,
            DeltaTracker tickCounter,
            boolean blockOutlines,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightmapTextureManager,
            Matrix4f projectionMatrix,
            Matrix4f positionMatrix,
            ClientLevel world,
            ProfilerFiller profiler,
            boolean advancedTranslucency,
            MultiBufferSource consumers,
            Frustum frustum)
            implements WorldRenderContext {
        private ForgeWorldContext(RenderLevelStageEvent event, Minecraft client) {
            // Port renderers already subtract the camera from world positions.
            // Forge's event pose includes that translation, so passing it through
            // made debris, ragdolls and attack markers follow the camera.
            this(
                    event.getLevelRenderer(),
                    new PoseStack(),
                    client.getTimer(),
                    true,
                    event.getCamera(),
                    client.gameRenderer,
                    client.gameRenderer.lightTexture(),
                    event.getProjectionMatrix(),
                    new Matrix4f(),
                    client.level,
                    client.getProfiler(),
                    Minecraft.useShaderTransparency(),
                    client.renderBuffers().bufferSource(),
                    event.getFrustum());
        }
    }
}
