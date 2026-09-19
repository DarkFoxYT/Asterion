package net.krodark.asterion.port.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.AncientContent;
import net.krodark.asterion.game.ChainLiftContent;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.port.client.AncientSkeletonRenderer;
import net.krodark.asterion.port.client.LiftCallRunePortRenderer;
import net.krodark.asterion.port.client.LabyrinthVinePortRenderer;
import net.krodark.asterion.port.client.MinotaurAxePortRenderer;
import net.krodark.asterion.port.client.SimpleGeoEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.krodark.asterion.port.client.PortClientFeatures;
import net.krodark.asterion.port.client.SimpleGeoBlockRenderer;

/**
 * Client bootstrap using the version-matched Amnetic lighting API.
 */
public final class AsterionFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PortClientFeatures.initialize();
        registerEntityRenderers();
        registerBlockEntityRenderers();
        registerRenderLayers();
        registerParticleProviders();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PortClientFeatures.tick(client);
        });
    }

    private static void registerBlockEntityRenderers() {
        BlockEntityRenderers.register(Asterion.RUNE_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortRuneRenderer());
        BlockEntityRenderers.register(Asterion.PILLAR_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/pillar"), Asterion.id("textures/block/pillar.png"), Asterion.id("block/pillar")));
        BlockEntityRenderers.register(Asterion.MINOTAUR_DOOR_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortDoorRenderers.Minotaur());
        BlockEntityRenderers.register(Asterion.CURSED_BRAZIER_DOOR_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortDoorRenderers.Cursed());
        BlockEntityRenderers.register(Asterion.BARREL_DOOR_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortDoorRenderers.Barrel());
        BlockEntityRenderers.register(net.krodark.asterion.block.RespawnObelisks.BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortSanctuaryRenderer());
        BlockEntityRenderers.register(Asterion.LABYRINTH_VINE_BLOCK_ENTITY,
                context -> new LabyrinthVinePortRenderer());
        BlockEntityRenderers.register(AncientContent.TROPHY_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/minotaur_trophy"), Asterion.id("textures/entity/minotaur.png"), null));
        BlockEntityRenderers.register(net.krodark.asterion.game.PedestalContent.BLOCK_ENTITY,
                net.krodark.asterion.port.client.PortPedestalRenderer::new);
        BlockEntityRenderers.register(Asterion.CRUCIBLE_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/crucible"), Asterion.id("textures/block/crucible.png"), null));
        BlockEntityRenderers.register(Asterion.GREEK_FIRE_TORCH_BLOCK_ENTITY,
                context -> new net.krodark.asterion.port.client.PortGreekFireTorchRenderer());
        BlockEntityRenderers.register(Asterion.SKELETON_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/skeleton"), Asterion.id("textures/block/skeleton.png"), Asterion.id("block/skeleton")));
        BlockEntityRenderers.register(Asterion.SHATTERED_DEAD_WOOD_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/shattered_dead_wood"), Asterion.id("textures/block/shattered_dead_wood.png"), Asterion.id("block/shattered_dead_wood")));
        BlockEntityRenderers.register(Asterion.OMEGA_LOCK_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
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

    private static void registerEntityRenderers() {
        EntityRendererRegistry.register(AncientContent.SKELETON, AncientSkeletonRenderer::new);
        EntityRendererRegistry.register(Asterion.MINOTAUR,
                net.krodark.asterion.port.client.PortMinotaurRenderer::new);
        EntityRendererRegistry.register(Asterion.BOMBARDIER_BEETLE,
                net.krodark.asterion.port.client.PortBombardierBeetleRenderer::new);
        EntityRendererRegistry.register(Asterion.RUNE_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/bombadier_beetle"), Asterion.id("textures/entity/bombadier_beetle.png"),
                Asterion.id("entity/bombadier_beetle"), 0.2F, 0.55F));
        EntityRendererRegistry.register(Asterion.CONSTRUCT,
                net.krodark.asterion.port.client.PortConstructRenderer::new);
        EntityRendererRegistry.register(Asterion.QUEEN_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/queen_beetle"), Asterion.id("textures/entity/queen_beetle.png"),
                Asterion.id("entity/queen_beetle"), 1.3F, 1.0F));
        EntityRendererRegistry.register(Asterion.SCARLET_CENTIPEDE,
                net.krodark.asterion.port.client.PortScarletCentipedeRenderer::new);
        EntityRendererRegistry.register(GameplayContent.CURSED_BRAZIER,
                net.krodark.asterion.port.client.PortCursedBrazierRenderer::new);
        EntityRendererRegistry.register(ChainLiftContent.LIFT,
                net.krodark.asterion.port.client.PortChainLiftRenderer::new);
        EntityRendererRegistry.register(Asterion.MINOTAUR_AXE, MinotaurAxePortRenderer::new);
        EntityRendererRegistry.register(ChainLiftContent.CALL_RUNE, LiftCallRunePortRenderer::new);
    }

    private static void registerParticleProviders() {
        ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
        register(registry, Asterion.GREEK_FIRE, net.krodark.asterion.port.client.PortParticles.Style.GREEK_FIRE);
        register(registry, Asterion.MINOTAUR_BELCH_FIRE, net.krodark.asterion.port.client.PortParticles.Style.BELCH_FIRE);
        register(registry, Asterion.FLAMETHROWER_GAS_FIRE, net.krodark.asterion.port.client.PortParticles.Style.FLAMETHROWER_FIRE);
        register(registry, Asterion.BOMBARDIER_GAS_FIRE, net.krodark.asterion.port.client.PortParticles.Style.GAS_FIRE);
        register(registry, Asterion.BRAZIER_FIRE, net.krodark.asterion.port.client.PortParticles.Style.BRAZIER_FIRE);
        register(registry, Asterion.FIREFLY, net.krodark.asterion.port.client.PortParticles.Style.FIREFLY);
        register(registry, Asterion.HOSTILE_FIREFLY, net.krodark.asterion.port.client.PortParticles.Style.HOSTILE_FIREFLY);
        register(registry, Asterion.BOMBARDIER_STENCH, net.krodark.asterion.port.client.PortParticles.Style.STENCH);
        register(registry, Asterion.MINOTAUR_BELCH_SMOKE, net.krodark.asterion.port.client.PortParticles.Style.BELCH_SMOKE);
        register(registry, Asterion.FLAMETHROWER_GAS, net.krodark.asterion.port.client.PortParticles.Style.FLAMETHROWER_GAS);
        register(registry, Asterion.GREEK_FIRE_SOOT, net.krodark.asterion.port.client.PortParticles.Style.SOOT);
        register(registry, Asterion.LAMENTER_TEAR, net.krodark.asterion.port.client.PortParticles.Style.TEAR);
        register(registry, Asterion.DOOR_SMOKE, net.krodark.asterion.port.client.PortParticles.Style.DOOR_SMOKE);
        register(registry, Asterion.DOOR_DUST, net.krodark.asterion.port.client.PortParticles.Style.DOOR_DUST);
        register(registry, Asterion.FLY, net.krodark.asterion.port.client.PortParticles.Style.FLY);
        register(registry, Asterion.ANCIENT_WALL_DUST, net.krodark.asterion.port.client.PortParticles.Style.WALL_DUST);
        register(registry, Asterion.RUMBLE_SMOKE, net.krodark.asterion.port.client.PortParticles.Style.RUMBLE);
    }

    private static void register(ParticleFactoryRegistry registry, net.minecraft.core.particles.SimpleParticleType type,
                                 net.krodark.asterion.port.client.PortParticles.Style style) {
        registry.register(type, sprites -> net.krodark.asterion.port.client.PortParticles.provider(sprites, style));
    }

}
