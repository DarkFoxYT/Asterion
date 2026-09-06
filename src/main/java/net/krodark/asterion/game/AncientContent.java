package net.krodark.asterion.game;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.AncientSkeletonEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;

public final class AncientContent {
    private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, Asterion.id("ancient_skeleton"));
    public static final EntityType<AncientSkeletonEntity> SKELETON = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.of(AncientSkeletonEntity::new, MobCategory.MONSTER).sized(.6F, 1.99F).clientTrackingRange(8).build(KEY));
    private static final ResourceKey<Item> EGG_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("ancient_skeleton_spawn_egg"));
    public static final Item EGG = Registry.register(BuiltInRegistries.ITEM, EGG_KEY,
            new SpawnEggItem(new Item.Properties().setId(EGG_KEY).spawnEgg(SKELETON)));
    private static final ResourceKey<Item> BONE_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("ancient_bone"));
    public static final Item ANCIENT_BONE = Registry.register(BuiltInRegistries.ITEM, BONE_KEY,
            new Item(new Item.Properties().setId(BONE_KEY).rarity(Rarity.UNCOMMON)
                    .component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                            java.util.List.of(net.minecraft.network.chat.Component.translatable("tooltip.asterion.ancient_bone"))))));

    private static final ResourceKey<net.minecraft.world.level.block.Block> TROPHY_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, Asterion.id("minotaur_skull_trophy"));
    public static final net.minecraft.world.level.block.Block MINOTAUR_TROPHY = Registry.register(BuiltInRegistries.BLOCK, TROPHY_BLOCK_KEY,
            new net.krodark.asterion.block.MinotaurTrophyBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .setId(TROPHY_BLOCK_KEY).strength(1.5F, 6).sound(net.minecraft.world.level.block.SoundType.BONE_BLOCK).noOcclusion()));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<net.krodark.asterion.block.MinotaurTrophyBlockEntity> TROPHY_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Asterion.id("minotaur_skull_trophy"),
                    net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                            .create(net.krodark.asterion.block.MinotaurTrophyBlockEntity::new, MINOTAUR_TROPHY).build());
    private static final ResourceKey<Item> TROPHY_ITEM_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("minotaur_skull_trophy"));
    public static final Item MINOTAUR_TROPHY_ITEM = Registry.register(BuiltInRegistries.ITEM, TROPHY_ITEM_KEY,
            new BlockItem(MINOTAUR_TROPHY, new Item.Properties().setId(TROPHY_ITEM_KEY).rarity(Rarity.RARE)));

    private AncientContent() {}
    public static void initialize() {
        FabricDefaultAttributeRegistry.register(SKELETON, AncientSkeletonEntity.attributes());
        SpawnPlacements.register(SKELETON, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, AncientSkeletonEntity::canSpawn);
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Asterion.id("asterion")))
                .register(output -> { output.accept(EGG); output.accept(ANCIENT_BONE); output.accept(MINOTAUR_TROPHY_ITEM); });
    }
}
