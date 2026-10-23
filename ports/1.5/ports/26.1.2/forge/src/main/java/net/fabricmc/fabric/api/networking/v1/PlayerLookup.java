package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.minecraftforge.event.entity.player.PlayerEvent;

/** Native viewers for shared entity synchronization; never loads Fabric API. */
public final class PlayerLookup {
    private static final java.util.Map<java.util.UUID, java.util.Set<ServerPlayer>> VIEWERS = new java.util.HashMap<>();
    private PlayerLookup() {}
    public static void initialize() {
        PlayerEvent.StartTracking.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player)
                VIEWERS.computeIfAbsent(event.getTarget().getUUID(), ignored -> new java.util.HashSet<>()).add(player);
        });
        PlayerEvent.StopTracking.BUS.addListener(event -> {
            var viewers = VIEWERS.get(event.getTarget().getUUID());
            if (viewers != null) {
                viewers.remove(event.getEntity());
                if (viewers.isEmpty()) VIEWERS.remove(event.getTarget().getUUID());
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            VIEWERS.values().forEach(viewers -> viewers.remove(event.getEntity()));
            VIEWERS.values().removeIf(java.util.Set::isEmpty);
        });
        net.minecraftforge.event.entity.EntityLeaveLevelEvent.BUS.addListener(event -> {
            if (!event.getLevel().isClientSide()) VIEWERS.remove(event.getEntity().getUUID());
        });
        net.minecraftforge.event.server.ServerStoppedEvent.BUS.addListener(event -> VIEWERS.clear());
    }
    public static java.util.Collection<ServerPlayer> tracking(Entity entity) {
        return java.util.Collections.unmodifiableSet(VIEWERS.getOrDefault(entity.getUUID(), java.util.Set.of()));
    }
}
