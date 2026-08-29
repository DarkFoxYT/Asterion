package net.krodark.asterion.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** The visible marker for Asterion's outnumbered-combat comeback mechanic. */
public final class ResolveEffect extends MobEffect {
    public ResolveEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB63A25);
    }
}
