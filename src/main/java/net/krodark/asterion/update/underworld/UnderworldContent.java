package net.krodark.asterion.update.underworld;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registry boundary for 2.0 content; kept separate from the original Asterion update. */
public final class UnderworldContent {
    private static final ResourceKey<EntityType<?>> FERRY_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Asterion.id("charons_ferry"));

    public static final EntityType<CharonsFerryEntity> CHARONS_FERRY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, FERRY_KEY,
            EntityType.Builder.of(CharonsFerryEntity::new, MobCategory.MISC)
                    .sized(5.6F, .52F).clientTrackingRange(24).updateInterval(1)
                    .fireImmune().build(FERRY_KEY));

    private UnderworldContent() { }
    public static void initialize() { }
}
