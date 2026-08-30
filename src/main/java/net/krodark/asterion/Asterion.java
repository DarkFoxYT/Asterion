package net.krodark.asterion;

import net.minecraft.core.BlockPos;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.krodark.asterion.network.DimensionTransitionPayload;
import net.krodark.asterion.network.EntryOmenPayload;
import net.krodark.asterion.network.BossFinalePayload;
import net.krodark.asterion.network.GatewayPortalPayload;
import net.krodark.asterion.network.TransitionReadyPayload;
import net.krodark.asterion.network.MazeZapPayload;
import net.krodark.asterion.network.DeadSunEventPayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.krodark.asterion.network.BossEncounterResetPayload;
import net.krodark.asterion.network.BiomeAtmospherePayload;
import net.krodark.asterion.network.DazePayload;
import net.krodark.asterion.network.ragdoll.*;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.entity.BombadierBeetleEntity;
import net.krodark.asterion.block.ShortGrassBlock;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.krodark.asterion.effect.ResolveEffect;
import net.krodark.asterion.effect.ResolveSystem;
import net.krodark.asterion.game.light.DynamicBlockLights;
import net.krodark.asterion.command.PortalCommands;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.sounds.SoundEvent;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.krodark.asterion.block.RuneBlock;
import net.krodark.asterion.block.RuneBlockEntity;
import net.krodark.asterion.block.RuneDoorBlock;
import net.krodark.asterion.block.DirectionalGateBlock;
import net.krodark.asterion.block.WinchBlock;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.krodark.asterion.block.SkeletonBlock;
import net.krodark.asterion.block.SkeletonBlockEntity;
import net.krodark.asterion.block.ShatteredDeadWoodBlock;
import net.krodark.asterion.block.ShatteredDeadWoodBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.krodark.asterion.worldgen.UnderwaterRuinFeature;
import net.krodark.asterion.worldgen.AncientMossPatchFeature;
import net.krodark.asterion.worldgen.AncientLeavesClusterFeature;
import net.krodark.asterion.worldgen.OvergrowthBridgeFeature;
import net.krodark.asterion.worldgen.OvergrowthBridgeChainFeature;
import net.krodark.asterion.worldgen.OvergrowthRestSiteFeature;
import net.krodark.asterion.worldgen.GiantDeadTreeFeature;
import net.krodark.asterion.worldgen.AncientGroundVineFeature;
import net.krodark.asterion.worldgen.AncientHangingVineFeature;
import net.krodark.asterion.worldgen.MazeBiomes;
import net.krodark.asterion.worldgen.MazeChunkGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Asterion implements ModInitializer {
    public static final String MOD_ID = "asterion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ResourceKey<Level> ASTERION_LEVEL = ResourceKey.create(
            Registries.DIMENSION, id("asterion_dimension"));
    public static final SoundEvent MINOTAUR_ROAR = registerSound("minotaur_roar");
    public static final Holder.Reference<MobEffect> RESOLVE = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, id("resolve"), new ResolveEffect());

    public static final Block ANCIENT_BRICKS = registerBlock("ancient_bricks", MapColor.COLOR_BROWN, Block::new);
    public static final Block ANCIENT_MOSSY_BRICKS = registerBlock(
            "ancient_mossy_bricks", MapColor.TERRACOTTA_GREEN, Block::new);
    public static final Block ANCIENT_BRICK_SLAB = registerBlock("ancient_brick_slab", MapColor.COLOR_BROWN, SlabBlock::new);
    public static final Block ANCIENT_BRICK_STAIRS = registerBlock("ancient_brick_stairs", MapColor.COLOR_BROWN,
            properties -> new StairBlock(ANCIENT_BRICKS.defaultBlockState(), properties) { });
    public static final Block ANCIENT_BRICK_WALL = registerBlock("ancient_brick_wall", MapColor.COLOR_BROWN, WallBlock::new);
    public static final Block ANCIENT_PLANKS = registerBlock("ancient_planks", MapColor.COLOR_BROWN,
            properties -> new Block(properties.strength(2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block ANCIENT_PLANK_SLAB = registerBlock("ancient_plank_slab", MapColor.COLOR_BROWN,
            properties -> new SlabBlock(properties.strength(2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block ANCIENT_PLANK_STAIRS = registerBlock("ancient_plank_stairs", MapColor.COLOR_BROWN,
            properties -> new StairBlock(ANCIENT_PLANKS.defaultBlockState(),
                    properties.strength(2.0F, 3.0F).sound(SoundType.WOOD)) { });
    public static final Block ANCIENT_PLANK_FENCE = registerBlock("ancient_plank_fence", MapColor.COLOR_BROWN,
            properties -> new FenceBlock(properties.strength(2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block DEAD_WOOD = registerBlock("dead_wood", MapColor.COLOR_BROWN,
            properties -> new RotatedPillarBlock(properties.strength(3.2F, 5.0F).sound(SoundType.WOOD)));
    public static final ShatteredDeadWoodBlock SHATTERED_DEAD_WOOD = (ShatteredDeadWoodBlock)registerBlock(
            "shattered_dead_wood", MapColor.COLOR_BROWN,
            properties -> new ShatteredDeadWoodBlock(properties.noOcclusion()
                    .strength(3.2F, 5.0F).sound(SoundType.WOOD)));
    public static final Block ANCIENT_STONE = registerBlock("ancient_stone", MapColor.TERRACOTTA_BROWN, Block::new);
    public static final Block MOSSY_ANCIENT_STONE = registerBlock("mossy_ancient_stone", MapColor.TERRACOTTA_GREEN, Block::new);
    public static final Block ANCIENT_MOSS = registerBlock("ancient_moss", MapColor.TERRACOTTA_GREEN, Block::new);
    public static final Block ANCIENT_MOSS_CARPET = registerBlock(
            "ancient_moss_carpet", MapColor.TERRACOTTA_GREEN,
            properties -> new CarpetBlock(properties.noCollision().strength(0.1F)
                    .sound(SoundType.MOSS_CARPET)));
    public static final LeavesBlock ANCIENT_LEAVES = (LeavesBlock)registerBlock(
            "ancient_leaves", MapColor.TERRACOTTA_BROWN,
            properties -> new UntintedParticleLeavesBlock(0.01F, ParticleTypes.PALE_OAK_LEAVES,
                    properties.strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()));
    public static final Block SHORT_GRASS = registerBlock("short_grass", MapColor.PLANT,
            properties -> new ShortGrassBlock(properties.noCollision().replaceable().instabreak()
                    .sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)));
    public static final Block ANCIENT_STONE_SLAB = registerBlock("ancient_stone_slab", MapColor.TERRACOTTA_BROWN, SlabBlock::new);
    public static final Block ANCIENT_STONE_STAIRS = registerBlock("ancient_stone_stairs", MapColor.TERRACOTTA_BROWN,
            properties -> new StairBlock(ANCIENT_STONE.defaultBlockState(), properties) { });
    public static final Block ANCIENT_STONE_WALL = registerBlock("ancient_stone_wall", MapColor.TERRACOTTA_BROWN, WallBlock::new);
    public static final Block MAZESTEEL_BLOCK = registerBlock("mazesteel_block", MapColor.METAL, Block::new);
    public static final ChainBlock MAZESTEEL_CHAIN = (ChainBlock)registerBlock(
            "mazesteel_chain", MapColor.METAL,
            properties -> new ChainBlock(properties.noOcclusion().sound(SoundType.CHAIN)));
    public static final DirectionalGateBlock MAZESTEEL_GATE = (DirectionalGateBlock)registerBlock(
            "mazesteel_gate", MapColor.METAL,
            properties -> new DirectionalGateBlock(properties.noOcclusion()));
    public static final WinchBlock WINCH = (WinchBlock)registerBlock(
            "winch", MapColor.METAL, WinchBlock::new);
    public static final LabyrinthVineBlock LABYRINTH_VINE = (LabyrinthVineBlock)registerBlock(
            "labyrinth_vine", MapColor.COLOR_BROWN,
            properties -> new LabyrinthVineBlock(properties.noOcclusion().strength(0.4F)
                    .sound(SoundType.VINE).lightLevel(state ->
                            state.getValue(LabyrinthVineBlock.END) ? 12 : 0)));
    public static final SkeletonBlock SKELETON = (SkeletonBlock)registerBlock(
            "skeleton", MapColor.COLOR_LIGHT_GRAY,
            properties -> new SkeletonBlock(properties.noOcclusion().strength(0.45F)
                    .sound(SoundType.BONE_BLOCK)));
    public static final RuneBlock[] RUNE_BLOCKS = registerRuneBlocks();
    public static final RuneDoorBlock RUNE_ZONE_DOOR = (RuneDoorBlock)registerBlock("rune_zone_door",
            MapColor.COLOR_BLACK, RuneDoorBlock::new);
    public static final BlockEntityType<RuneBlockEntity> RUNE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("rune"),
            FabricBlockEntityTypeBuilder.create(RuneBlockEntity::new, RUNE_BLOCKS).build());
    public static final BlockEntityType<LabyrinthVineBlockEntity> LABYRINTH_VINE_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("labyrinth_vine"),
            FabricBlockEntityTypeBuilder.create(LabyrinthVineBlockEntity::new, LABYRINTH_VINE).build());
    public static final BlockEntityType<SkeletonBlockEntity> SKELETON_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("skeleton"),
            FabricBlockEntityTypeBuilder.create(SkeletonBlockEntity::new, SKELETON).build());
    public static final BlockEntityType<ShatteredDeadWoodBlockEntity> SHATTERED_DEAD_WOOD_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("shattered_dead_wood"),
                    FabricBlockEntityTypeBuilder.create(ShatteredDeadWoodBlockEntity::new,
                            SHATTERED_DEAD_WOOD).build());
    private static final ResourceKey<EntityType<?>> MINOTAUR_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, id("minotaur"));
    public static final EntityType<MinotaurEntity> MINOTAUR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            MINOTAUR_KEY,
            EntityType.Builder.of(MinotaurEntity::new, MobCategory.MONSTER)
                    .sized(1.25F * AsterionConfig.INSTANCE.minotaurScale,
                            2.75F * AsterionConfig.INSTANCE.minotaurScale)
                    .eyeHeight(2.35F * AsterionConfig.INSTANCE.minotaurScale)
                    .clientTrackingRange(16).build(MINOTAUR_KEY)
    );
    private static final ResourceKey<EntityType<?>> BOMBARDIER_BEETLE_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, id("bombadier_beetle"));
    public static final EntityType<BombadierBeetleEntity> BOMBARDIER_BEETLE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            BOMBARDIER_BEETLE_KEY,
            EntityType.Builder.of(BombadierBeetleEntity::new, MobCategory.CREATURE)
                    .sized(0.8F, 0.45F).eyeHeight(0.3F).clientTrackingRange(10)
                    .fireImmune().build(BOMBARDIER_BEETLE_KEY)
    );
    public static final SimpleParticleType BOMBARDIER_STENCH = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("bombardier_stench"), FabricParticleTypes.simple());
    public static final SimpleParticleType BOMBARDIER_GAS_FIRE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("bombardier_gas_fire"), FabricParticleTypes.simple());
    public static final SimpleParticleType FLY = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("fly"), FabricParticleTypes.simple());
    public static final SimpleParticleType FIREFLY = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("firefly"), FabricParticleTypes.simple());
    public static final SimpleParticleType ANCIENT_WALL_DUST = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("ancient_wall_dust"), FabricParticleTypes.simple());
    public static final SimpleParticleType RUMBLE_SMOKE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("rumble_smoke"), FabricParticleTypes.simple());

    private static final ResourceKey<Item> MECHANISM_KEY = ResourceKey.create(
            Registries.ITEM, id("antikythera_mechanism"));
    public static final Item ANTIKYTHERA_MECHANISM = Registry.register(
            BuiltInRegistries.ITEM,
            MECHANISM_KEY,
            new AntikytheraMechanismItem(new Item.Properties().setId(MECHANISM_KEY).stacksTo(1).rarity(Rarity.EPIC))
    );
    private static final ResourceKey<Item> BLUEPRINT_KEY = ResourceKey.create(
            Registries.ITEM, id("antikythera_blueprint"));
    public static final Item ANTIKYTHERA_BLUEPRINT = Registry.register(
            BuiltInRegistries.ITEM,
            BLUEPRINT_KEY,
            new AntikytheraBlueprintItem(new Item.Properties().setId(BLUEPRINT_KEY).stacksTo(1).rarity(Rarity.RARE))
    );
    private static final ResourceKey<Item> MINOTAUR_SIGIL_KEY = ResourceKey.create(
            Registries.ITEM, id("minotaur_sigil"));
    public static final Item MINOTAUR_SIGIL = Registry.register(
            BuiltInRegistries.ITEM, MINOTAUR_SIGIL_KEY,
            new Item(new Item.Properties().setId(MINOTAUR_SIGIL_KEY).stacksTo(1).rarity(Rarity.EPIC)));
    private static final ResourceKey<CreativeModeTab> ITEM_GROUP_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, id("asterion"));
    public static final CreativeModeTab ITEM_GROUP = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ITEM_GROUP_KEY,
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.asterion.asterion"))
                    .icon(() -> new ItemStack(ANTIKYTHERA_MECHANISM))
                    .displayItems((parameters, output) -> {
                        output.accept(ANTIKYTHERA_MECHANISM);
                        output.accept(ANTIKYTHERA_BLUEPRINT);
                        output.accept(MINOTAUR_SIGIL);
                        output.accept(ANCIENT_BRICKS);
                        output.accept(ANCIENT_MOSSY_BRICKS);
                        output.accept(ANCIENT_BRICK_SLAB);
                        output.accept(ANCIENT_BRICK_STAIRS);
                        output.accept(ANCIENT_BRICK_WALL);
                        output.accept(ANCIENT_PLANKS);
                        output.accept(ANCIENT_PLANK_SLAB);
                        output.accept(ANCIENT_PLANK_STAIRS);
                        output.accept(ANCIENT_PLANK_FENCE);
                        output.accept(DEAD_WOOD);
                        output.accept(SHATTERED_DEAD_WOOD);
                        output.accept(ANCIENT_STONE);
                        output.accept(MOSSY_ANCIENT_STONE);
                        output.accept(ANCIENT_MOSS);
                        output.accept(ANCIENT_MOSS_CARPET);
                        output.accept(ANCIENT_LEAVES);
                        output.accept(SHORT_GRASS);
                        output.accept(ANCIENT_STONE_SLAB);
                        output.accept(ANCIENT_STONE_STAIRS);
                        output.accept(ANCIENT_STONE_WALL);
                        output.accept(MAZESTEEL_BLOCK);
                        output.accept(MAZESTEEL_CHAIN);
                        output.accept(MAZESTEEL_GATE);
                        output.accept(WINCH);
                        output.accept(LABYRINTH_VINE);
                        output.accept(SKELETON);
                        for (RuneBlock rune : RUNE_BLOCKS) output.accept(rune);
                        output.accept(RUNE_ZONE_DOOR);
                    })
                    .build()
    );
    public static final Feature<NoneFeatureConfiguration> UNDERWATER_RUIN_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("underwater_ruin"),
            new UnderwaterRuinFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> ANCIENT_MOSS_PATCH_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("ancient_moss_patch"),
            new AncientMossPatchFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> ANCIENT_LEAVES_CLUSTER_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("ancient_leaves_cluster"),
            new AncientLeavesClusterFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> OVERGROWTH_BRIDGE_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("overgrowth_bridge"),
            new OvergrowthBridgeFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> OVERGROWTH_BRIDGE_CHAIN_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("overgrowth_bridge_chains"),
            new OvergrowthBridgeChainFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> OVERGROWTH_REST_SITE_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("overgrowth_rest_site"),
            new OvergrowthRestSiteFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> GIANT_DEAD_TREE_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("giant_dead_tree"),
            new GiantDeadTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> ANCIENT_GROUND_VINE_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("ancient_ground_vines"),
            new AncientGroundVineFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> ANCIENT_HANGING_VINE_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("ancient_hanging_vines"),
            new AncientHangingVineFeature(NoneFeatureConfiguration.CODEC));
    public static final com.mojang.serialization.MapCodec<MazeChunkGenerator> MAZE_CHUNK_GENERATOR =
            Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("maze"), MazeChunkGenerator.CODEC);
    private static final ResourceKey<PlacedFeature> UNDERWATER_RUIN_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("underwater_ruin"));
    private static final ResourceKey<PlacedFeature> ANCIENT_MOSS_PATCH_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("ancient_moss_patch"));
    private static final ResourceKey<PlacedFeature> ANCIENT_LEAVES_CLUSTER_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("ancient_leaves_cluster"));
    private static final ResourceKey<PlacedFeature> OVERGROWTH_BRIDGE_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("overgrowth_bridge"));
    private static final ResourceKey<PlacedFeature> OVERGROWTH_BRIDGE_CHAIN_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("overgrowth_bridge_chains"));
    private static final ResourceKey<PlacedFeature> OVERGROWTH_REST_SITE_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("overgrowth_rest_site"));
    private static final ResourceKey<PlacedFeature> GIANT_DEAD_TREE_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("giant_dead_tree"));
    private static final ResourceKey<PlacedFeature> ANCIENT_GROUND_VINE_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("ancient_ground_vines"));
    private static final ResourceKey<PlacedFeature> ANCIENT_HANGING_VINE_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("ancient_hanging_vines"));

    @Override
    public void onInitialize() {
        AsterionConfig.INSTANCE.sanitize();
        PayloadTypeRegistry.clientboundPlay().register(DimensionTransitionPayload.TYPE, DimensionTransitionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EntryOmenPayload.TYPE, EntryOmenPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BossFinalePayload.TYPE, BossFinalePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GatewayPortalPayload.TYPE, GatewayPortalPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TransitionReadyPayload.TYPE, TransitionReadyPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MazeZapPayload.TYPE, MazeZapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeadSunEventPayload.TYPE, DeadSunEventPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MazeShiftPayload.TYPE, MazeShiftPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeadSunStrikePayload.TYPE, DeadSunStrikePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BossTelegraphPayload.TYPE, BossTelegraphPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BossEncounterResetPayload.TYPE, BossEncounterResetPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DazePayload.TYPE, DazePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BiomeAtmospherePayload.TYPE, BiomeAtmospherePayload.CODEC);
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
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                TransitionReadyPayload.TYPE, (payload, context) -> context.server().execute(() ->
                        WorldGenerator.markTransitionReady(context.player())));
        DeadSunEventSystem.registerCommands();
        DynamicBlockLights.initialize();
        PortalCommands.register();
        FabricDefaultAttributeRegistry.register(MINOTAUR, MinotaurEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BOMBARDIER_BEETLE, BombadierBeetleEntity.createAttributes());
        ServerChunkEvents.CHUNK_LOAD.register(WorldGenerator::onChunkLoad);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                        || !WorldGenerator.isActivePortalProtected(serverLevel, pos));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                WorldGenerator.trackMazeBreak(serverLevel, pos, state);
        });
        BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OCEAN),
                GenerationStep.Decoration.SURFACE_STRUCTURES, UNDERWATER_RUIN_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, ANCIENT_MOSS_PATCH_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, GIANT_DEAD_TREE_PLACED);
        // Ordering matters: supports are generated before the chains and vines that use them.
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, ANCIENT_LEAVES_CLUSTER_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, OVERGROWTH_BRIDGE_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, OVERGROWTH_REST_SITE_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, OVERGROWTH_BRIDGE_CHAIN_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, ANCIENT_GROUND_VINE_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, ANCIENT_HANGING_VINE_PLACED);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                MobCategory.CREATURE, BOMBARDIER_BEETLE, 12, 1, 3);
        ServerTickEvents.END_SERVER_TICK.register(WorldGenerator::tickServer);
        ServerTickEvents.END_SERVER_TICK.register(ResolveSystem::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken,
                                                         damageTaken, blocked) ->
                ResolveSystem.recordAttack(entity, source, damageTaken));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof net.minecraft.server.level.ServerPlayer player)
                WorldGenerator.prepareRapidRespawn(player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                WorldGenerator.playerConnected(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer.level().dimension().equals(ASTERION_LEVEL)) {
                BlockPos deathPosition = oldPlayer.blockPosition().immutable();
                WorldGenerator.finishRapidRespawn(newPlayer);
                boolean bossWipe = WorldGenerator.resetBossEncounterAfterDeath(oldPlayer);
                WorldGenerator.respawnAtRune(newPlayer, deathPosition);
                if (bossWipe && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                        newPlayer, BossEncounterResetPayload.TYPE))
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                            newPlayer, BossEncounterResetPayload.INSTANCE);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(WorldGenerator::clearRuntimeState);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var maze = server.getLevel(ASTERION_LEVEL);
            if (maze != null) MazeBiomes.load(maze);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ResolveSystem.clear());
        LOGGER.info("The Antikythera Mechanism stirs beneath the sea");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static SoundEvent registerSound(String name) {
        Identifier identifier = id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, identifier,
                SoundEvent.createVariableRangeEvent(identifier));
    }

    private static Block registerBlock(String name, MapColor color,
                                       java.util.function.Function<BlockBehaviour.Properties, Block> factory) {
        Identifier identifier = id(name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, identifier);
        Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(
                BlockBehaviour.Properties.of().setId(blockKey).mapColor(color)
                        .strength(3.5f, 8.0f).sound(SoundType.DEEPSLATE)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, identifier);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    private static RuneBlock[] registerRuneBlocks() {
        RuneBlock[] runes = new RuneBlock[GreekRune.values().length];
        for (int index = 0; index < runes.length; index++) {
            final int runeIndex = index;
            runes[index] = (RuneBlock)registerBlock("rune_" + (index + 1), MapColor.COLOR_BROWN,
                    properties -> new RuneBlock(runeIndex, properties.noOcclusion()));
        }
        return runes;
    }
}
