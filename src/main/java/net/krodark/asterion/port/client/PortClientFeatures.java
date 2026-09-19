package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.mixin.ItemPropertiesInvoker;
import net.krodark.asterion.network.CrucibleScreenPayload;
import net.krodark.asterion.network.ForgeInsertPayload;
import net.krodark.asterion.network.GatewayPortalPayload;
import net.krodark.asterion.network.BiomeAtmospherePayload;
import net.krodark.asterion.network.MinotaurGlobalSoundPayload;
import net.krodark.asterion.network.EntryOmenPayload;
import net.krodark.asterion.network.MazeZapPayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.DeadSunEventPayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.krodark.asterion.network.ArenaDebrisPayload;
import net.krodark.asterion.network.DoorBreakPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.minecraft.client.renderer.DimensionSpecialEffects;

/** Loader-neutral client features backed by APIs available on both 1.21.1 jars. */
public final class PortClientFeatures {
    private static boolean initialized;

    private PortClientFeatures() {}

    public static DimensionSpecialEffects dimensionEffects() {
        return new DimensionSpecialEffects(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE,
                        false, false) {
                    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float sunHeight) {
                        return Vec3.ZERO;
                    }
                    @Override public boolean isFoggyAt(int x, int z) { return false; }
                };
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        DimensionRenderingRegistry.registerDimensionEffects(Asterion.id("asterion"),
                dimensionEffects());
        // Clouds use a separate world render path in 1.21.1, even for a NONE
        // sky type.  A no-op renderer prevents vanilla clouds leaking into the
        // enclosed Labyrinth without touching clouds in other dimensions.
        DimensionRenderingRegistry.registerCloudRenderer(Asterion.ASTERION_LEVEL, context -> {});

        // Compass predicates are registered per-item in 1.21.1. Extending CompassItem
        // alone does not make a custom item use the vanilla angle property.
        ItemPropertiesInvoker.asterion$register(Asterion.ANTIKYTHERA_MECHANISM,
                ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> {
                    LodestoneTracker tracker = net.krodark.asterion.port.compat.ItemData.get(stack, DataComponents.LODESTONE_TRACKER);
                    return tracker == null ? null : tracker.target().orElse(null);
                }));

        ClientPlayNetworking.registerGlobalReceiver(CrucibleScreenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof PortCrucibleScreen screen
                            && screen.matches(payload.pos())) screen.update(payload);
                    else context.client().setScreen(new PortCrucibleScreen(payload));
                }));
        ClientPlayNetworking.registerGlobalReceiver(GatewayPortalPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortPortalRenderer.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ForgeInsertPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortForgeItemFlights.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BiomeAtmospherePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    PortDimensionEffects.setBiome(payload.biome());
                    PortAudio.setBiome(payload.biome());
                }));
        ClientPlayNetworking.registerGlobalReceiver(MinotaurGlobalSoundPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortAudio.playGlobal(payload)));
        ClientPlayNetworking.registerGlobalReceiver(EntryOmenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().level != null) {
                        Vec3 pos = payload.position();
                        context.client().level.playLocalSound(pos.x, pos.y, pos.z, Asterion.MINOTAUR_ROAR,
                                net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.72F, false);
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(MazeZapPayload.TYPE, (payload, context) ->
                context.client().execute(() -> { PortLightning.receive(payload); PortDeadSunEvents.receiveWardZap(payload); }));
        ClientPlayNetworking.registerGlobalReceiver(DeadSunStrikePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    PortLightning.receive(payload);
                    PortDeadSunEvents.receive(payload);
                }));
        ClientPlayNetworking.registerGlobalReceiver(DeadSunEventPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortDeadSunEvents.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MazeShiftPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortDeadSunEvents.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BossTelegraphPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortBossTelegraphs.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ArenaDebrisPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortPhysicsDebris.spawnArenaDebris(payload)));
        ClientPlayNetworking.registerGlobalReceiver(DoorBreakPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortPhysicsDebris.spawnDoors(payload)));
        ClientPlayNetworking.registerGlobalReceiver(net.krodark.asterion.network.ObjectiveProgressPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortMazeObjectiveOverlay.receiveSharedProgress(payload.stage())));
        PortEmissiveConfig.load();
        net.krodark.asterion.port.client.particle.AnimatedEmissiveParticle.initialize();
        net.krodark.asterion.port.client.particle.AsterionEmissiveParticles.initialize();
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> {
            Minecraft client = Minecraft.getInstance();
            PortPerformanceGovernor.frame(client);
            PortHeldItemLights.renderFrame(client, client.getTimer().getGameTimeDeltaPartialTick(true));
        });
        PortPortalRenderer.initialize();
        PortForgeItemFlights.initialize();
        PortCinematics.initialize();
        PortRagdolls.initialize();
        PortAudio.initialize();
        PortLightning.initialize();
        PortBossTelegraphs.initialize();
        PortDimensionEffects.initialize();
        PortPhysicsDebris.initialize();
        // Register last so all other entity-stage geometry is queued before
        // the shared depth-aware Geo emissive replay.
        PortEmissiveQueue.initialize();
    }

    public static void tick(Minecraft client) {
        PortLight.tickCleanup(client);
        PortHeldItemLights.tick(client);
        PortBrazierLights.tick(client);
        if (client.player != null) {
            PortMinotaurBodyPicking.pick(client,
                    client.getTimer().getGameTimeDeltaPartialTick(true));
        }
        PortPortalRenderer.tick(client);
        PortForgeItemFlights.tick(client);
        PortAtmosphere.tick(client);
        PortCrucibleCamera.tick(client);
        PortCinematics.tick(client);
        PortRagdolls.tick(client);
        PortDimensionEffects.tick(client);
        PortCentipedeInteraction.tick(client);
        PortAudio.tick(client);
        PortLightning.tick(client);
        PortDeadSunEvents.tick(client);
        PortPhysicsDebris.tick(client);
    }
}
