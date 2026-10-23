package net.krodark.asterion.block;

import net.krodark.asterion.Asterion;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class RespawnObelisks {
    public static final SanctuaryBlock ALTAR = register("respawn_altar", true);
    public static final SanctuaryBlock OBELISK = register("respawn_obelisk", false);
    public static final Item CHARGED_RUNE = Registry.register(BuiltInRegistries.ITEM,
            Asterion.id("charged_respawn_rune"), new Item(new Item.Properties().stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, Asterion.id("charged_respawn_rune")))));
    public static final BlockEntityType<SanctuaryBlockEntity> BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Asterion.id("sanctuary"),
            FabricBlockEntityTypeBuilder.create(SanctuaryBlockEntity::new, ALTAR, OBELISK).build());

    private RespawnObelisks() { }
    public static void initialize() { }

    private static SanctuaryBlock register(String name, boolean altar) {
        var id = Asterion.id(name);
        SanctuaryBlock block = Registry.register(BuiltInRegistries.BLOCK, id,
                new SanctuaryBlock(altar, BlockBehaviour.Properties.of()
                        .setId(ResourceKey.create(Registries.BLOCK, id)).strength(4.0F, 12.0F)
                        .noOcclusion().sound(SoundType.DEEPSLATE)
                        .lightLevel(state -> !altar && state.getValue(SanctuaryBlock.CHARGE) == 1 ? 11 : 0)));
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block,
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
        return block;
    }
}
