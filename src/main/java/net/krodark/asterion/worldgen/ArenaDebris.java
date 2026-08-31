package net.krodark.asterion.worldgen;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.network.ArenaDebrisPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Server chooses launches and block destruction; clients own short-lived visual rigid bodies. */
public final class ArenaDebris {
    private static final Map<ServerLevel, List<ArenaDebrisPayload.Fragment>> PENDING = new IdentityHashMap<>();
    private ArenaDebris() { }
    public static void queue(ServerLevel level, Vec3 position, Vec3 velocity) {
        var batch = PENDING.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (batch.size() < ArenaDebrisPayload.MAX_FRAGMENTS) batch.add(new ArenaDebrisPayload.Fragment(position, velocity));
    }
    public static void flush(MinecraftServer server) {
        for (var entry : PENDING.entrySet()) for (var player : entry.getKey().players()) {
            if (!ServerPlayNetworking.canSend(player, ArenaDebrisPayload.TYPE)) continue;
            var nearby = entry.getValue().stream().filter(f -> f.position().distanceToSqr(player.position()) < 96 * 96).toList();
            if (!nearby.isEmpty()) ServerPlayNetworking.send(player, new ArenaDebrisPayload(nearby, entry.getKey().getRandom().nextLong()));
        }
        PENDING.clear();
    }
    public static void clear(ServerLevel level) {
        PENDING.remove(level);
        // Empty batch is an encounter reset, clearing only arena rubble and door leaves client-side.
        for (var player : level.players()) if (ServerPlayNetworking.canSend(player, ArenaDebrisPayload.TYPE))
            ServerPlayNetworking.send(player, new ArenaDebrisPayload(List.of(), 0));
    }
    public static void clear() { PENDING.clear(); }
}
