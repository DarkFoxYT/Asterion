package net.fabricmc.fabric.api.client.rendering.v1;

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

public interface WorldRenderContext {
    LevelRenderer worldRenderer();
    PoseStack matrixStack();
    DeltaTracker tickCounter();
    boolean blockOutlines();
    Camera camera();
    GameRenderer gameRenderer();
    LightTexture lightmapTextureManager();
    Matrix4f projectionMatrix();
    Matrix4f positionMatrix();
    ClientLevel world();
    ProfilerFiller profiler();
    boolean advancedTranslucency();
    MultiBufferSource consumers();
    Frustum frustum();
}
