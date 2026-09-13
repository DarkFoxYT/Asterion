package net.krodark.asterion.port.neoforge;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.AncientContent;
import net.krodark.asterion.game.ChainLiftContent;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.port.client.AncientSkeletonRenderer;
import net.krodark.asterion.port.client.LiftCallRunePortRenderer;
import net.krodark.asterion.port.client.LabyrinthVinePortRenderer;
import net.krodark.asterion.port.client.MinotaurAxePortRenderer;
import net.krodark.asterion.port.client.SimpleGeoEntityRenderer;
import net.krodark.asterion.port.client.SimpleGeoBlockRenderer;
import net.krodark.asterion.port.client.PortClientFeatures;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.renderer.RenderType;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = Asterion.MOD_ID, value = Dist.CLIENT)
public final class AsterionNeoForgeClientModEvents {
    private AsterionNeoForgeClientModEvents() {}

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        PortClientFeatures.initialize();
        registerRenderLayers();
        event.registerEntityRenderer(AncientContent.SKELETON, AncientSkeletonRenderer::new);
        event.registerEntityRenderer(Asterion.MINOTAUR, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/minotaur"), Asterion.id("textures/entity/minotaur.png"),
                Asterion.id("entity/minotaur"), 1.1F, 1.0F));
        event.registerEntityRenderer(Asterion.BOMBARDIER_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/bombadier_beetle"), Asterion.id("textures/entity/bombadier_beetle.png"),
                Asterion.id("entity/bombadier_beetle"), 0.35F, 1.0F));
        event.registerEntityRenderer(Asterion.RUNE_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/bombadier_beetle"), Asterion.id("textures/entity/bombadier_beetle.png"),
                Asterion.id("entity/bombadier_beetle"), 0.2F, 0.55F));
        event.registerEntityRenderer(Asterion.CONSTRUCT, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/construct"), Asterion.id("textures/entity/construct.png"),
                Asterion.id("entity/construct"), 0.55F, 1.0F));
        event.registerEntityRenderer(Asterion.QUEEN_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/queen_beetle"), Asterion.id("textures/entity/queen_beetle.png"),
                Asterion.id("entity/queen_beetle"), 1.3F, 1.0F));
        event.registerEntityRenderer(Asterion.SCARLET_CENTIPEDE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/centipede"), Asterion.id("textures/entity/centipede.png"),
                Asterion.id("entity/centipede"), 0.8F, 1.0F));
        event.registerEntityRenderer(GameplayContent.CURSED_BRAZIER, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/cursed_brazier"), Asterion.id("textures/entity/cursed_brazier.png"),
                Asterion.id("entity/cursed_brazier"), 2.35F, 1.0F));
        event.registerEntityRenderer(ChainLiftContent.LIFT, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("block/chain_lift"), Asterion.id("textures/block/chain_lift.png"),
                Asterion.id("block/chain_lift"), 1.5F, 1.0F));
        event.registerEntityRenderer(Asterion.MINOTAUR_AXE, MinotaurAxePortRenderer::new);
        event.registerEntityRenderer(ChainLiftContent.CALL_RUNE, LiftCallRunePortRenderer::new);

        event.registerBlockEntityRenderer(Asterion.RUNE_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/rune"), rune -> Asterion.id("textures/block/runes/"
                        + (rune.runeIndex() + 1) + ".png"), ignored -> Asterion.id("block/rune")));
        event.registerBlockEntityRenderer(Asterion.PILLAR_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/pillar"), Asterion.id("textures/block/pillar.png"), Asterion.id("block/pillar")));
        event.registerBlockEntityRenderer(Asterion.MINOTAUR_DOOR_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/minotaur_door"), Asterion.id("textures/block/minotaur_door.png"), Asterion.id("block/minotaur_door")));
        event.registerBlockEntityRenderer(Asterion.CURSED_BRAZIER_DOOR_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/cursed_brazier_door"), Asterion.id("textures/block/cursed_brazier_door.png"), Asterion.id("block/cursed_brazier_door")));
        event.registerBlockEntityRenderer(Asterion.BARREL_DOOR_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/barrel_door"), Asterion.id("textures/block/barrel_door.png"), Asterion.id("block/barrel_door")));
        event.registerBlockEntityRenderer(net.krodark.asterion.block.RespawnObelisks.BLOCK_ENTITY,
                context -> new SimpleGeoBlockRenderer<>(entity -> Asterion.id(entity.getBlockState().is(
                                net.krodark.asterion.block.RespawnObelisks.ALTAR) ? "block/respawn_altar" : "block/respawn_obelisk"),
                        entity -> entity.getBlockState().is(net.krodark.asterion.block.RespawnObelisks.ALTAR)
                                ? net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/block/gold_block.png")
                                : Asterion.id("textures/block/respawn_obelisk.png"),
                        ignored -> Asterion.id("block/sanctuary")));
        event.registerBlockEntityRenderer(Asterion.LABYRINTH_VINE_BLOCK_ENTITY,
                context -> new LabyrinthVinePortRenderer());
        event.registerBlockEntityRenderer(AncientContent.TROPHY_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/minotaur_trophy"), Asterion.id("textures/entity/minotaur.png"), null));
        event.registerBlockEntityRenderer(net.krodark.asterion.game.PedestalContent.BLOCK_ENTITY,
                context -> new SimpleGeoBlockRenderer<>(Asterion.id("block/pedestal"),
                        Asterion.id("textures/block/pedestal.png"), null));
        event.registerBlockEntityRenderer(Asterion.CRUCIBLE_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/crucible"), Asterion.id("textures/block/crucible.png"), null));
        event.registerBlockEntityRenderer(Asterion.GREEK_FIRE_TORCH_BLOCK_ENTITY,
                context -> new SimpleGeoBlockRenderer<>(entity -> {
                    var block = (net.krodark.asterion.block.GreekFireTorchBlock) entity.getBlockState().getBlock();
                    return Asterion.id(block.wall ? "block/wall_torch" : "block/floor_torch");
                }, entity -> {
                    var state = entity.getBlockState();
                    var block = (net.krodark.asterion.block.GreekFireTorchBlock) state.getBlock();
                    return Asterion.id("textures/block/" + (state.getValue(net.krodark.asterion.block.GreekFireTorchBlock.LIT)
                            ? block.fireColor.texture : "torch_no_fire") + ".png");
                }, ignored -> Asterion.id("block/greek_fire_torch")));
        event.registerBlockEntityRenderer(Asterion.SKELETON_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/skeleton"), Asterion.id("textures/block/skeleton.png"), Asterion.id("block/skeleton")));
        event.registerBlockEntityRenderer(Asterion.SHATTERED_DEAD_WOOD_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/shattered_dead_wood"), Asterion.id("textures/block/shattered_dead_wood.png"), Asterion.id("block/shattered_dead_wood")));
        event.registerBlockEntityRenderer(Asterion.OMEGA_LOCK_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/omega_lock"), Asterion.id("textures/block/runes/24.png"), Asterion.id("block/omega_lock")));
    }

    private static void registerRenderLayers() {
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), Asterion.ANCIENT_LEAVES,
                Asterion.TAINTED_LEAVES, Asterion.TAINTED_PETALS, Asterion.PASSION_BLOOM,
                Asterion.SHORT_GRASS, Asterion.ANCIENT_MOSS_CARPET, Asterion.MAZESTEEL_BARS,
                Asterion.MAZESTEEL_CHAIN, Asterion.MAZESTEEL_GATE, Asterion.GREEK_BRAZIER,
                Asterion.GREEK_FIRE_LANTERN, Asterion.RED_FIRE_LANTERN,
                Asterion.GREEK_FIRE_FLOOR_TORCH, Asterion.GREEK_FIRE_WALL_TORCH,
                Asterion.RED_FIRE_FLOOR_TORCH, Asterion.RED_FIRE_WALL_TORCH,
                Asterion.ORANGE_FIRE_FLOOR_TORCH, Asterion.ORANGE_FIRE_WALL_TORCH,
                Asterion.LABYRINTH_VINE);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(),
                net.krodark.asterion.fluid.HeavyWater.WATER_BLOCK,
                net.krodark.asterion.fluid.HeavyWater.BLOCK);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(Asterion.GREEK_FIRE, FlameParticle.Provider::new);
        event.registerSpriteSet(Asterion.MINOTAUR_BELCH_FIRE, FlameParticle.Provider::new);
        event.registerSpriteSet(Asterion.FLAMETHROWER_GAS_FIRE, FlameParticle.Provider::new);
        event.registerSpriteSet(Asterion.BOMBARDIER_GAS_FIRE, FlameParticle.Provider::new);
        event.registerSpriteSet(Asterion.BRAZIER_FIRE, FlameParticle.Provider::new);
        event.registerSpriteSet(Asterion.FIREFLY, FlameParticle.Provider::new);
        event.registerSpriteSet(Asterion.HOSTILE_FIREFLY, FlameParticle.Provider::new);
        event.registerSpriteSet(Asterion.BOMBARDIER_STENCH, SmokeParticle.Provider::new);
        event.registerSpriteSet(Asterion.MINOTAUR_BELCH_SMOKE, SmokeParticle.Provider::new);
        event.registerSpriteSet(Asterion.FLAMETHROWER_GAS, SmokeParticle.Provider::new);
        event.registerSpriteSet(Asterion.GREEK_FIRE_SOOT, SmokeParticle.Provider::new);
        event.registerSpriteSet(Asterion.LAMENTER_TEAR, SmokeParticle.Provider::new);
        event.registerSpriteSet(Asterion.DOOR_SMOKE, SmokeParticle.Provider::new);
        event.registerSpriteSet(Asterion.DOOR_DUST, SmokeParticle.Provider::new);
        event.registerSpriteSet(Asterion.FLY, SmokeParticle.Provider::new);
        event.registerSpriteSet(Asterion.ANCIENT_WALL_DUST, SmokeParticle.Provider::new);
        event.registerSpriteSet(Asterion.RUMBLE_SMOKE, SmokeParticle.Provider::new);
    }
}
