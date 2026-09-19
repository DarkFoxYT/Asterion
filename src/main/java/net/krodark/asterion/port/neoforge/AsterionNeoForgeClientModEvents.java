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
    public static void dimensionEffects(net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(Asterion.id("asterion"), PortClientFeatures.dimensionEffects());
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        PortClientFeatures.initialize();
        registerRenderLayers();
        event.registerEntityRenderer(AncientContent.SKELETON, AncientSkeletonRenderer::new);
        event.registerEntityRenderer(Asterion.MINOTAUR,
                net.krodark.asterion.port.client.PortMinotaurRenderer::new);
        event.registerEntityRenderer(Asterion.BOMBARDIER_BEETLE,
                net.krodark.asterion.port.client.PortBombardierBeetleRenderer::new);
        event.registerEntityRenderer(Asterion.RUNE_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/bombadier_beetle"), Asterion.id("textures/entity/bombadier_beetle.png"),
                Asterion.id("entity/bombadier_beetle"), 0.2F, 0.55F));
        event.registerEntityRenderer(Asterion.CONSTRUCT,
                net.krodark.asterion.port.client.PortConstructRenderer::new);
        event.registerEntityRenderer(Asterion.QUEEN_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/queen_beetle"), Asterion.id("textures/entity/queen_beetle.png"),
                Asterion.id("entity/queen_beetle"), 1.3F, 1.0F));
        event.registerEntityRenderer(Asterion.SCARLET_CENTIPEDE,
                net.krodark.asterion.port.client.PortScarletCentipedeRenderer::new);
        event.registerEntityRenderer(GameplayContent.CURSED_BRAZIER,
                net.krodark.asterion.port.client.PortCursedBrazierRenderer::new);
        event.registerEntityRenderer(ChainLiftContent.LIFT,
                net.krodark.asterion.port.client.PortChainLiftRenderer::new);
        event.registerEntityRenderer(Asterion.MINOTAUR_AXE, MinotaurAxePortRenderer::new);
        event.registerEntityRenderer(ChainLiftContent.CALL_RUNE, LiftCallRunePortRenderer::new);

        event.registerBlockEntityRenderer(Asterion.RUNE_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortRuneRenderer());
        event.registerBlockEntityRenderer(Asterion.PILLAR_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/pillar"), Asterion.id("textures/block/pillar.png"), Asterion.id("block/pillar")));
        event.registerBlockEntityRenderer(Asterion.MINOTAUR_DOOR_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortDoorRenderers.Minotaur());
        event.registerBlockEntityRenderer(Asterion.CURSED_BRAZIER_DOOR_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortDoorRenderers.Cursed());
        event.registerBlockEntityRenderer(Asterion.BARREL_DOOR_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortDoorRenderers.Barrel());
        event.registerBlockEntityRenderer(net.krodark.asterion.block.RespawnObelisks.BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortSanctuaryRenderer());
        event.registerBlockEntityRenderer(Asterion.LABYRINTH_VINE_BLOCK_ENTITY,
                context -> new LabyrinthVinePortRenderer());
        event.registerBlockEntityRenderer(AncientContent.TROPHY_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/minotaur_trophy"), Asterion.id("textures/entity/minotaur.png"), null));
        event.registerBlockEntityRenderer(net.krodark.asterion.game.PedestalContent.BLOCK_ENTITY,
                net.krodark.asterion.port.client.PortPedestalRenderer::new);
        event.registerBlockEntityRenderer(Asterion.CRUCIBLE_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/crucible"), Asterion.id("textures/block/crucible.png"), null));
        event.registerBlockEntityRenderer(Asterion.GREEK_FIRE_TORCH_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortGreekFireTorchRenderer());
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
        register(event, Asterion.GREEK_FIRE, net.krodark.asterion.port.client.PortParticles.Style.GREEK_FIRE);
        register(event, Asterion.MINOTAUR_BELCH_FIRE, net.krodark.asterion.port.client.PortParticles.Style.BELCH_FIRE);
        register(event, Asterion.FLAMETHROWER_GAS_FIRE, net.krodark.asterion.port.client.PortParticles.Style.FLAMETHROWER_FIRE);
        register(event, Asterion.BOMBARDIER_GAS_FIRE, net.krodark.asterion.port.client.PortParticles.Style.GAS_FIRE);
        register(event, Asterion.BRAZIER_FIRE, net.krodark.asterion.port.client.PortParticles.Style.BRAZIER_FIRE);
        register(event, Asterion.FIREFLY, net.krodark.asterion.port.client.PortParticles.Style.FIREFLY);
        register(event, Asterion.HOSTILE_FIREFLY, net.krodark.asterion.port.client.PortParticles.Style.HOSTILE_FIREFLY);
        register(event, Asterion.BOMBARDIER_STENCH, net.krodark.asterion.port.client.PortParticles.Style.STENCH);
        register(event, Asterion.MINOTAUR_BELCH_SMOKE, net.krodark.asterion.port.client.PortParticles.Style.BELCH_SMOKE);
        register(event, Asterion.FLAMETHROWER_GAS, net.krodark.asterion.port.client.PortParticles.Style.FLAMETHROWER_GAS);
        register(event, Asterion.GREEK_FIRE_SOOT, net.krodark.asterion.port.client.PortParticles.Style.SOOT);
        register(event, Asterion.LAMENTER_TEAR, net.krodark.asterion.port.client.PortParticles.Style.TEAR);
        register(event, Asterion.DOOR_SMOKE, net.krodark.asterion.port.client.PortParticles.Style.DOOR_SMOKE);
        register(event, Asterion.DOOR_DUST, net.krodark.asterion.port.client.PortParticles.Style.DOOR_DUST);
        register(event, Asterion.FLY, net.krodark.asterion.port.client.PortParticles.Style.FLY);
        register(event, Asterion.ANCIENT_WALL_DUST, net.krodark.asterion.port.client.PortParticles.Style.WALL_DUST);
        register(event, Asterion.RUMBLE_SMOKE, net.krodark.asterion.port.client.PortParticles.Style.RUMBLE);
    }

    private static void register(RegisterParticleProvidersEvent event, net.minecraft.core.particles.SimpleParticleType type,
                                 net.krodark.asterion.port.client.PortParticles.Style style) {
        event.registerSpriteSet(type, sprites -> net.krodark.asterion.port.client.PortParticles.provider(sprites, style));
    }
}
