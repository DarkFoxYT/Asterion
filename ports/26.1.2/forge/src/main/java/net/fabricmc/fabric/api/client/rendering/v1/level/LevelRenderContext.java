package net.fabricmc.fabric.api.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.level.LevelRenderState;

public interface LevelRenderContext {
    LevelRenderState levelState();
    PoseStack poseStack();
    MultiBufferSource.BufferSource bufferSource();
}
