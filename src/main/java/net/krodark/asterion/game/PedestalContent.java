package net.krodark.asterion.game;

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
    public static final PedestalBlock BLOCK = Registry.register(BuiltInRegistries.BLOCK, KEY,
            new PedestalBlock(BlockBehaviour.Properties.of().setId(KEY).strength(3.5F).sound(SoundType.STONE).noOcclusion()));
    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, Asterion.id("pedestal"));
    public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY, new PedestalBlockItem(BLOCK, new Item.Properties().setId(ITEM_KEY)));
    public static final BlockEntityType<PedestalBlockEntity> BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Asterion.id("pedestal"), net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(PedestalBlockEntity::new, BLOCK).build());
    private PedestalContent() { }
    public static void initialize() {
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Asterion.id("asterion")))
                .register(output -> output.accept(ITEM));
    }
}
