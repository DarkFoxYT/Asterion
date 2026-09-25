package net.krodark.asterion.update.underworld;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.krodark.asterion.update.underworld.entity.CharonEntity;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
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
    private static final ResourceKey<EntityType<?>> CHARON_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Asterion.id("charon"));
    private static final ResourceKey<EntityType<?>> SPIDER_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Asterion.id("limbo_spider"));

    public static final EntityType<CharonsFerryEntity> CHARONS_FERRY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, FERRY_KEY,
            EntityType.Builder.of(CharonsFerryEntity::new, MobCategory.MISC)
                    .sized(5.6F, 2.25F).clientTrackingRange(24).updateInterval(1)
                    .fireImmune().build(FERRY_KEY));

    public static final EntityType<CharonEntity> CHARON = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, CHARON_KEY,
            EntityType.Builder.of(CharonEntity::new, MobCategory.MISC)
                    .sized(.9F, 2.55F).clientTrackingRange(24).updateInterval(1)
                    .fireImmune().build(CHARON_KEY));

    public static final EntityType<LimboSpiderEntity> SPIDER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, SPIDER_KEY,
            EntityType.Builder.of(LimboSpiderEntity::new, MobCategory.MONSTER)
                    .sized(1.45F,1.3F).clientTrackingRange(12).updateInterval(1)
                    .build(SPIDER_KEY));

    private UnderworldContent() { }
    public static void initialize() {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                SPIDER,LimboSpiderEntity.createAttributes());
        LimboWebSystem.initialize();
        SpiderCommands.register();
    }
}
