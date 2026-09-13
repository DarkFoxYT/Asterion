package net.krodark.asterion.port.neoforge;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.AncientContent;
import net.krodark.asterion.game.ChainLiftContent;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.port.client.AncientSkeletonRenderer;
import net.krodark.asterion.port.client.NoopEntityRenderer;
import net.krodark.asterion.port.client.SimpleGeoEntityRenderer;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.SmokeParticle;
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
        event.registerEntityRenderer(Asterion.MINOTAUR_AXE, NoopEntityRenderer::new);
        event.registerEntityRenderer(ChainLiftContent.CALL_RUNE, NoopEntityRenderer::new);
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
