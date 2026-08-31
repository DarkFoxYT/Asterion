package net.krodark.asterion.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.krodark.asterion.client.light.HeldItemDynamicLights;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import net.krodark.asterion.client.lightning.MazeZapRenderer;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.client.ragdoll.PhysicsDebrisSystem;
import net.krodark.asterion.client.ragdoll.RagdollClientController;
import net.krodark.asterion.client.render.entity.MinotaurGeoRenderer;
import net.krodark.asterion.client.render.entity.BombadierBeetleGeoRenderer;
import net.krodark.asterion.client.render.entity.ScarletCentipedeGeoRenderer;
import net.krodark.asterion.client.particle.BombardierStenchParticle;
import net.krodark.asterion.client.particle.BombardierGasFireParticle;
import net.krodark.asterion.client.particle.GreekFireParticle;
import net.krodark.asterion.client.particle.AnimatedEmissiveParticle;
import net.krodark.asterion.client.particle.AsterionEmissiveParticles;
import net.krodark.asterion.client.particle.FlyingInsectParticle;
import net.krodark.asterion.client.particle.AncientWallDustParticle;
import net.krodark.asterion.client.particle.RumbleSmokeParticle;
import net.krodark.asterion.client.particle.HostileFireflyParticle;
import net.krodark.asterion.client.render.block.RuneGeoRenderer;
import net.krodark.asterion.client.render.block.LabyrinthVineGeoRenderer;
import net.krodark.asterion.client.render.block.SkeletonGeoRenderer;
import net.krodark.asterion.client.render.block.ShatteredDeadWoodGeoRenderer;
import net.krodark.asterion.client.render.portal.AsterionPortalRenderer;
import net.krodark.asterion.client.render.post.AsterionPostEffects;
import net.krodark.asterion.network.*;
import net.krodark.asterion.network.ragdoll.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

public final class AsterionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AsterionEmissiveConfig.load();
        AsterionEmissiveParticles.initialize();
        AnimatedEmissiveParticle.initialize();
        AsterionPostEffects.register();
        AsterionPortalRenderer.register();
        DimensionTransitionOverlay.register();
        BossFinaleOverlay.register();
        BossEntranceCinematic.register();
        MazeObjectiveOverlay.register();
        MazeZapRenderer.register();
        DazeOverlay.register();
        RagdollGetUpOverlay.register();
        RagdollClientController.initialize();
        CentipedeInteractionClient.initialize();
        EntityRenderers.register(Asterion.MINOTAUR, MinotaurGeoRenderer::new);
        EntityRenderers.register(Asterion.MINOTAUR_AXE, net.krodark.asterion.client.render.entity.MinotaurAxeRenderer::new);
        EntityRenderers.register(Asterion.BOMBARDIER_BEETLE, BombadierBeetleGeoRenderer::new);
        EntityRenderers.register(Asterion.SCARLET_CENTIPEDE, ScarletCentipedeGeoRenderer::new);
        ParticleProviderRegistry.getInstance().register(Asterion.GREEK_FIRE, sprites ->
                (type, level, x, y, z, vx, vy, vz, random) ->
                        GreekFireParticle.create(level, x, y, z, vx, vy, vz, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.GREEK_FIRE_SOOT, sprites ->
                (type, level, x, y, z, vx, vy, vz, random) ->
                        net.krodark.asterion.client.particle.DoorSmokeParticle.soot(level, x, y, z, vx, vy, vz, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.BRAZIER_FIRE, sprites ->
                (type, level, x, y, z, vx, vy, vz, random) ->
                        GreekFireParticle.createBrazier(level, x, y, z, vx, vy, vz, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.LAMENTER_TEAR, sprites ->
                (type, level, x, y, z, vx, vy, vz, random) ->
                        new net.krodark.asterion.client.particle.LamenterTearParticle(level, x, y, z, vx, vz, sprites));
        ParticleProviderRegistry.getInstance().register(Asterion.DOOR_SMOKE, sprites ->
                (type, level, x, y, z, vx, vy, vz, random) ->
                        new net.krodark.asterion.client.particle.DoorSmokeParticle(level, x, y, z, vx, vy, vz, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.DOOR_DUST, sprites ->
                (type, level, x, y, z, vx, vy, vz, random) ->
                        new net.krodark.asterion.client.particle.DoorDustParticle(level, x, y, z, vx, vy, vz, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.BOMBARDIER_STENCH, sprites ->
                (type, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
                        BombardierStenchParticle.create(level, x, y, z,
                                velocityX, velocityY, velocityZ, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.BOMBARDIER_GAS_FIRE, sprites ->
                (type, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
                        BombardierGasFireParticle.create(level, x, y, z,
                                velocityX, velocityY, velocityZ, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.FLY, sprites ->
                (type, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
                        FlyingInsectParticle.createFly(level, x, y, z,
                                velocityX, velocityY, velocityZ, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.FIREFLY, sprites ->
                (type, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
                        FlyingInsectParticle.createFirefly(level, x, y, z,
                                velocityX, velocityY, velocityZ, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.HOSTILE_FIREFLY, sprites ->
                (type, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
                        HostileFireflyParticle.create(level, x, y, z,
                                velocityX, velocityY, velocityZ, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.ANCIENT_WALL_DUST, sprites ->
                (type, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
                        AncientWallDustParticle.create(level, x, y, z,
                                velocityX, velocityY, velocityZ, sprites, random));
        ParticleProviderRegistry.getInstance().register(Asterion.RUMBLE_SMOKE, sprites ->
                (type, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
                        RumbleSmokeParticle.create(level, x, y, z,
                                velocityX, velocityY, velocityZ, sprites, random));
        BlockEntityRenderers.register(Asterion.RUNE_BLOCK_ENTITY, RuneGeoRenderer::new);
        BlockEntityRenderers.register(Asterion.MINOTAUR_DOOR_BLOCK_ENTITY,
                net.krodark.asterion.client.render.block.MinotaurDoorRenderer::new);
        BlockEntityRenderers.register(Asterion.BARREL_DOOR_BLOCK_ENTITY,
                net.krodark.asterion.client.render.block.BarrelDoorRenderer::new);
        BlockEntityRenderers.register(net.krodark.asterion.block.RespawnObelisks.BLOCK_ENTITY,
                net.krodark.asterion.client.render.block.SanctuaryRenderer::new);
        BlockEntityRenderers.register(Asterion.LABYRINTH_VINE_BLOCK_ENTITY, LabyrinthVineGeoRenderer::new);
        BlockEntityRenderers.register(Asterion.SKELETON_BLOCK_ENTITY, SkeletonGeoRenderer::new);
        BlockEntityRenderers.register(Asterion.SHATTERED_DEAD_WOOD_BLOCK_ENTITY,
                ShatteredDeadWoodGeoRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(DimensionTransitionPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        DimensionTransitionOverlay.begin(payload.fadeInTicks(), payload.holdTicks())));
        ClientPlayNetworking.registerGlobalReceiver(EntryOmenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().getSoundManager().play(
                        SimpleSoundInstance.forUI(Asterion.MINOTAUR_ROAR, 0.72F, 4.0F))));
        ClientPlayNetworking.registerGlobalReceiver(BossFinalePayload.TYPE, (payload, context) ->
                context.client().execute(BossFinaleOverlay::begin));
        ClientPlayNetworking.registerGlobalReceiver(BossEntrancePayload.TYPE, (payload, context) ->
                context.client().execute(() -> BossEntranceCinematic.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(GatewayPortalPayload.TYPE, (payload, context) ->
                context.client().execute(() -> AsterionPortalRenderer.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MazeZapPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    MazeZapRenderer.receive(payload);
                    DeadSunClientEvents.receiveWardZap(payload);
                    RagdollClientController.suppressAutomaticFallRagdoll(payload.durationTicks() + 80);
                }));
        ClientPlayNetworking.registerGlobalReceiver(DeadSunEventPayload.TYPE, (payload, context) ->
                context.client().execute(() -> DeadSunClientEvents.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MazeShiftPayload.TYPE, (payload, context) ->
                context.client().execute(() -> DeadSunClientEvents.receiveShift(payload)));
        ClientPlayNetworking.registerGlobalReceiver(net.krodark.asterion.network.ArenaDebrisPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PhysicsDebrisSystem.spawnArenaDebris(payload)));
        ClientPlayNetworking.registerGlobalReceiver(DoorBreakPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PhysicsDebrisSystem.spawnDoors(payload)));
        ClientPlayNetworking.registerGlobalReceiver(DeadSunStrikePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    DeadSunClientEvents.receiveStrike(payload);
                    MazeZapRenderer.receiveGroundStrike(payload);
                }));
        ClientPlayNetworking.registerGlobalReceiver(BossTelegraphPayload.TYPE, (payload, context) ->
                context.client().execute(() -> MazeZapRenderer.receiveTelegraph(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BossEncounterResetPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    MazeZapRenderer.clearTransientCombatEffects();
                    MazeObjectiveOverlay.armAfterBossWipe();
                    BossEntranceCinematic.finish(context.client());
                    PhysicsDebrisSystem.clear();
                }));
        ClientPlayNetworking.registerGlobalReceiver(DazePayload.TYPE, (payload, context) ->
                context.client().execute(() -> DazeOverlay.begin(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BiomeAtmospherePayload.TYPE, (payload, context) ->
                context.client().execute(() -> AsterionPostEffects.setBiome(payload.biome())));
        ClientPlayNetworking.registerGlobalReceiver(RagdollImpulsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> DismembermentEngine.INSTANCE.forcePlayerTumble(
                        context.client(), payload.source(), payload.impulse(), payload.force())));
        ClientPlayNetworking.registerGlobalReceiver(RagdollExplosionPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    DismembermentEngine.INSTANCE.applyExplosion(context.client(), payload.center(), payload.radius());
                    PhysicsDebrisSystem.throwDoors(payload.center(), payload.radius());
                }));
        ClientPlayNetworking.registerGlobalReceiver(RagdollAuthorityPayload.TYPE, (payload, context) ->
                context.client().execute(() -> DismembermentEngine.INSTANCE.reconcilePlayerAuthority(
                        context.client(), payload.position(), payload.velocity(), payload.serverTick())));
        ClientPlayNetworking.registerGlobalReceiver(RagdollPosePayload.TYPE, (payload, context) ->
                context.client().execute(() -> DismembermentEngine.INSTANCE.applyRemotePose(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        DimensionTransitionOverlay.tick(client);
        DeadSunEntryCinematic.tick(client);
        BossFinaleOverlay.tick(client);
        BossEntranceCinematic.tick(client);
        CinematicControls.tick(client);
        MazeObjectiveOverlay.tick(client);
        DeadSunClientEvents.tick(client);
        PhysicsDebrisSystem.tick(client);
        DazeOverlay.tick(client);
        HeldItemDynamicLights.tick(client);
        LedAmneticLight.tickCleanup(client);
        AsterionPostEffects.tickBiomeAtmosphere(client);
    }
}
