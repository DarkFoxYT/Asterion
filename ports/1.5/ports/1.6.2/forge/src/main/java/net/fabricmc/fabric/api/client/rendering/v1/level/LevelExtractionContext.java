package net.fabricmc.fabric.api.client.rendering.v1.level;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;

public interface LevelExtractionContext {
    ClientLevel level();
    Camera camera();
    DeltaTracker deltaTracker();
    LevelRenderState levelState();
}
