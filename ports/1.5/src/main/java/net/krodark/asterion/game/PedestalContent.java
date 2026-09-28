package net.krodark.asterion.game;

import net.krodark.asterion.port.compat.AsterionRegistry;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.*;

public final class PedestalContent {
    private static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, Asterion.id("pedestal"));
    public static final PedestalBlock BLOCK = AsterionRegistry.register(BuiltInRegistries.BLOCK, KEY,
            new PedestalBlock(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.STONE).noOcclusion()));
    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("pedestal"));
    public static final Item ITEM = AsterionRegistry.register(BuiltInRegistries.ITEM, ITEM_KEY, new PedestalBlockItem(BLOCK, new net.krodark.asterion.port.compat.ItemProperties()));
    public static final BlockEntityType<PedestalBlockEntity> BLOCK_ENTITY = AsterionRegistry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Asterion.id("pedestal"), BlockEntityType.Builder.of(PedestalBlockEntity::new, BLOCK).build(null));
    private PedestalContent() { }
    public static void initialize() {
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Asterion.id("asterion")))
                .register(output -> output.accept(ITEM));
    }
}
