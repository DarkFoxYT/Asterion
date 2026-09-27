package net.krodark.asterion.port.forge;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.port.client.PortClientFeatures;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client gameplay updates that are independent of the render pipeline. */
@Mod.EventBusSubscriber(modid = Asterion.MOD_ID, value = Dist.CLIENT)
public final class AsterionForgeClientEvents {
    private AsterionForgeClientEvents() {}

    @SubscribeEvent
    public static void afterClientTick(TickEvent.ClientTickEvent.Post event) {
        PortClientFeatures.tick(Minecraft.getInstance());
    }

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
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            WorldRenderEvents.fireBeforeBlockOutline(context, client.hitResult);
        }
    }

    private record ForgeWorldContext(LevelRenderer worldRenderer, PoseStack matrixStack,
                                     DeltaTracker tickCounter, boolean blockOutlines, Camera camera,
                                     GameRenderer gameRenderer, LightTexture lightmapTextureManager,
                                     Matrix4f projectionMatrix, Matrix4f positionMatrix,
                                     ClientLevel world, ProfilerFiller profiler,
                                     boolean advancedTranslucency, MultiBufferSource consumers,
                                     Frustum frustum) implements WorldRenderContext {
        private ForgeWorldContext(RenderLevelStageEvent event, Minecraft client) {
            // Port renderers already subtract the camera from world positions.
            // Forge's event pose includes that translation, so passing it through
            // made debris, ragdolls and attack markers follow the camera.
            this(event.getLevelRenderer(), new PoseStack(), client.getTimer(), true,
                    event.getCamera(), client.gameRenderer, client.gameRenderer.lightTexture(),
                    event.getProjectionMatrix(), new Matrix4f(), client.level,
                    client.getProfiler(), Minecraft.useShaderTransparency(),
                    client.renderBuffers().bufferSource(), event.getFrustum());
        }
    }
}
