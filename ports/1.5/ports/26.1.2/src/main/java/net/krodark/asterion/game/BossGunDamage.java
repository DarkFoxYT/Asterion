package net.krodark.asterion.game;

import net.krodark.asterion.Asterion;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** Gun-specific boss resistance; packs can extend the tag without classifying spells as bullets. */
public final class BossGunDamage {
    private static final TagKey<DamageType> GUN_DAMAGE =
            TagKey.create(Registries.DAMAGE_TYPE, Asterion.id("gun_damage"));

    private BossGunDamage() {}

    public static float scale(DamageSource source, float amount) {
        return source.is(GUN_DAMAGE) ? amount * 0.5F : amount;
    }
}
