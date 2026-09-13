package net.krodark.asterion.port.fabric;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.AncientContent;
import net.krodark.asterion.game.ChainLiftContent;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.port.client.AncientSkeletonRenderer;
import net.krodark.asterion.port.client.NoopEntityRenderer;
import net.krodark.asterion.port.client.SimpleGeoEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.world.item.ItemStack;

/**
 * Lightweight 1.21.1 client bootstrap. The old Amnetic render hooks targeted the
 * post-1.21 render-state renderer and cannot safely run on 1.21.1.
 */
public final class AsterionFabricClient implements ClientModInitializer {
    private static LightRenderHandle<PointLightData> heldLight;

    @Override
    public void onInitializeClient() {
        registerEntityRenderers();
        registerParticleProviders();
        ClientTickEvents.END_CLIENT_TICK.register(AsterionFabricClient::tickVeilLight);
    }

    private static void registerEntityRenderers() {
        EntityRendererRegistry.register(AncientContent.SKELETON, AncientSkeletonRenderer::new);
        EntityRendererRegistry.register(Asterion.MINOTAUR, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/minotaur"), Asterion.id("textures/entity/minotaur.png"),
                Asterion.id("entity/minotaur"), 1.1F, 1.0F));
        EntityRendererRegistry.register(Asterion.BOMBARDIER_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/bombadier_beetle"), Asterion.id("textures/entity/bombadier_beetle.png"),
                Asterion.id("entity/bombadier_beetle"), 0.35F, 1.0F));
        EntityRendererRegistry.register(Asterion.RUNE_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/bombadier_beetle"), Asterion.id("textures/entity/bombadier_beetle.png"),
                Asterion.id("entity/bombadier_beetle"), 0.2F, 0.55F));
        EntityRendererRegistry.register(Asterion.CONSTRUCT, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/construct"), Asterion.id("textures/entity/construct.png"),
                Asterion.id("entity/construct"), 0.55F, 1.0F));
        EntityRendererRegistry.register(Asterion.QUEEN_BEETLE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/queen_beetle"), Asterion.id("textures/entity/queen_beetle.png"),
                Asterion.id("entity/queen_beetle"), 1.3F, 1.0F));
        EntityRendererRegistry.register(Asterion.SCARLET_CENTIPEDE, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/centipede"), Asterion.id("textures/entity/centipede.png"),
                Asterion.id("entity/centipede"), 0.8F, 1.0F));
        EntityRendererRegistry.register(GameplayContent.CURSED_BRAZIER, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("entity/cursed_brazier"), Asterion.id("textures/entity/cursed_brazier.png"),
                Asterion.id("entity/cursed_brazier"), 2.35F, 1.0F));
        EntityRendererRegistry.register(ChainLiftContent.LIFT, context -> new SimpleGeoEntityRenderer<>(context,
                Asterion.id("block/chain_lift"), Asterion.id("textures/block/chain_lift.png"),
                Asterion.id("block/chain_lift"), 1.5F, 1.0F));
        EntityRendererRegistry.register(Asterion.MINOTAUR_AXE, NoopEntityRenderer::new);
        EntityRendererRegistry.register(ChainLiftContent.CALL_RUNE, NoopEntityRenderer::new);
    }

    private static void registerParticleProviders() {
        ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
        registry.register(Asterion.GREEK_FIRE, FlameParticle.Provider::new);
        registry.register(Asterion.MINOTAUR_BELCH_FIRE, FlameParticle.Provider::new);
        registry.register(Asterion.FLAMETHROWER_GAS_FIRE, FlameParticle.Provider::new);
        registry.register(Asterion.BOMBARDIER_GAS_FIRE, FlameParticle.Provider::new);
        registry.register(Asterion.BRAZIER_FIRE, FlameParticle.Provider::new);
        registry.register(Asterion.FIREFLY, FlameParticle.Provider::new);
        registry.register(Asterion.HOSTILE_FIREFLY, FlameParticle.Provider::new);
        registry.register(Asterion.BOMBARDIER_STENCH, SmokeParticle.Provider::new);
        registry.register(Asterion.MINOTAUR_BELCH_SMOKE, SmokeParticle.Provider::new);
        registry.register(Asterion.FLAMETHROWER_GAS, SmokeParticle.Provider::new);
        registry.register(Asterion.GREEK_FIRE_SOOT, SmokeParticle.Provider::new);
        registry.register(Asterion.LAMENTER_TEAR, SmokeParticle.Provider::new);
        registry.register(Asterion.DOOR_SMOKE, SmokeParticle.Provider::new);
        registry.register(Asterion.DOOR_DUST, SmokeParticle.Provider::new);
        registry.register(Asterion.FLY, SmokeParticle.Provider::new);
        registry.register(Asterion.ANCIENT_WALL_DUST, SmokeParticle.Provider::new);
        registry.register(Asterion.RUMBLE_SMOKE, SmokeParticle.Provider::new);
    }

    private static void tickVeilLight(Minecraft client) {
        if (client.player == null || client.level == null) {
            clearLight();
            return;
        }

        ItemStack mainHand = client.player.getMainHandItem();
        ItemStack offHand = client.player.getOffhandItem();
        int color = lightColor(mainHand);
        if (color < 0) color = lightColor(offHand);
        if (color < 0) {
            clearLight();
            return;
        }

        if (heldLight == null || !heldLight.isValid()) {
            PointLightData light = new PointLightData()
                    .setRadius(7.5F)
                    .setBrightness(1.1F)
                    .setOcclusionEnabled(true)
                    .setColor(color);
            heldLight = VeilRenderSystem.renderer().getLightRenderer().addLight(light);
        }

        heldLight.getLightData().setColor(color).setPosition(
                client.player.getX(), client.player.getEyeY() - 0.2D, client.player.getZ());
    }

    private static int lightColor(ItemStack stack) {
        if (stack.is(Asterion.GREEK_FIRE_LANTERN.asItem())
                || stack.is(Asterion.GREEK_FIRE_FLOOR_TORCH.asItem())
                || stack.is(Asterion.GREEK_FIRE_WALL_TORCH.asItem())) return 0x38F0A0;
        if (stack.is(Asterion.RED_FIRE_LANTERN.asItem())
                || stack.is(Asterion.RED_FIRE_FLOOR_TORCH.asItem())
                || stack.is(Asterion.RED_FIRE_WALL_TORCH.asItem())) return 0xFF3B24;
        if (stack.is(Asterion.ORANGE_FIRE_FLOOR_TORCH.asItem())
                || stack.is(Asterion.ORANGE_FIRE_WALL_TORCH.asItem())) return 0xFF9A32;
        return -1;
    }

    private static void clearLight() {
        if (heldLight != null) {
            heldLight.free();
            heldLight = null;
        }
    }
}
