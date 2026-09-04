package net.krodark.asterion;

import net.minecraft.core.BlockPos;

import net.fabricmc.api.ModInitializer;
import net.krodark.asterion.event.CatacombFloodState;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.krodark.asterion.network.DimensionTransitionPayload;
import net.krodark.asterion.network.EntryOmenPayload;
import net.krodark.asterion.network.BossFinalePayload;
import net.krodark.asterion.network.GatewayPortalPayload;
import net.krodark.asterion.network.TransitionReadyPayload;
import net.krodark.asterion.network.PressureButtonHoldPayload;
import net.krodark.asterion.network.PressureButtonNetworking;
import net.krodark.asterion.network.MazeZapPayload;
import net.krodark.asterion.network.DeadSunEventPayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.krodark.asterion.network.BossEncounterResetPayload;
import net.krodark.asterion.network.BiomeAtmospherePayload;
import net.krodark.asterion.network.DazePayload;
import net.krodark.asterion.network.CrucibleControlPayload;
import net.krodark.asterion.network.CrucibleScreenPayload;
import net.krodark.asterion.network.ragdoll.*;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.entity.BombadierBeetleEntity;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.krodark.asterion.entity.ConstructEntity;
import net.krodark.asterion.entity.QueenBeetleEntity;
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
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
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
import net.krodark.asterion.block.RuneBlockItem;
import net.krodark.asterion.block.RuneDoorBlock;
import net.krodark.asterion.block.DirectionalGateBlock;
import net.krodark.asterion.block.WinchBlock;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.krodark.asterion.block.SkeletonBlock;
import net.krodark.asterion.block.SkeletonBlockEntity;
import net.krodark.asterion.block.ShatteredDeadWoodBlock;
import net.krodark.asterion.block.ShatteredDeadWoodBlockEntity;
import net.krodark.asterion.block.PassionBloomBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
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
import net.krodark.asterion.worldgen.OvergrowthPuddleFeature;
import net.krodark.asterion.worldgen.GiantDeadTreeFeature;
import net.krodark.asterion.worldgen.AncientGroundVineFeature;
import net.krodark.asterion.worldgen.AncientHangingVineFeature;
import net.krodark.asterion.worldgen.TaintedPetalsFeature;
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
    public static final SoundEvent MINOTAUR_STEP = registerSound("minotaur_step");
    public static final SoundEvent MINOTAUR_DOOR_OPENCLOSE = registerSound("minotaur_door_openclose");
    public static final SoundEvent METAL_HIT = registerSound("metal_hit_sound");
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
            properties -> new RotatedPillarBlock(properties.strength(3.2F, 5.0F)
                    .sound(SoundType.WOOD).ignitedByLava()));
    public static final Block DEAD_WOOD_PLANKS = registerBlock("dead_wood_planks", MapColor.COLOR_BROWN,
            properties -> new Block(properties.strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    public static final Block DEAD_WOOD_SLAB = registerBlock("dead_wood_slab", MapColor.COLOR_BROWN,
            properties -> new SlabBlock(properties.strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    public static final Block DEAD_WOOD_STAIRS = registerBlock("dead_wood_stairs", MapColor.COLOR_BROWN,
            properties -> new StairBlock(DEAD_WOOD_PLANKS.defaultBlockState(),
                    properties.strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()) { });
    public static final Block DEAD_WOOD_FENCE = registerBlock("dead_wood_fence", MapColor.COLOR_BROWN,
            properties -> new FenceBlock(properties.strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    public static final Block DEAD_WOOD_FENCE_GATE = registerBlock("dead_wood_fence_gate", MapColor.COLOR_BROWN,
            properties -> new FenceGateBlock(WoodType.OAK,
                    properties.strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    public static final ShatteredDeadWoodBlock SHATTERED_DEAD_WOOD = (ShatteredDeadWoodBlock)registerBlock(
            "shattered_dead_wood", MapColor.COLOR_BROWN,
            properties -> new ShatteredDeadWoodBlock(properties.noOcclusion()
                    .strength(3.2F, 5.0F).sound(SoundType.WOOD).ignitedByLava()));
    public static final Block ANCIENT_STONE = registerBlock("ancient_stone", MapColor.TERRACOTTA_BROWN,
            properties -> new Block(properties.strength(2.0F, 6.0F).sound(SoundType.STONE)));
    public static final Block CELESTIAL_BRONZE_ORE = registerBlock("celestial_bronze_ore", MapColor.COLOR_ORANGE,
            properties -> new Block(properties.strength(4.0F, 8.0F).sound(SoundType.DEEPSLATE)));
    public static final Block TARNISHED_GOLD_ORE = registerBlock("tarnished_gold_ore", MapColor.GOLD,
            properties -> new Block(properties.strength(3.5F, 7.0F).sound(SoundType.DEEPSLATE)));
    public static final Block CELESTIAL_GOLD_ORE = registerBlock("celestial_gold_ore", MapColor.GOLD,
            properties -> new Block(properties.strength(4.5F, 9.0F).sound(SoundType.DEEPSLATE)));
    public static final Block MOSSY_ANCIENT_STONE = registerBlock("mossy_ancient_stone", MapColor.TERRACOTTA_GREEN, Block::new);
    public static final Block ANCIENT_MOSS = registerBlock("ancient_moss", MapColor.TERRACOTTA_GREEN,
            properties -> new net.krodark.asterion.block.WaterloggedMossBlock(properties.strength(0.1F)
                    .sound(SoundType.MOSS)));
    public static final Block ANCIENT_MOSS_CARPET = registerBlock(
            "ancient_moss_carpet", MapColor.TERRACOTTA_GREEN,
            properties -> new net.krodark.asterion.block.WaterloggedMossCarpetBlock(properties.noCollision().strength(0.1F)
                    .sound(SoundType.MOSS_CARPET)));
    public static final LeavesBlock ANCIENT_LEAVES = (LeavesBlock)registerBlock(
            "ancient_leaves", MapColor.TERRACOTTA_BROWN,
            properties -> new UntintedParticleLeavesBlock(0.01F, ParticleTypes.PALE_OAK_LEAVES,
                    properties.strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()));
    public static final LeavesBlock TAINTED_LEAVES = (LeavesBlock)registerBlock(
            "tainted_leaves", MapColor.COLOR_RED,
            properties -> new UntintedParticleLeavesBlock(0.008F, ParticleTypes.CRIMSON_SPORE,
                    properties.strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()));
    public static final MultifaceBlock TAINTED_PETALS = (MultifaceBlock)registerBlock(
            "tainted_petals", MapColor.COLOR_RED,
            properties -> new MultifaceBlock(properties.noCollision().replaceable().instabreak()
                    .sound(SoundType.PINK_PETALS).noOcclusion()));
    public static final PassionBloomBlock PASSION_BLOOM = (PassionBloomBlock)registerBlockWithoutItem(
            "tainted_heart", MapColor.COLOR_RED,
            properties -> new PassionBloomBlock(properties.noCollision().instabreak()
                    .sound(SoundType.SWEET_BERRY_BUSH).noOcclusion()));
    public static final Block SHORT_GRASS = registerBlock("short_grass", MapColor.PLANT,
            properties -> new ShortGrassBlock(properties.noCollision().replaceable().instabreak()
                    .sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)));
    public static final Block ANCIENT_STONE_SLAB = registerBlock("ancient_stone_slab", MapColor.TERRACOTTA_BROWN, SlabBlock::new);
    public static final Block ANCIENT_STONE_STAIRS = registerBlock("ancient_stone_stairs", MapColor.TERRACOTTA_BROWN,
            properties -> new StairBlock(ANCIENT_STONE.defaultBlockState(), properties) { });
    public static final Block ANCIENT_STONE_WALL = registerBlock("ancient_stone_wall", MapColor.TERRACOTTA_BROWN, WallBlock::new);
    public static final Block MAZESTEEL_BLOCK = registerBlock("mazesteel_block", MapColor.METAL, Block::new);
    public static final Block MAZE_WALL_CORE = registerBlockWithoutItem("maze_wall_core", MapColor.METAL,
            properties -> new Block(properties.strength(-1.0F, 3_600_000F).sound(SoundType.METAL)));
    public static final Block MAZESTEEL_SLAB = registerBlock("mazesteel_slab", MapColor.METAL,
            properties -> new SlabBlock(properties.sound(SoundType.METAL)));
    public static final Block MAZESTEEL_STAIRS = registerBlock("mazesteel_stairs", MapColor.METAL,
            properties -> new StairBlock(MAZESTEEL_BLOCK.defaultBlockState(), properties.sound(SoundType.METAL)) { });
    public static final Block MAZESTEEL_BRICKS = registerBlock("mazesteel_bricks", MapColor.METAL,
            properties -> new Block(properties.sound(SoundType.METAL)));
    public static final Block MAZESTEEL_BRICK_SLAB = registerBlock("mazesteel_brick_slab", MapColor.METAL,
            properties -> new SlabBlock(properties.sound(SoundType.METAL)));
    public static final Block MAZESTEEL_BRICK_STAIRS = registerBlock("mazesteel_brick_stairs", MapColor.METAL,
            properties -> new StairBlock(MAZESTEEL_BRICKS.defaultBlockState(), properties.sound(SoundType.METAL)) { });
    public static final Block MAZESTEEL_BRICK_WALL = registerBlock("mazesteel_brick_wall", MapColor.METAL,
            properties -> new WallBlock(properties.sound(SoundType.METAL)));
    public static final Block POLISHED_MAZESTEEL = registerBlock("polished_mazesteel", MapColor.METAL,
            properties -> new Block(properties.sound(SoundType.METAL)));
    public static final Block POLISHED_MAZESTEEL_SLAB = registerBlock("polished_mazesteel_slab", MapColor.METAL,
            properties -> new SlabBlock(properties.sound(SoundType.METAL)));
    public static final Block POLISHED_MAZESTEEL_STAIRS = registerBlock("polished_mazesteel_stairs", MapColor.METAL,
            properties -> new StairBlock(POLISHED_MAZESTEEL.defaultBlockState(), properties.sound(SoundType.METAL)) { });
    public static final Block POLISHED_MAZESTEEL_WALL = registerBlock("polished_mazesteel_wall", MapColor.METAL,
            properties -> new WallBlock(properties.sound(SoundType.METAL)));
    public static final Block MAZESTEEL_BARS = registerBlock("mazesteel_bars", MapColor.METAL,
            properties -> new net.minecraft.world.level.block.IronBarsBlock(properties.noOcclusion().sound(SoundType.METAL)));
    public static final Block ZIPLINE_ANCHOR = registerBlock("zipline_anchor", MapColor.METAL,
            properties -> new net.krodark.asterion.block.ZiplineAnchorBlock(properties.noOcclusion()
                    .noCollision().strength(3F, 8F).sound(SoundType.CHAIN).noLootTable()));
    public static final BlockEntityType<net.krodark.asterion.block.ZiplineAnchorBlockEntity> ZIPLINE_ANCHOR_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("zipline_anchor"),
                    FabricBlockEntityTypeBuilder.create(net.krodark.asterion.block.ZiplineAnchorBlockEntity::new,
                            ZIPLINE_ANCHOR).build());
    private static final ResourceKey<Item> ZIPLINE_CREATOR_KEY = ResourceKey.create(Registries.ITEM, id("zipline_creator"));
    public static final Item ZIPLINE_CREATOR = Registry.register(BuiltInRegistries.ITEM, ZIPLINE_CREATOR_KEY,
            new net.krodark.asterion.item.ZiplineCreatorItem(new Item.Properties().setId(ZIPLINE_CREATOR_KEY).stacksTo(1)));
    private static final ResourceKey<Item> ZIPLINE_HOOK_KEY = ResourceKey.create(Registries.ITEM, id("zipline_hook"));
    public static final Item ZIPLINE_HOOK = Registry.register(BuiltInRegistries.ITEM, ZIPLINE_HOOK_KEY,
            new net.krodark.asterion.item.ZiplineHookItem(new Item.Properties().setId(ZIPLINE_HOOK_KEY).stacksTo(1)));
    public static final Block SLICK_CATACOMB_STONE = registerBlock("slick_catacomb_stone", MapColor.TERRACOTTA_CYAN,
            properties -> new Block(properties.friction(0.985F)));
    public static final Block GREEK_BRAZIER = registerBlock("greek_brazier", MapColor.COLOR_GREEN,
            properties -> new net.krodark.asterion.block.GreekBrazierBlock(properties.noOcclusion()
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 12 : 0)));
    public static final Block GREEK_FIRE_LANTERN = registerBlock("greek_fire_lantern", MapColor.COLOR_GREEN,
            properties -> new Block(properties.sound(SoundType.METAL).lightLevel(state -> 15)));
    public static final Block RED_FIRE_LANTERN = registerBlock("red_fire_lantern", MapColor.COLOR_RED,
            properties -> new Block(properties.sound(SoundType.METAL).lightLevel(state -> 15)));
    public static final net.krodark.asterion.block.GreekFireTorchBlock GREEK_FIRE_WALL_TORCH =
            (net.krodark.asterion.block.GreekFireTorchBlock)registerBlock("greek_fire_wall_torch", MapColor.COLOR_GREEN,
                    properties -> new net.krodark.asterion.block.GreekFireTorchBlock(properties.noOcclusion()
                            .strength(.4F).sound(SoundType.METAL).lightLevel(state ->
                                    state.getValue(net.krodark.asterion.block.GreekFireTorchBlock.LIT)?14:0), true,
                            net.krodark.asterion.block.GreekFireTorchBlock.FireColor.GREEK));
    public static final net.krodark.asterion.block.GreekFireTorchBlock GREEK_FIRE_FLOOR_TORCH =
            (net.krodark.asterion.block.GreekFireTorchBlock)registerBlock("greek_fire_floor_torch", MapColor.COLOR_GREEN,
                    properties -> new net.krodark.asterion.block.GreekFireTorchBlock(properties.noOcclusion()
                            .strength(.4F).sound(SoundType.METAL).lightLevel(state ->
                                    state.getValue(net.krodark.asterion.block.GreekFireTorchBlock.LIT)
                                            && state.getValue(net.krodark.asterion.block.GreekFireTorchBlock.TOP)
                                            ? 14 : 0), false,
                            net.krodark.asterion.block.GreekFireTorchBlock.FireColor.GREEK));
    public static final net.krodark.asterion.block.GreekFireTorchBlock RED_FIRE_WALL_TORCH = torch("red_fire_wall_torch",true,
            net.krodark.asterion.block.GreekFireTorchBlock.FireColor.RED);
    public static final net.krodark.asterion.block.GreekFireTorchBlock RED_FIRE_FLOOR_TORCH = torch("red_fire_floor_torch",false,
            net.krodark.asterion.block.GreekFireTorchBlock.FireColor.RED);
    public static final net.krodark.asterion.block.GreekFireTorchBlock ORANGE_FIRE_WALL_TORCH = torch("orange_fire_wall_torch",true,
            net.krodark.asterion.block.GreekFireTorchBlock.FireColor.ORANGE);
    public static final net.krodark.asterion.block.GreekFireTorchBlock ORANGE_FIRE_FLOOR_TORCH = torch("orange_fire_floor_torch",false,
            net.krodark.asterion.block.GreekFireTorchBlock.FireColor.ORANGE);
    public static final BlockEntityType<net.krodark.asterion.block.GreekFireTorchBlockEntity> GREEK_FIRE_TORCH_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,id("greek_fire_torch"),FabricBlockEntityTypeBuilder.create(
                    net.krodark.asterion.block.GreekFireTorchBlockEntity::new,GREEK_FIRE_WALL_TORCH,GREEK_FIRE_FLOOR_TORCH,
                    RED_FIRE_WALL_TORCH,RED_FIRE_FLOOR_TORCH,ORANGE_FIRE_WALL_TORCH,ORANGE_FIRE_FLOOR_TORCH).build());
    public static final Block LAMENTER = registerBlock("lamenter", MapColor.TERRACOTTA_BROWN,
            net.krodark.asterion.block.LamenterBlock::new);
    public static final Block CRUCIBLE = registerBlock("crucible", MapColor.METAL,
            properties -> new net.krodark.asterion.block.CrucibleBlock(properties.noOcclusion()
                    .strength(4.5F, 10.0F).sound(SoundType.METAL)));
    public static final Block MOLD_HOLDER = registerBlock("mold_holder", MapColor.METAL,
            properties -> new net.krodark.asterion.block.MoldHolderBlock(properties.noOcclusion()
                    .strength(3.0F, 7.0F).sound(SoundType.METAL)));
    public static final BlockEntityType<net.krodark.asterion.block.CrucibleBlockEntity> CRUCIBLE_BLOCK_ENTITY =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("crucible"),
                    FabricBlockEntityTypeBuilder.create(net.krodark.asterion.block.CrucibleBlockEntity::new,
                            CRUCIBLE).build());
    public static final Block PRESSURE_BUTTON = registerBlock("pressure_button", MapColor.METAL,
            properties -> new net.krodark.asterion.block.PressureButtonBlock(properties.noOcclusion()
                    .strength(1.4F,6.0F).sound(SoundType.METAL)));
    public static final BlockEntityType<net.krodark.asterion.block.LamenterBlockEntity> LAMENTER_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("lamenter"), FabricBlockEntityTypeBuilder.create(
                    net.krodark.asterion.block.LamenterBlockEntity::new, LAMENTER).build());
    public static final Block PILLAR = registerBlock("pillar", MapColor.COLOR_BROWN,
            props -> new net.krodark.asterion.block.PillarBlock(props.noOcclusion().strength(8F, 1200F).sound(net.minecraft.world.level.block.SoundType.WOOD).noLootTable()));
    public static final BlockEntityType<net.krodark.asterion.block.PillarBlockEntity> PILLAR_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("pillar"), FabricBlockEntityTypeBuilder.create(
                    net.krodark.asterion.block.PillarBlockEntity::new, PILLAR).build());
    public static final Block MINOTAUR_DOOR = registerBlock("minotaur_door", MapColor.COLOR_BROWN,
            properties -> new net.krodark.asterion.block.MinotaurDoorBlock(properties.noOcclusion().strength(8F, 1200F).noLootTable()));
    public static final BlockEntityType<net.krodark.asterion.block.MinotaurDoorBlockEntity> MINOTAUR_DOOR_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("minotaur_door"), FabricBlockEntityTypeBuilder.create(
                    net.krodark.asterion.block.MinotaurDoorBlockEntity::new, MINOTAUR_DOOR).build());
    public static final Block CURSED_BRAZIER_DOOR = registerBlock("cursed_brazier_door", MapColor.COLOR_BROWN,
            properties -> new net.krodark.asterion.block.CursedBrazierDoorBlock(properties.noOcclusion()
                    .strength(8F, 1200F).sound(SoundType.METAL).noLootTable()));
    public static final BlockEntityType<net.krodark.asterion.block.CursedBrazierDoorBlockEntity> CURSED_BRAZIER_DOOR_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("cursed_brazier_door"), FabricBlockEntityTypeBuilder.create(
                    net.krodark.asterion.block.CursedBrazierDoorBlockEntity::new, CURSED_BRAZIER_DOOR).build());
    public static final Block BARREL_DOOR = registerBlock("barrel_door", MapColor.COLOR_BROWN,
            properties -> new net.krodark.asterion.block.BarrelDoorBlock(properties.noOcclusion().strength(3F, 6F).sound(SoundType.WOOD).noLootTable()));
    public static final BlockEntityType<net.krodark.asterion.block.BarrelDoorBlockEntity> BARREL_DOOR_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("barrel_door"), FabricBlockEntityTypeBuilder.create(
                    net.krodark.asterion.block.BarrelDoorBlockEntity::new, BARREL_DOOR).build());
    public static final ChainBlock MAZESTEEL_CHAIN = (ChainBlock)registerBlock(
            "mazesteel_chain", MapColor.METAL,
            properties -> new ChainBlock(properties.noOcclusion().sound(SoundType.CHAIN)));
    public static final DirectionalGateBlock MAZESTEEL_GATE = (DirectionalGateBlock)registerBlock(
            "mazesteel_gate", MapColor.METAL,
            properties -> new DirectionalGateBlock(properties.noOcclusion()));
    public static final WinchBlock WINCH = (WinchBlock)registerBlock(
            "winch", MapColor.METAL, WinchBlock::new);
    public static final net.krodark.asterion.block.OmegaLockBlock OMEGA_LOCK =
            (net.krodark.asterion.block.OmegaLockBlock)registerBlock("omega_lock", MapColor.METAL,
                    properties -> new net.krodark.asterion.block.OmegaLockBlock(properties.noOcclusion()
                            .strength(8F, 1200F).sound(SoundType.METAL)));
    public static final BlockEntityType<net.krodark.asterion.block.OmegaLockBlockEntity> OMEGA_LOCK_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("omega_lock"), FabricBlockEntityTypeBuilder.create(
                    net.krodark.asterion.block.OmegaLockBlockEntity::new, OMEGA_LOCK).build());
    public static final LabyrinthVineBlock LABYRINTH_VINE = (LabyrinthVineBlock)registerBlock(
            "labyrinth_vine", MapColor.COLOR_BROWN,
            properties -> new LabyrinthVineBlock(properties.noOcclusion().strength(0.4F)
                    .sound(SoundType.VINE).lightLevel(state -> 5)),
            properties -> properties.food(new FoodProperties.Builder().nutrition(1)
                            .saturationModifier(0.15F).build(),
                    Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(
                            new MobEffectInstance(MobEffects.GLOWING, 10 * 20))).build()));
    private static final ResourceKey<Item> POPPED_ANCIENT_VINES_KEY = ResourceKey.create(
            Registries.ITEM, id("popped_ancient_vines"));
    public static final Item POPPED_ANCIENT_VINES = Registry.register(BuiltInRegistries.ITEM,
            POPPED_ANCIENT_VINES_KEY, new Item(new Item.Properties().setId(POPPED_ANCIENT_VINES_KEY)
                    .food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.45F).build())));
    public static final Item INGOT_CAST = registerSimpleItem("ingot_cast");
    public static final Item SWORD_GUARD_CAST = registerSimpleItem("sword_guard_cast");
    public static final Item SWORD_POMMEL_CAST = registerSimpleItem("sword_pommel_cast");
    public static final Item SWORD_BLADE_CAST = registerSimpleItem("sword_blade_cast");
    public static final Item AXE_HEAD_CAST = registerSimpleItem("axe_head_cast");
    public static final Item CELESTIAL_BRONZE_INGOT = registerPurityMetalItem("celestial_bronze_ingot");
    public static final Item TARNISHED_GOLD_INGOT = registerPurityMetalItem("tarnished_gold_ingot");
    public static final Item CELESTIAL_GOLD_INGOT = registerPurityMetalItem("celestial_gold_ingot");
    public static final Item BONESTEEL_INGOT = registerPurityMetalItem("bonesteel_ingot");
    public static final Item CELESTIAL_STEEL_INGOT = registerPurityMetalItem("celestial_steel_ingot");
    public static final Item FORGED_INGOT = registerForgedComponentItem("forged_ingot");
    public static final Item FORGED_SWORD_GUARD = registerForgedComponentItem("forged_sword_guard");
    public static final Item FORGED_SWORD_POMMEL = registerForgedComponentItem("forged_sword_pommel");
    public static final Item FORGED_SWORD_BLADE = registerForgedComponentItem("forged_sword_blade");
    public static final Item FORGED_SWORD = registerForgedSwordItem("forged_sword");
    public static final Item FORGED_AXE_HEAD = registerForgedComponentItem("forged_axe_head");
    public static final net.minecraft.world.item.crafting.RecipeSerializer<net.krodark.asterion.recipe.ForgedSwordRecipe>
            FORGED_SWORD_RECIPE = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("forged_sword"),
            new net.minecraft.world.item.crafting.RecipeSerializer<>(
                    com.mojang.serialization.MapCodec.unit(new net.krodark.asterion.recipe.ForgedSwordRecipe()),
                    net.minecraft.network.codec.StreamCodec.unit(new net.krodark.asterion.recipe.ForgedSwordRecipe())));
    public static final net.minecraft.world.item.crafting.RecipeSerializer<net.krodark.asterion.recipe.RemovedRecipe>
            REMOVED_RECIPE = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("removed"),
            new net.minecraft.world.item.crafting.RecipeSerializer<>(
                    com.mojang.serialization.MapCodec.unit(new net.krodark.asterion.recipe.RemovedRecipe()),
                    net.minecraft.network.codec.StreamCodec.unit(new net.krodark.asterion.recipe.RemovedRecipe())));
    public static final SkeletonBlock SKELETON = (SkeletonBlock)registerBlock(
            "skeleton", MapColor.COLOR_LIGHT_GRAY,
            properties -> new SkeletonBlock(properties.noOcclusion().strength(0.45F)
                    .sound(SoundType.BONE_BLOCK)));
    public static final RuneBlock[] RUNE_BLOCKS = registerRuneBlocks();
    public static final Block[] RUNE_STONE_BLOCKS = registerRuneStoneBlocks();
    public static final Item[] RUNE_TABLETS = registerRuneTablets();
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
    private static final ResourceKey<EntityType<?>> MINOTAUR_ENTITY_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, id("minotaur"));
    public static final EntityType<MinotaurEntity> MINOTAUR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            MINOTAUR_ENTITY_KEY,
            EntityType.Builder.of(MinotaurEntity::new, MobCategory.MONSTER)
                    .sized(1.25F * AsterionConfig.INSTANCE.minotaurScale,
                            2.75F * AsterionConfig.INSTANCE.minotaurScale)
                    .eyeHeight(2.35F * AsterionConfig.INSTANCE.minotaurScale)
                    .clientTrackingRange(16).build(MINOTAUR_ENTITY_KEY)
    );
    private static final ResourceKey<EntityType<?>> MINOTAUR_AXE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("minotaur_axe"));
    public static final EntityType<net.krodark.asterion.entity.MinotaurAxeEntity> MINOTAUR_AXE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, MINOTAUR_AXE_KEY,
            EntityType.Builder.<net.krodark.asterion.entity.MinotaurAxeEntity>of(net.krodark.asterion.entity.MinotaurAxeEntity::new, MobCategory.MISC)
                    .sized(1, 1).clientTrackingRange(16).updateInterval(1).build(MINOTAUR_AXE_KEY));
    private static final ResourceKey<EntityType<?>> BOMBARDIER_BEETLE_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, id("bombadier_beetle"));
    private static final ResourceKey<EntityType<?>> RUNE_BEETLE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("rune_beetle"));
    public static final EntityType<net.krodark.asterion.entity.RuneBeetleEntity> RUNE_BEETLE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, RUNE_BEETLE_KEY,
            EntityType.Builder.of(net.krodark.asterion.entity.RuneBeetleEntity::new, MobCategory.CREATURE)
                    .sized(.45F, .25F).eyeHeight(.15F).clientTrackingRange(8).build(RUNE_BEETLE_KEY));
    public static final EntityType<BombadierBeetleEntity> BOMBARDIER_BEETLE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            BOMBARDIER_BEETLE_KEY,
            EntityType.Builder.of(BombadierBeetleEntity::new, MobCategory.CREATURE)
                    .sized(0.8F, 0.45F).eyeHeight(0.3F).clientTrackingRange(10)
                    .fireImmune().build(BOMBARDIER_BEETLE_KEY)
    );
    private static final ResourceKey<EntityType<?>> CONSTRUCT_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, id("construct"));
    public static final EntityType<ConstructEntity> CONSTRUCT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, CONSTRUCT_KEY,
            EntityType.Builder.of(ConstructEntity::new, MobCategory.MONSTER)
                    // Model pixels map 1:16: x/z [-8,8], y [0,35].
                    .sized(1.0F, 2.1875F).eyeHeight(1.75F).clientTrackingRange(10).fireImmune()
                    .build(CONSTRUCT_KEY));
    private static final ResourceKey<EntityType<?>> QUEEN_BEETLE_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, id("queen_beetle"));
    public static final EntityType<QueenBeetleEntity> QUEEN_BEETLE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, QUEEN_BEETLE_KEY,
            EntityType.Builder.of(QueenBeetleEntity::new, MobCategory.CREATURE)
                    // The widest authored span is 42 px and the highest point is 26 px.
                    .sized(2.625F, 1.625F).eyeHeight(0.9F).clientTrackingRange(12)
                    .build(QUEEN_BEETLE_KEY));
    private static final ResourceKey<Item> CONSTRUCT_EGG_KEY = ResourceKey.create(
            Registries.ITEM, id("construct_spawn_egg"));
    public static final Item CONSTRUCT_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM,
            CONSTRUCT_EGG_KEY, new SpawnEggItem(new Item.Properties().setId(CONSTRUCT_EGG_KEY)
                    .spawnEgg(CONSTRUCT)));
    private static final ResourceKey<Item> QUEEN_BEETLE_EGG_KEY = ResourceKey.create(
            Registries.ITEM, id("queen_beetle_spawn_egg"));
    public static final Item QUEEN_BEETLE_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM,
            QUEEN_BEETLE_EGG_KEY, new SpawnEggItem(new Item.Properties().setId(QUEEN_BEETLE_EGG_KEY)
                    .spawnEgg(QUEEN_BEETLE)));
    private static final ResourceKey<EntityType<?>> SCARLET_CENTIPEDE_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, id("scarlet_centipede"));
    public static final EntityType<ScarletCentipedeEntity> SCARLET_CENTIPEDE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            SCARLET_CENTIPEDE_KEY,
            EntityType.Builder.of(ScarletCentipedeEntity::new, MobCategory.CREATURE)
                    .sized(1.785F, 0.697F).eyeHeight(0.527F).clientTrackingRange(48)
                    .build(SCARLET_CENTIPEDE_KEY)
    );
    private static final ResourceKey<Item> SCARLET_CENTIPEDE_SPAWN_EGG_KEY = ResourceKey.create(
            Registries.ITEM, id("scarlet_centipede_spawn_egg"));
    public static final Item SCARLET_CENTIPEDE_SPAWN_EGG = Registry.register(
            BuiltInRegistries.ITEM,
            SCARLET_CENTIPEDE_SPAWN_EGG_KEY,
            new SpawnEggItem(new Item.Properties().setId(SCARLET_CENTIPEDE_SPAWN_EGG_KEY)
                    .spawnEgg(SCARLET_CENTIPEDE))
    );
    public static final SimpleParticleType BOMBARDIER_STENCH = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("bombardier_stench"), FabricParticleTypes.simple());
    public static final SimpleParticleType MINOTAUR_BELCH_SMOKE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("minotaur_belch_smoke"), FabricParticleTypes.simple());
    public static final SimpleParticleType FLAMETHROWER_GAS_FIRE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("flamethrower_gas_fire"), FabricParticleTypes.simple());
    public static final SimpleParticleType FLAMETHROWER_GAS = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("flamethrower_gas"), FabricParticleTypes.simple());
    public static final SimpleParticleType BOMBARDIER_GAS_FIRE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("bombardier_gas_fire"), FabricParticleTypes.simple());
    public static final SimpleParticleType GREEK_FIRE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("greek_fire"), FabricParticleTypes.simple());
    public static final SimpleParticleType MINOTAUR_BELCH_FIRE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("minotaur_belch_fire"), FabricParticleTypes.simple());
    public static final SimpleParticleType GREEK_FIRE_SOOT = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("greek_fire_soot"), FabricParticleTypes.simple());
    public static final SimpleParticleType BRAZIER_FIRE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("brazier_fire"), FabricParticleTypes.simple());
    public static final SimpleParticleType LAMENTER_TEAR = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("lamenter_tear"), FabricParticleTypes.simple());
    public static final SimpleParticleType DOOR_SMOKE = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("door_smoke"), FabricParticleTypes.simple());
    public static final SimpleParticleType DOOR_DUST = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("door_dust"), FabricParticleTypes.simple());
    public static final SimpleParticleType FLY = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("fly"), FabricParticleTypes.simple());
    public static final SimpleParticleType FIREFLY = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("firefly"), FabricParticleTypes.simple());
    public static final SimpleParticleType HOSTILE_FIREFLY = Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, id("hostile_firefly"), FabricParticleTypes.simple());
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
    private static final TagKey<Block> INCORRECT_FOR_CELESTIAL_BRONZE_TOOL = TagKey.create(
            Registries.BLOCK, id("incorrect_for_celestial_bronze_tool"));
    private static final TagKey<Item> REPAIRS_CELESTIAL_BRONZE_TOOLS = TagKey.create(
            Registries.ITEM, id("repairs_celestial_bronze_tools"));
    public static final ToolMaterial CELESTIAL_BRONZE = new ToolMaterial(
            INCORRECT_FOR_CELESTIAL_BRONZE_TOOL, 1200, 7.5F, 3.5F, 18,
            REPAIRS_CELESTIAL_BRONZE_TOOLS);
    private static final ResourceKey<Item> CELESTIAL_BRONZE_SWORD_KEY = ResourceKey.create(
            Registries.ITEM, id("celestial_bronze_sword"));
    public static final Item CELESTIAL_BRONZE_SWORD = Registry.register(
            BuiltInRegistries.ITEM, CELESTIAL_BRONZE_SWORD_KEY,
            new Item(new Item.Properties().setId(CELESTIAL_BRONZE_SWORD_KEY)
                    .sword(CELESTIAL_BRONZE, 3.5F, -2.3F)
                    .rarity(Rarity.RARE).fireResistant()));
    private static final ResourceKey<Item> MINOTAUR_KEY_ID = ResourceKey.create(Registries.ITEM, id("minotaur_key"));
    public static final Item MINOTAUR_KEY = Registry.register(BuiltInRegistries.ITEM, MINOTAUR_KEY_ID,
            new Item(new Item.Properties().setId(MINOTAUR_KEY_ID).stacksTo(1).rarity(Rarity.UNCOMMON)));
    private static final ResourceKey<Item> OMEGA_KEY_ID = ResourceKey.create(Registries.ITEM, id("omega_key"));
    public static final Item OMEGA_KEY = Registry.register(BuiltInRegistries.ITEM, OMEGA_KEY_ID,
            new Item(new Item.Properties().setId(OMEGA_KEY_ID).stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    private static final ResourceKey<Item> TAINTED_HEART_KEY = ResourceKey.create(
            Registries.ITEM, id("tainted_heart"));
    public static final Item TAINTED_HEART = Registry.register(
            BuiltInRegistries.ITEM, TAINTED_HEART_KEY,
            new BlockItem(PASSION_BLOOM, new Item.Properties().setId(TAINTED_HEART_KEY)));
    private static final ResourceKey<Item> TAINTED_HEART_EATABLE_KEY = ResourceKey.create(
            Registries.ITEM, id("tainted_heart_eatable"));
    public static final Item TAINTED_HEART_EATABLE = Registry.register(
            BuiltInRegistries.ITEM, TAINTED_HEART_EATABLE_KEY,
            new Item(new Item.Properties().setId(TAINTED_HEART_EATABLE_KEY)
                    .food(new FoodProperties.Builder().nutrition(5)
                            .saturationModifier(0.55F).build())));
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
                        output.accept(CELESTIAL_BRONZE_SWORD);
                        output.accept(net.krodark.asterion.fluid.HeavyWater.BUCKET);
                        output.accept(SLICK_CATACOMB_STONE);
                        output.accept(GREEK_BRAZIER);
                        output.accept(GREEK_FIRE_LANTERN);
                        output.accept(RED_FIRE_LANTERN);
                        output.accept(LAMENTER);
                        output.accept(PRESSURE_BUTTON);
                        output.accept(ANTIKYTHERA_BLUEPRINT);
                        output.accept(SCARLET_CENTIPEDE_SPAWN_EGG);
                        output.accept(CONSTRUCT_SPAWN_EGG);
                        output.accept(QUEEN_BEETLE_SPAWN_EGG);
                        output.accept(TAINTED_HEART);
                        output.accept(TAINTED_HEART_EATABLE);
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
                        output.accept(DEAD_WOOD_PLANKS);
                        output.accept(DEAD_WOOD_SLAB);
                        output.accept(DEAD_WOOD_STAIRS);
                        output.accept(DEAD_WOOD_FENCE);
                        output.accept(DEAD_WOOD_FENCE_GATE);
                        output.accept(SHATTERED_DEAD_WOOD);
                        output.accept(ANCIENT_STONE);
                        output.accept(MOSSY_ANCIENT_STONE);
                        output.accept(ANCIENT_MOSS);
                        output.accept(ANCIENT_MOSS_CARPET);
                        output.accept(ANCIENT_LEAVES);
                        output.accept(TAINTED_LEAVES);
                        output.accept(TAINTED_PETALS);
                        output.accept(SHORT_GRASS);
                        output.accept(ANCIENT_STONE_SLAB);
                        output.accept(ANCIENT_STONE_STAIRS);
                        output.accept(ANCIENT_STONE_WALL);
                        output.accept(MAZESTEEL_BLOCK);
                        output.accept(MAZESTEEL_SLAB);
                        output.accept(MAZESTEEL_STAIRS);
                        output.accept(MAZESTEEL_BRICKS);
                        output.accept(MAZESTEEL_BRICK_SLAB);
                        output.accept(MAZESTEEL_BRICK_STAIRS);
                        output.accept(MAZESTEEL_BRICK_WALL);
                        output.accept(POLISHED_MAZESTEEL);
                        output.accept(POLISHED_MAZESTEEL_SLAB);
                        output.accept(POLISHED_MAZESTEEL_STAIRS);
                        output.accept(POLISHED_MAZESTEEL_WALL);
                        output.accept(ZIPLINE_CREATOR);
                        output.accept(ZIPLINE_HOOK);
                        output.accept(MAZESTEEL_BARS);
                        output.accept(MAZESTEEL_CHAIN);
                        output.accept(MAZESTEEL_GATE);
                        output.accept(WINCH);
                        output.accept(OMEGA_LOCK);
                        output.accept(LABYRINTH_VINE);
                        output.accept(POPPED_ANCIENT_VINES);
                        output.accept(GREEK_FIRE_WALL_TORCH);
                        output.accept(GREEK_FIRE_FLOOR_TORCH);
                        output.accept(RED_FIRE_WALL_TORCH);
                        output.accept(RED_FIRE_FLOOR_TORCH);
                        output.accept(ORANGE_FIRE_WALL_TORCH);
                        output.accept(ORANGE_FIRE_FLOOR_TORCH);
                        output.accept(SKELETON);
                        output.accept(MINOTAUR_DOOR);
                        output.accept(CURSED_BRAZIER_DOOR);
                        output.accept(PILLAR);
                        output.accept(BARREL_DOOR);
                        output.accept(MINOTAUR_KEY);
                        output.accept(OMEGA_KEY);
                    })
                    .build()
    );
    public static final CreativeModeTab FORGING_ITEM_GROUP = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("forging")),
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.asterion.forging"))
                    .icon(() -> new ItemStack(CRUCIBLE))
                    .displayItems((parameters, output) -> {
                        output.accept(CRUCIBLE);
                        output.accept(MOLD_HOLDER);
                        output.accept(CELESTIAL_BRONZE_ORE);
                        output.accept(TARNISHED_GOLD_ORE);
                        output.accept(CELESTIAL_GOLD_ORE);
                        output.accept(CELESTIAL_BRONZE_INGOT);
                        output.accept(TARNISHED_GOLD_INGOT);
                        output.accept(CELESTIAL_GOLD_INGOT);
                        output.accept(BONESTEEL_INGOT);
                        output.accept(CELESTIAL_STEEL_INGOT);
                        output.accept(net.minecraft.world.item.Items.IRON_INGOT);
                        output.accept(net.minecraft.world.item.Items.COPPER_INGOT);
                        output.accept(net.minecraft.world.item.Items.GOLD_INGOT);
                        output.accept(net.minecraft.world.item.Items.NETHERITE_INGOT);
                        output.accept(INGOT_CAST);
                        output.accept(SWORD_GUARD_CAST);
                        output.accept(SWORD_POMMEL_CAST);
                        output.accept(SWORD_BLADE_CAST);
                        output.accept(AXE_HEAD_CAST);
                        output.accept(FORGED_INGOT);
                        for (int metal : new int[]{5, 4, 7, 6, 1, 8, 0, 3, 2}) {
                            ItemStack blade = forgePart(FORGED_SWORD_BLADE, metal, "Sword Blade");
                            ItemStack guard = forgePart(FORGED_SWORD_GUARD, metal, "Sword Guard");
                            ItemStack pommel = forgePart(FORGED_SWORD_POMMEL, metal, "Sword Pommel");
                            output.accept(blade); output.accept(guard); output.accept(pommel);
                            output.accept(new net.krodark.asterion.recipe.ForgedSwordRecipe().assemble(
                                    net.minecraft.world.item.crafting.CraftingInput.of(3, 1,
                                            java.util.List.of(blade, guard, pommel))));
                        }
                        output.accept(FORGED_AXE_HEAD);
                        output.accept(CELESTIAL_BRONZE_SWORD);
                    }).build());
    public static final CreativeModeTab RUNE_ITEM_GROUP = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("runes")),
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.asterion.runes"))
                    .icon(() -> new ItemStack(RUNE_TABLETS[0]))
                    .displayItems((parameters, output) -> {
                        for (Item tablet : RUNE_TABLETS) output.accept(tablet);
                        for (RuneBlock rune : RUNE_BLOCKS) output.accept(rune);
                        for (Block rune : RUNE_STONE_BLOCKS) output.accept(rune);
                        output.accept(RUNE_ZONE_DOOR);
                        output.accept(net.krodark.asterion.block.RespawnObelisks.CHARGED_RUNE);
                        output.accept(net.krodark.asterion.block.RespawnObelisks.ALTAR);
                        output.accept(net.krodark.asterion.block.RespawnObelisks.OBELISK);
                    }).build());

    public static final Feature<NoneFeatureConfiguration> UNDERWATER_RUIN_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("underwater_ruin"),
            new UnderwaterRuinFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> CATACOMBS_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("catacombs"),
            new net.krodark.asterion.worldgen.CatacombFeature(NoneFeatureConfiguration.CODEC));
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
    public static final Feature<NoneFeatureConfiguration> OVERGROWTH_PUDDLE_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("overgrowth_puddle"),
            new OvergrowthPuddleFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> GIANT_DEAD_TREE_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("giant_dead_tree"),
            new GiantDeadTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> ANCIENT_GROUND_VINE_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("ancient_ground_vines"),
            new AncientGroundVineFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> ANCIENT_HANGING_VINE_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("ancient_hanging_vines"),
            new AncientHangingVineFeature(NoneFeatureConfiguration.CODEC));
    public static final Feature<NoneFeatureConfiguration> TAINTED_PETALS_FEATURE = Registry.register(
            BuiltInRegistries.FEATURE, id("tainted_petals"),
            new TaintedPetalsFeature(NoneFeatureConfiguration.CODEC));
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
    private static final ResourceKey<PlacedFeature> OVERGROWTH_PUDDLE_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("overgrowth_puddle"));
    private static final ResourceKey<PlacedFeature> GIANT_DEAD_TREE_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("giant_dead_tree"));
    private static final ResourceKey<PlacedFeature> ANCIENT_GROUND_VINE_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("ancient_ground_vines"));
    private static final ResourceKey<PlacedFeature> ANCIENT_HANGING_VINE_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("ancient_hanging_vines"));
    private static final ResourceKey<PlacedFeature> TAINTED_PETALS_PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, id("tainted_petals"));

    private static void registerDeadWoodProperties() {
        FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
        fire.add(DEAD_WOOD, 5, 5);
        fire.add(SHATTERED_DEAD_WOOD, 5, 5);
        fire.add(DEAD_WOOD_PLANKS, 5, 20);
        fire.add(DEAD_WOOD_SLAB, 5, 20);
        fire.add(DEAD_WOOD_STAIRS, 5, 20);
        fire.add(DEAD_WOOD_FENCE, 5, 20);
        fire.add(DEAD_WOOD_FENCE_GATE, 5, 20);

    }

    @Override
    public void onInitialize() {
        registerDeadWoodProperties();
        net.krodark.asterion.game.GameplayContent.initialize();
        net.krodark.asterion.game.EncounterKeyRecovery.initialize();
        net.krodark.asterion.game.ArenaDeathRecovery.initialize();
        net.krodark.asterion.effect.GreekFireBurn.initialize();
        net.krodark.asterion.effect.SingedEffect.initialize();
        ServerTickEvents.END_SERVER_TICK.register(net.krodark.asterion.effect.SingedScars::tick);
        ServerTickEvents.END_SERVER_TICK.register(net.krodark.asterion.forging.OrePuritySystem::tick);
        net.krodark.asterion.fluid.HeavyWater.initialize();
        net.krodark.asterion.block.RespawnObelisks.initialize();
        AsterionConfig.INSTANCE.sanitize();
        PayloadTypeRegistry.clientboundPlay().register(DimensionTransitionPayload.TYPE, DimensionTransitionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EntryOmenPayload.TYPE, EntryOmenPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BossFinalePayload.TYPE, BossFinalePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                net.krodark.asterion.network.RoofCollapsePayload.TYPE,
                net.krodark.asterion.network.RoofCollapsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(net.krodark.asterion.network.BossEntrancePayload.TYPE,
                net.krodark.asterion.network.BossEntrancePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                net.krodark.asterion.network.CursedBrazierAwakeningPayload.TYPE,
                net.krodark.asterion.network.CursedBrazierAwakeningPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GatewayPortalPayload.TYPE, GatewayPortalPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TransitionReadyPayload.TYPE, TransitionReadyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PressureButtonHoldPayload.TYPE,PressureButtonHoldPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MazeZapPayload.TYPE, MazeZapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeadSunEventPayload.TYPE, DeadSunEventPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MazeShiftPayload.TYPE, MazeShiftPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(net.krodark.asterion.network.ArenaDebrisPayload.TYPE, net.krodark.asterion.network.ArenaDebrisPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(net.krodark.asterion.network.MinotaurImpactPayload.TYPE,
                net.krodark.asterion.network.MinotaurImpactPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(net.krodark.asterion.worldgen.ArenaDebris::flush);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> net.krodark.asterion.worldgen.ArenaDebris.clear());
        PayloadTypeRegistry.clientboundPlay().register(net.krodark.asterion.network.DoorBreakPayload.TYPE,
                net.krodark.asterion.network.DoorBreakPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DeadSunStrikePayload.TYPE, DeadSunStrikePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BossTelegraphPayload.TYPE, BossTelegraphPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BossEncounterResetPayload.TYPE, BossEncounterResetPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DazePayload.TYPE, DazePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BiomeAtmospherePayload.TYPE, BiomeAtmospherePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CrucibleScreenPayload.TYPE, CrucibleScreenPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CrucibleControlPayload.TYPE, CrucibleControlPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                net.krodark.asterion.network.QueenBeetleQuestPayload.TYPE,
                net.krodark.asterion.network.QueenBeetleQuestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RagdollImpulsePayload.TYPE, RagdollImpulsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RagdollPosePayload.TYPE, RagdollPosePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RagdollStatePayload.TYPE, RagdollStatePayload.CODEC);
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
        net.krodark.asterion.network.CentipedeNetworking.initialize();
        PressureButtonNetworking.initialize();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                CrucibleControlPayload.TYPE, (payload, context) -> context.server().execute(() -> {
                    net.minecraft.server.level.ServerPlayer player = context.player();
                    if (!player.level().getChunkSource().hasChunk(payload.pos().getX() >> 4, payload.pos().getZ() >> 4)
                            || player.distanceToSqr(payload.pos().getCenter()) > 64.0D) return;
                    if (player.level().getBlockEntity(payload.pos())
                            instanceof net.krodark.asterion.block.CrucibleBlockEntity crucible)
                        crucible.control(player, payload.action());
                }));
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                TransitionReadyPayload.TYPE, (payload, context) -> context.server().execute(() ->
                        WorldGenerator.markTransitionReady(context.player())));
        DeadSunEventSystem.registerCommands();
        net.krodark.asterion.command.CentipedeCommands.register();
        net.krodark.asterion.event.CatacombFloodState.registerCommands();
        DynamicBlockLights.initialize();
        PortalCommands.register();
        net.krodark.asterion.command.CatacombLocateCommands.register();
        net.krodark.asterion.command.MinotaurDebugCommands.register();
        FabricDefaultAttributeRegistry.register(MINOTAUR, MinotaurEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BOMBARDIER_BEETLE, BombadierBeetleEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RUNE_BEETLE, net.krodark.asterion.entity.RuneBeetleEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SCARLET_CENTIPEDE, ScarletCentipedeEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CONSTRUCT, ConstructEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(QUEEN_BEETLE, QueenBeetleEntity.createAttributes());
        ServerChunkEvents.CHUNK_LOAD.register(WorldGenerator::onChunkLoad);
        ServerChunkEvents.CHUNK_LOAD.register(CatacombFloodState::onChunkLoad);
        ServerChunkEvents.CHUNK_UNLOAD.register(CatacombFloodState::onChunkUnload);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CatacombFloodState.clear());
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                        || !state.is(MAZE_WALL_CORE)
                        && !WorldGenerator.isActivePortalProtected(serverLevel, pos));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && !net.krodark.asterion.worldgen.CatacombProtection.isOre(state))
                WorldGenerator.trackMazeBreak(serverLevel, pos, state);
        });
        BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OCEAN),
                GenerationStep.Decoration.SURFACE_STRUCTURES, UNDERWATER_RUIN_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                // FlatLevelSource drops the two structure-decoration steps; keep this jigsaw
                // feature in the underground decoration step so it runs in the maze dimension.
                GenerationStep.Decoration.UNDERGROUND_DECORATION,
                ResourceKey.create(Registries.PLACED_FEATURE, id("catacombs")));
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
                GenerationStep.Decoration.VEGETAL_DECORATION, OVERGROWTH_PUDDLE_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, OVERGROWTH_BRIDGE_CHAIN_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, ANCIENT_GROUND_VINE_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, ANCIENT_HANGING_VINE_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                GenerationStep.Decoration.VEGETAL_DECORATION, TAINTED_PETALS_PLACED);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                MobCategory.CREATURE, BOMBARDIER_BEETLE, 12, 1, 3);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                MobCategory.CREATURE, SCARLET_CENTIPEDE, 5, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.THE_VOID),
                MobCategory.MONSTER, CONSTRUCT, 1, 1, 1);
        // Enforce maze-zone creature restrictions for every spawn path, including
        // natural biome spawning, eggs and commands.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ConstructEntity
                    && level.dimension().equals(ASTERION_LEVEL)
                    && !ConstructEntity.isAllowedAsterionLocation(entity.blockPosition())) {
                entity.discard();
                return;
            }
            if (entity instanceof ScarletCentipedeEntity
                    && level.dimension().equals(ASTERION_LEVEL)
                    && (!WorldGenerator.isAncientBiomeAt(entity.getX(),entity.getZ())
                    || net.krodark.asterion.worldgen.CatacombLayout.contains(entity.blockPosition())
                    || net.krodark.asterion.worldgen.AuthoredCatacombs.insideCursedBrazierRoom(entity.blockPosition())
                    || Math.abs((long)entity.getBlockX()) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS
                    && Math.abs((long)entity.getBlockZ()) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS))
                entity.discard();
        });
        ServerTickEvents.END_SERVER_TICK.register(WorldGenerator::tickServer);
        ServerTickEvents.END_SERVER_TICK.register(net.krodark.asterion.fluid.HeavyWaterFatigue::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(net.krodark.asterion.fluid.HeavyWaterFatigue::clear);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            net.krodark.asterion.worldgen.CatacombArena.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(ResolveSystem::tick);
        ServerTickEvents.END_SERVER_TICK.register(net.krodark.asterion.zipline.ZiplineSystem::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken,
                                                         damageTaken, blocked) ->
                ResolveSystem.recordAttack(entity, source, damageTaken));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            QueenBeetleEntity.recordBeetleKill(entity, source);
            if (entity instanceof net.minecraft.server.level.ServerPlayer player)
                WorldGenerator.prepareRapidRespawn(player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            handler.getPlayer().awardRecipes(server.getRecipeManager().getRecipes());
            net.krodark.asterion.effect.SingedScars.get(server).apply(handler.getPlayer());
            WorldGenerator.playerConnected(handler.getPlayer());
            QueenBeetleEntity.syncActiveQuest(handler.getPlayer());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            net.krodark.asterion.effect.SingedScars.get(newPlayer.level().getServer()).apply(newPlayer);
            net.krodark.asterion.fluid.HeavyWaterFatigue.reset(newPlayer);
            if (oldPlayer.level().dimension().equals(ASTERION_LEVEL)) {
                BlockPos deathPosition = oldPlayer.blockPosition().immutable();
                net.krodark.asterion.worldgen.AuthoredCatacombs.resetCursedBrazierAfterDeath(
                        (net.minecraft.server.level.ServerLevel)oldPlayer.level(), deathPosition);
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
            if (maze != null) {
                WorldGenerator.initializeMazeTerrain(maze);
                WorldGenerator.prepareBossArenaBeforePlayers(maze);
                if (!AsterionWorldState.get(maze).minotaurDefeated())
                    net.krodark.asterion.worldgen.BossArenaEncounter.initialize(maze);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ResolveSystem.clear());
        LOGGER.info("Asterion loaded");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static SoundEvent registerSound(String name) {
        Identifier identifier = id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, identifier,
                SoundEvent.createVariableRangeEvent(identifier));
    }

    private static net.krodark.asterion.block.GreekFireTorchBlock torch(String name,boolean wall,
            net.krodark.asterion.block.GreekFireTorchBlock.FireColor color) {
        return (net.krodark.asterion.block.GreekFireTorchBlock)registerBlock(name,
                color==net.krodark.asterion.block.GreekFireTorchBlock.FireColor.RED?MapColor.COLOR_RED:MapColor.COLOR_ORANGE,
                properties -> new net.krodark.asterion.block.GreekFireTorchBlock(properties.noOcclusion()
                        .strength(.4F).sound(SoundType.METAL)
                        .lightLevel(state -> state.getValue(net.krodark.asterion.block.GreekFireTorchBlock.LIT)
                                &&(wall||state.getValue(net.krodark.asterion.block.GreekFireTorchBlock.TOP))?14:0),wall,color));
    }

    private static Block registerBlock(String name, MapColor color,
                                       java.util.function.Function<BlockBehaviour.Properties, Block> factory) {
        return registerBlock(name, color, factory, java.util.function.UnaryOperator.identity());
    }

    private static Item registerSimpleItem(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    private static Item registerForgedComponentItem(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new net.krodark.asterion.item.ForgedComponentItem(new Item.Properties().setId(key)));
    }

    private static Item registerPurityMetalItem(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new net.krodark.asterion.item.PurityMetalItem(new Item.Properties().setId(key)));
    }

    private static Item registerForgedSwordItem(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new net.krodark.asterion.item.ForgedSwordItem(new Item.Properties().setId(key).stacksTo(1).durability(1)));
    }

    private static ItemStack forgePart(Item item, int metal, String part) {
        String material = net.krodark.asterion.block.CrucibleBlockEntity.metalId(metal);
        String display = java.util.Arrays.stream(material.split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
        int[] hardness = {8, 4, 2, 12, 9, 11, 13, 7, 3};
        int[] edge = {9, 5, 3, 13, 10, 12, 14, 8, 4};
        int[] conductivity = {3, 10, 9, 2, 8, 2, 11, 15, 12};
        int[] weight = {8, 7, 10, 11, 8, 9, 7, 8, 10};
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(display + " " + part));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(java.util.List.of(), java.util.List.of(),
                        java.util.List.of(material, "none", "none", "none"),
                        java.util.List.of(0xFFFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x00FFFFFF)));
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.putString("metal_sequence", Integer.toString(metal));
        data.putString("alloy", display); data.putInt("purity", 100);
        data.putInt("hardness", hardness[metal]); data.putInt("edge", edge[metal]);
        data.putInt("conductivity", conductivity[metal]); data.putInt("weight", weight[metal]);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(data));
        return stack;
    }

    private static Block registerBlock(String name, MapColor color,
                                       java.util.function.Function<BlockBehaviour.Properties, Block> factory,
                                       java.util.function.UnaryOperator<Item.Properties> itemProperties) {
        Block block = registerBlockWithoutItem(name, color, factory);
        Identifier identifier = id(name);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, identifier);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, itemProperties.apply(new Item.Properties().setId(itemKey))
                        .useBlockDescriptionPrefix()));
        return block;
    }

    private static Block registerBlockWithoutItem(String name, MapColor color,
                                                  java.util.function.Function<BlockBehaviour.Properties, Block> factory) {
        Identifier identifier = id(name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, identifier);
        return Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(
                BlockBehaviour.Properties.of().setId(blockKey).mapColor(color)
                        .strength(3.5f, 8.0f).sound(SoundType.DEEPSLATE)));
    }

    private static Item[] registerRuneTablets() {
        Item[] tablets = new Item[GreekRune.values().length];
        for (int index = 0; index < tablets.length; index++) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id("runestone" + (index + 1)));
            tablets[index] = Registry.register(BuiltInRegistries.ITEM, key,
                    new Item(new Item.Properties().setId(key)));
        }
        return tablets;
    }

    /** Full decorative cubes, kept separate from the interactive Greek rune puzzle pieces. */
    private static Block[] registerRuneStoneBlocks() {
        Block[] blocks = new Block[24];
        for (int index = 0; index < blocks.length; index++) {
            blocks[index] = registerBlock("rune_" + (char)('a' + index), MapColor.TERRACOTTA_BROWN, Block::new);
        }
        return blocks;
    }

    private static RuneBlock[] registerRuneBlocks() {
        RuneBlock[] runes = new RuneBlock[GreekRune.values().length];
        for (int index = 0; index < runes.length; index++) {
            final int runeIndex = index;
            String name = "rune_" + (index + 1);
            runes[index] = (RuneBlock)registerBlockWithoutItem(name, MapColor.COLOR_BROWN,
                    properties -> new RuneBlock(runeIndex, properties.noOcclusion().noLootTable()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
            Registry.register(BuiltInRegistries.ITEM, itemKey, new RuneBlockItem(runes[index], runeIndex,
                    new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        }
        return runes;
    }
}
