package net.krodark.asterion.block;

import net.krodark.asterion.port.compat.AsterionRegistry;

import net.krodark.asterion.Asterion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class RespawnObelisks {
    public static final SanctuaryBlock ALTAR = register("respawn_altar", true);
    public static final SanctuaryBlock OBELISK = register("respawn_obelisk", false);
    public static final Item CHARGED_RUNE = AsterionRegistry.register(BuiltInRegistries.ITEM,
            Asterion.id("charged_respawn_rune"), new Item(new net.krodark.asterion.port.compat.ItemProperties().stacksTo(1)));
    public static final BlockEntityType<SanctuaryBlockEntity> BLOCK_ENTITY = AsterionRegistry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Asterion.id("sanctuary"),
            BlockEntityType.Builder.of(SanctuaryBlockEntity::new, ALTAR, OBELISK).build(null));

    private RespawnObelisks() { }
    public static void initialize() { }

    private static SanctuaryBlock register(String name, boolean altar) {
        var id = Asterion.id(name);
        SanctuaryBlock block = AsterionRegistry.register(BuiltInRegistries.BLOCK, id,
                new SanctuaryBlock(altar, BlockBehaviour.Properties.of().strength(4.0F, 12.0F)
                        .noOcclusion().sound(SoundType.DEEPSLATE)
                        .lightLevel(state -> !altar && state.getValue(SanctuaryBlock.CHARGE) == 1 ? 11 : 0)));
        AsterionRegistry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new net.krodark.asterion.port.compat.ItemProperties()));
        return block;
    }
}
