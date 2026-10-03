package net.krodark.asterion.game;



import net.krodark.asterion.port.compat.AsterionRegistry;



import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

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

    public static final Block ANCHOR = AsterionRegistry.register(BuiltInRegistries.BLOCK, BLOCK_KEY,

            new ChainLiftBlock(BlockBehaviour.Properties.of().strength(5, 1200).sound(SoundType.METAL)));

    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("chain_lift"));

    public static final Item ITEM = AsterionRegistry.register(BuiltInRegistries.ITEM, ITEM_KEY, new ChainLiftBlockItem(ANCHOR, new net.krodark.asterion.port.compat.ItemProperties()));

    public static final BlockEntityType<ChainLiftBlockEntity> BLOCK_ENTITY = AsterionRegistry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,

            Asterion.id("chain_lift"), BlockEntityType.Builder.of(ChainLiftBlockEntity::new, ANCHOR).build(null));

    private static final ResourceKey<EntityType<?>> ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Asterion.id("chain_lift"));

    public static final EntityType<ChainLiftEntity> LIFT = AsterionRegistry.register(BuiltInRegistries.ENTITY_TYPE, ENTITY_KEY,

            net.minecraft.world.entity.EntityType.Builder.of(ChainLiftEntity::new, MobCategory.MISC).sized(3, .5F)

                    .clientTrackingRange(16).updateInterval(1).fireImmune().build(null));

    private static final ResourceKey<EntityType<?>> RUNE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Asterion.id("lift_call_rune"));

    public static final EntityType<net.krodark.asterion.entity.LiftCallRuneEntity> CALL_RUNE = AsterionRegistry.register(BuiltInRegistries.ENTITY_TYPE, RUNE_KEY,

            net.minecraft.world.entity.EntityType.Builder.of(net.krodark.asterion.entity.LiftCallRuneEntity::new, MobCategory.MISC).sized(.7F, .7F)

                    .clientTrackingRange(10).updateInterval(20).fireImmune().build(null));

    private ChainLiftContent() {}

    private static final ResourceKey<EntityType<?>> PHYSICS_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Asterion.id("physics_chain"));

    public static final EntityType<net.krodark.asterion.entity.PhysicsChainEntity> PHYSICS_CHAIN = AsterionRegistry.register(BuiltInRegistries.ENTITY_TYPE, PHYSICS_KEY,

            EntityType.Builder.of(net.krodark.asterion.entity.PhysicsChainEntity::new, MobCategory.MISC).sized(1, 1)

                    .clientTrackingRange(12).updateInterval(2).fireImmune().build("asterion:physics_chain"));

    private static final ResourceKey<Item> PHYSICS_ITEM_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("physics_chain"));

    public static final Item PHYSICS_CHAIN_ITEM = AsterionRegistry.register(BuiltInRegistries.ITEM, PHYSICS_ITEM_KEY,

            new net.krodark.asterion.item.PhysicsChainItem(new net.krodark.asterion.port.compat.ItemProperties()));

    public static void initialize() {


        net.krodark.asterion.worldgen.GeneratedPhysicsChains.initialize();
        net.krodark.asterion.network.ChainHoldPayload.initialize();

        ItemGroupEvents.modifyEntriesEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Asterion.id("asterion")))

                .register(output -> { output.accept(ITEM); output.accept(PHYSICS_CHAIN_ITEM); });


    }

}
