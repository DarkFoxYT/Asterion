package net.krodark.labyrinth;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.krodark.labyrinth.network.DimensionTransitionPayload;
import net.krodark.labyrinth.network.MazeZapPayload;
import net.krodark.labyrinth.network.DeadSunEventPayload;
import net.krodark.labyrinth.network.ragdoll.*;
import net.krodark.labyrinth.entity.MinotaurEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.krodark.labyrinth.worldgen.UnderwaterRuinFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Labyrinth implements ModInitializer {
    public static final String MOD_ID = "labyrinth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ResourceKey<Level> LABYRINTH_LEVEL = ResourceKey.create(
            Registries.DIMENSION, id("labyrinth_dimension"));

    public static final Block ANCIENT_BRICKS = registerBlock("ancient_bricks", MapColor.COLOR_BROWN);
    public static final Block ANCIENT_STONE = registerBlock("ancient_stone", MapColor.TERRACOTTA_BROWN);
    private static final ResourceKey<EntityType<?>> MINOTAUR_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, id("minotaur"));
    public static final EntityType<MinotaurEntity> MINOTAUR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            MINOTAUR_KEY,
            EntityType.Builder.of(MinotaurEntity::new, MobCategory.MONSTER)
                    .sized(1.25F, 2.75F).eyeHeight(2.35F).clientTrackingRange(10).build(MINOTAUR_KEY)
    );

    private static final ResourceKey<Item> MECHANISM_KEY = ResourceKey.create(
            Registries.ITEM, id("antikythera_mechanism"));
    public static final Item ANTIKYTHERA_MECHANISM = Registry.register(
            BuiltInRegistries.ITEM,
            MECHANISM_KEY,
            new AntikytheraMechanismItem(new Item.Properties().setId(MECHANISM_KEY).stacksTo(1).rarity(Rarity.EPIC))
    );
    private static final ResourceKey<CreativeModeTab> ITEM_GROUP_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, id("labyrinth"));
    public static final CreativeModeTab ITEM_GROUP = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ITEM_GROUP_KEY,
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.labyrinth.labyrinth"))
                    .icon(() -> new ItemStack(ANTIKYTHERA_MECHANISM))
                    .displayItems((parameters, output) -> {
                        output.accept(ANTIKYTHERA_MECHANISM);
                        output.accept(ANCIENT_BRICKS);
                        output.accept(ANCIENT_STONE);
                    })
                    .build()
    );
    public static final Feature<NoneFeatureConfiguration> UNDERWATER_RUIN_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("underwater_ruin"),
            new UnderwaterRuinFeature(NoneFeatureConfiguration.CODEC));
    private static final ResourceKey<PlacedFeature> UNDERWATER_RUIN_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("underwater_ruin"));

    @Override
    public void onInitialize() {
        LabyrinthConfig.INSTANCE.sanitize();
        PayloadTypeRegistry.clientboundPlay().register(DimensionTransitionPayload.TYPE, DimensionTransitionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MazeZapPayload.TYPE, MazeZapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeadSunEventPayload.TYPE, DeadSunEventPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RagdollImpulsePayload.TYPE, RagdollImpulsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RagdollPosePayload.TYPE, RagdollPosePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RagdollAuthorityPayload.TYPE, RagdollAuthorityPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RagdollExplosionPayload.TYPE, RagdollExplosionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RagdollKillPayload.TYPE, RagdollKillPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RagdollBlockImpactPayload.TYPE, RagdollBlockImpactPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RagdollEntityImpactPayload.TYPE, RagdollEntityImpactPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RagdollFallDamagePayload.TYPE, RagdollFallDamagePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TumbleExitPayload.TYPE, TumbleExitPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RagdollArmorImpactPayload.TYPE, RagdollArmorImpactPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RagdollPosePayload.TYPE, RagdollPosePayload.CODEC);
        RagdollServerNetworking.initialize();
        FabricDefaultAttributeRegistry.register(MINOTAUR, MinotaurEntity.createAttributes());
        ServerChunkEvents.CHUNK_LOAD.register(WorldGenerator::onChunkLoad);
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                WorldGenerator.trackMazeBreak(serverLevel, pos, state);
        });
        BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OCEAN),
                GenerationStep.Decoration.SURFACE_STRUCTURES, UNDERWATER_RUIN_PLACED);
        ServerTickEvents.END_SERVER_TICK.register(WorldGenerator::tickServer);
        LOGGER.info("The Antikythera Mechanism stirs beneath the sea");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static Block registerBlock(String name, MapColor color) {
        Identifier identifier = id(name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, identifier);
        Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                new Block(BlockBehaviour.Properties.of().setId(blockKey).mapColor(color)
                        .strength(3.5f, 8.0f).sound(SoundType.DEEPSLATE)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, identifier);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }
}
