package net.krodark.asterion.game;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.*;
import net.krodark.asterion.entity.ChainLiftEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ChainLiftContent {
    private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, Asterion.id("chain_lift"));
    public static final Block ANCHOR = Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY,
            new ChainLiftBlock(BlockBehaviour.Properties.of().setId(BLOCK_KEY).strength(5, 1200).sound(SoundType.METAL)));
    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("chain_lift"));
    public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY, new ChainLiftBlockItem(ANCHOR, new Item.Properties().setId(ITEM_KEY)));
    public static final BlockEntityType<ChainLiftBlockEntity> BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Asterion.id("chain_lift"), FabricBlockEntityTypeBuilder.create(ChainLiftBlockEntity::new, ANCHOR).build());
    private static final ResourceKey<EntityType<?>> ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Asterion.id("chain_lift"));
    public static final EntityType<ChainLiftEntity> LIFT = Registry.register(BuiltInRegistries.ENTITY_TYPE, ENTITY_KEY,
            EntityType.Builder.of(ChainLiftEntity::new, MobCategory.MISC).sized(3, .5F)
                    .clientTrackingRange(16).updateInterval(1).fireImmune().build(ENTITY_KEY));
    private ChainLiftContent() {}
    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Asterion.id("asterion")))
                .register(output -> output.accept(ITEM));
    }
}
