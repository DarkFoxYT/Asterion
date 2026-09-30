package net.krodark.asterion.mixin;

import net.krodark.asterion.update.underworld.client.LimboBoatRenderPose;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BoatRenderState.class)
public abstract class LimboBoatRenderStateMixin implements LimboBoatRenderPose {
    @Unique private float asterion$pitch, asterion$roll;
    public void limboTilt(float pitch, float roll) { asterion$pitch = pitch; asterion$roll = roll; }
    public float limboPitch() { return asterion$pitch; }
    public float limboRoll() { return asterion$roll; }
}
