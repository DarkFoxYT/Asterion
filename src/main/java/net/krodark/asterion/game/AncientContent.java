package net.krodark.asterion.game;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
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
            EntityType.Builder.of(AncientSkeletonEntity::new, MobCategory.MONSTER).sized(.6F, 1.99F)
                    .clientTrackingRange(8).build(null));
    private static final ResourceKey<Item> EGG_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("ancient_skeleton_spawn_egg"));
    @SuppressWarnings("deprecation") // Required by the shared Fabric/NeoForge registration path.
    public static final Item EGG = Registry.register(BuiltInRegistries.ITEM, EGG_KEY,
            new SpawnEggItem(SKELETON, 0xD7C8A5, 0x6F6048, new Item.Properties()));
    private static final ResourceKey<Item> BONE_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("ancient_bone"));
    public static final Item ANCIENT_BONE = Registry.register(BuiltInRegistries.ITEM, BONE_KEY,
            new Item(new Item.Properties().rarity(Rarity.UNCOMMON)
                    .component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                            java.util.List.of(net.minecraft.network.chat.Component.translatable("tooltip.asterion.ancient_bone"))))));

    private static final ResourceKey<Item> HIDE_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("minotaur_hide"));
    public static final Item MINOTAUR_HIDE = Registry.register(BuiltInRegistries.ITEM, HIDE_KEY,
            new Item(new Item.Properties().rarity(Rarity.UNCOMMON)
                    .component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                            java.util.List.of(net.minecraft.network.chat.Component.translatable("tooltip.asterion.minotaur_hide"))))));

    private static final ResourceKey<net.minecraft.world.level.block.Block> TROPHY_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, Asterion.id("minotaur_skull_trophy"));
    public static final net.minecraft.world.level.block.Block MINOTAUR_TROPHY = Registry.register(BuiltInRegistries.BLOCK, TROPHY_BLOCK_KEY,
            new net.krodark.asterion.block.MinotaurTrophyBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .strength(1.5F, 6).sound(net.minecraft.world.level.block.SoundType.BONE_BLOCK).noOcclusion()));
    public static final net.minecraft.world.level.block.entity.BlockEntityType<net.krodark.asterion.block.MinotaurTrophyBlockEntity> TROPHY_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Asterion.id("minotaur_skull_trophy"),
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder
                            .of(net.krodark.asterion.block.MinotaurTrophyBlockEntity::new, MINOTAUR_TROPHY).build(null));
    private static final ResourceKey<Item> TROPHY_ITEM_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("minotaur_skull_trophy"));
    public static final Item MINOTAUR_TROPHY_ITEM = Registry.register(BuiltInRegistries.ITEM, TROPHY_ITEM_KEY,
            new BlockItem(MINOTAUR_TROPHY, new Item.Properties().rarity(Rarity.RARE)));

    private AncientContent() {}
    public static void initialize() {
        FabricDefaultAttributeRegistry.register(SKELETON, AncientSkeletonEntity.attributes());
        ItemGroupEvents.modifyEntriesEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Asterion.id("asterion")))
                .register(output -> { output.accept(EGG); output.accept(ANCIENT_BONE); output.accept(MINOTAUR_HIDE); output.accept(MINOTAUR_TROPHY_ITEM); });
    }
}
