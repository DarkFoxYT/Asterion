package net.krodark.asterion.port.forge;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.AncientContent;
import net.krodark.asterion.game.ChainLiftContent;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.port.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge registrations for Asterion's shared GeckoLib renderers and particles. */
@Mod.EventBusSubscriber(modid = Asterion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AsterionForgeClientModEvents {
    private AsterionForgeClientModEvents() {}

    @SubscribeEvent
    public static void setupClient(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerRenderLayers();
            PortClientFeatures.registerMechanismCompass();
            PortClientFeatures.initializeForge();
        });
    }

    private static void registerRenderLayers() {
        var cutout = net.minecraft.client.renderer.RenderType.cutout();
        for (var block : new net.minecraft.world.level.block.Block[] {
                Asterion.ANCIENT_LEAVES, Asterion.TAINTED_LEAVES, Asterion.TAINTED_PETALS,
                Asterion.PASSION_BLOOM, Asterion.SHORT_GRASS, Asterion.ANCIENT_MOSS_CARPET,
                Asterion.MAZESTEEL_BARS, Asterion.MAZESTEEL_CHAIN, Asterion.MAZESTEEL_GATE,
                Asterion.GREEK_BRAZIER, Asterion.GREEK_FIRE_LANTERN, Asterion.RED_FIRE_LANTERN,
                Asterion.GREEK_FIRE_FLOOR_TORCH, Asterion.GREEK_FIRE_WALL_TORCH,
                Asterion.RED_FIRE_FLOOR_TORCH, Asterion.RED_FIRE_WALL_TORCH,
                Asterion.ORANGE_FIRE_FLOOR_TORCH, Asterion.ORANGE_FIRE_WALL_TORCH,
                Asterion.LABYRINTH_VINE
        }) {
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(block, cutout);
        }
        var translucent = net.minecraft.client.renderer.RenderType.translucent();
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                net.krodark.asterion.fluid.HeavyWater.WATER_BLOCK, translucent);
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                net.krodark.asterion.fluid.HeavyWater.BLOCK, translucent);
    }

    @SubscribeEvent
    public static void registerDimensionEffects(net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(Asterion.id("asterion"), PortClientFeatures.dimensionEffects());
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws java.io.IOException {
        net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback.fire((id, format, loaded) ->
                event.registerShader(new net.minecraft.client.renderer.ShaderInstance(
                        event.getResourceProvider(), id, format), loaded));
    }

    @SubscribeEvent
    public static void registerHud(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(Asterion.id("hud"), ForgeLayeredDraw.POST_SLEEP_STACK,
                net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback::fire);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AncientContent.SKELETON, AncientSkeletonRenderer::new);
        event.registerEntityRenderer(Asterion.MINOTAUR, PortMinotaurRenderer::new);
        event.registerEntityRenderer(Asterion.BOMBARDIER_BEETLE, PortBombardierBeetleRenderer::new);
        event.registerEntityRenderer(Asterion.RUNE_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/bombadier_beetle"), Asterion.id("textures/entity/bombadier_beetle.png"),
                Asterion.id("entity/bombadier_beetle"), 0.2F, 0.55F));
        event.registerEntityRenderer(Asterion.CONSTRUCT, PortConstructRenderer::new);
        event.registerEntityRenderer(Asterion.QUEEN_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/queen_beetle"), Asterion.id("textures/entity/queen_beetle.png"),
                Asterion.id("entity/queen_beetle"), 1.3F, 1.0F));
        event.registerEntityRenderer(Asterion.SCARLET_CENTIPEDE, PortScarletCentipedeRenderer::new);
        event.registerEntityRenderer(GameplayContent.CURSED_BRAZIER, PortCursedBrazierRenderer::new);
        event.registerEntityRenderer(ChainLiftContent.LIFT, PortChainLiftRenderer::new);
        event.registerEntityRenderer(Asterion.MINOTAUR_AXE, MinotaurAxePortRenderer::new);
        event.registerEntityRenderer(ChainLiftContent.CALL_RUNE, LiftCallRunePortRenderer::new);

        event.registerBlockEntityRenderer(Asterion.RUNE_BLOCK_ENTITY, context -> new PortRuneRenderer());
        event.registerBlockEntityRenderer(Asterion.PILLAR_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/pillar"), Asterion.id("textures/block/pillar.png"), Asterion.id("block/pillar")));
        event.registerBlockEntityRenderer(Asterion.MINOTAUR_DOOR_BLOCK_ENTITY, context -> new PortDoorRenderers.Minotaur());
        event.registerBlockEntityRenderer(Asterion.CURSED_BRAZIER_DOOR_BLOCK_ENTITY, context -> new PortDoorRenderers.Cursed());
        event.registerBlockEntityRenderer(Asterion.BARREL_DOOR_BLOCK_ENTITY, context -> new PortDoorRenderers.Barrel());
        event.registerBlockEntityRenderer(net.krodark.asterion.block.RespawnObelisks.BLOCK_ENTITY,
                context -> new PortSanctuaryRenderer());
        event.registerBlockEntityRenderer(Asterion.LABYRINTH_VINE_BLOCK_ENTITY, context -> new LabyrinthVinePortRenderer());
        event.registerBlockEntityRenderer(AncientContent.TROPHY_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/minotaur_trophy"), Asterion.id("textures/entity/minotaur.png"), null));
        event.registerBlockEntityRenderer(net.krodark.asterion.game.PedestalContent.BLOCK_ENTITY, PortPedestalRenderer::new);
        event.registerBlockEntityRenderer(Asterion.CRUCIBLE_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/crucible"), Asterion.id("textures/block/crucible.png"), null));
        event.registerBlockEntityRenderer(Asterion.GREEK_FIRE_TORCH_BLOCK_ENTITY,
                context -> new PortGreekFireTorchRenderer());
        event.registerBlockEntityRenderer(Asterion.SKELETON_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/skeleton"), Asterion.id("textures/block/skeleton.png"), Asterion.id("block/skeleton")));
        event.registerBlockEntityRenderer(Asterion.SHATTERED_DEAD_WOOD_BLOCK_ENTITY, context -> new SimpleGeoBlockRenderer<>(
                Asterion.id("block/shattered_dead_wood"), Asterion.id("textures/block/shattered_dead_wood.png"),
                Asterion.id("block/shattered_dead_wood")));
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        register(event, Asterion.GREEK_FIRE, PortParticles.Style.GREEK_FIRE);
        register(event, Asterion.MINOTAUR_BELCH_FIRE, PortParticles.Style.BELCH_FIRE);
        register(event, Asterion.FLAMETHROWER_GAS_FIRE, PortParticles.Style.FLAMETHROWER_FIRE);
        register(event, Asterion.BOMBARDIER_GAS_FIRE, PortParticles.Style.GAS_FIRE);
        register(event, Asterion.BRAZIER_FIRE, PortParticles.Style.BRAZIER_FIRE);
        register(event, Asterion.FIREFLY, PortParticles.Style.FIREFLY);
        register(event, Asterion.HOSTILE_FIREFLY, PortParticles.Style.HOSTILE_FIREFLY);
        register(event, Asterion.BOMBARDIER_STENCH, PortParticles.Style.STENCH);
        register(event, Asterion.MINOTAUR_BELCH_SMOKE, PortParticles.Style.BELCH_SMOKE);
        register(event, Asterion.FLAMETHROWER_GAS, PortParticles.Style.FLAMETHROWER_GAS);
        register(event, Asterion.GREEK_FIRE_SOOT, PortParticles.Style.SOOT);
        register(event, Asterion.LAMENTER_TEAR, PortParticles.Style.TEAR);
        register(event, Asterion.DOOR_SMOKE, PortParticles.Style.DOOR_SMOKE);
        register(event, Asterion.DOOR_DUST, PortParticles.Style.DOOR_DUST);
        register(event, Asterion.FLY, PortParticles.Style.FLY);
        register(event, Asterion.ANCIENT_WALL_DUST, PortParticles.Style.WALL_DUST);
        register(event, Asterion.RUMBLE_SMOKE, PortParticles.Style.RUMBLE);
    }

    private static void register(RegisterParticleProvidersEvent event,
                                 net.minecraft.core.particles.SimpleParticleType type, PortParticles.Style style) {
        event.registerSpriteSet(type, sprites -> PortParticles.provider(sprites, style));
    }
}
