package net.krodark.asterion.port.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Connection and entity ownership callbacks on the native NeoForge bus. */
public final class NativeLifecycle {
    private static final java.util.Map<java.util.UUID, java.util.Set<ServerPlayer>> TRACKING =
            new java.util.HashMap<>();

    public static java.util.Collection<ServerPlayer> tracking(
            net.minecraft.world.entity.Entity entity) {
        return java.util.Collections.unmodifiableSet(
                TRACKING.getOrDefault(entity.getUUID(), java.util.Set.of()));
    }

    private NativeLifecycle() {}

    static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.fire(
                                event.getDispatcher(),
                                event.getBuildContext(),
                                event.getCommandSelection()));
        bus.addListener(
                (PlayerEvent.PlayerLoggedInEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player)
                        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.fireJoin(
                                player.connection, player.getServer());
                });
        bus.addListener(
                (PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.cancelRespawn(player);
                        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
                                .fireDisconnect(player.connection, player.getServer());
                        TRACKING.values().forEach(viewers -> viewers.remove(player));
                        TRACKING.values().removeIf(java.util.Set::isEmpty);
                    }
                });
        bus.addListener(
                (PlayerEvent.StartTracking event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        TRACKING.computeIfAbsent(
                                        event.getTarget().getUUID(),
                                        ignored -> new java.util.HashSet<>())
                                .add(player);
                        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.fireStart(
                                event.getTarget(), player);
                    }
                });
        bus.addListener(
                (PlayerEvent.StopTracking event) -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        var viewers = TRACKING.get(event.getTarget().getUUID());
                        if (viewers != null) {
                            viewers.remove(player);
                            if (viewers.isEmpty()) TRACKING.remove(event.getTarget().getUUID());
                        }
                        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.fireStop(
                                event.getTarget(), player);
                    }
                });
        bus.addListener(
                (net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) -> {
                    if (!event.getLevel().isClientSide())
                        TRACKING.remove(event.getEntity().getUUID());
                });
        bus.addListener(
                (net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
                    TRACKING.clear();
                    net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.clearRespawns();
                });
        // Clone fires before the replacement player is positioned and connected.
        // Fire the recovery callback from the completed respawn event instead.
        bus.addListener(
                (PlayerEvent.Clone event) -> {
                    if (event.getOriginal() instanceof ServerPlayer oldPlayer
                            && event.getEntity() instanceof ServerPlayer newPlayer)
                        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.rememberRespawn(
                                oldPlayer, newPlayer, !event.isWasDeath());
                });
        bus.addListener(
                (PlayerEvent.PlayerRespawnEvent event) -> {
                    if (event.getEntity() instanceof ServerPlayer player)
                        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.completeRespawn(player);
                });
    }
}
