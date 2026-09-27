package net.krodark.asterion.worldgen;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.network.ArenaDebrisPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

 
public final class ArenaDebris {
    private static final Map<ServerLevel, List<ArenaDebrisPayload.Fragment>> PENDING = new IdentityHashMap<>();
    private ArenaDebris() { }
    public static void queue(ServerLevel level, Vec3 position, Vec3 velocity) {
        queue(level, position, velocity, 0.55F + level.getRandom().nextFloat() * 0.4F);
    }
    public static void queue(ServerLevel level, Vec3 position, Vec3 velocity, float scale) {
        var batch = PENDING.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (batch.size() < ArenaDebrisPayload.MAX_FRAGMENTS)
            batch.add(new ArenaDebrisPayload.Fragment(position, velocity, scale));
    }
    /** One impact burst; cosmetic fragments use the existing bounded network batch. */
    public static void entranceBurst(ServerLevel level, net.minecraft.core.BlockPos door, Vec3 inward, double height) {
        Vec3 front = Vec3.atBottomCenterOf(door).add(inward.scale(1.8));
        level.sendParticles(net.krodark.asterion.Asterion.DOOR_SMOKE,
                front.x, front.y + height * .45, front.z, 140, 2.4, height * .4, 2.4, .13);
        var chips = new net.minecraft.core.particles.BlockParticleOption(
                net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState());
        level.sendParticles(chips, front.x, front.y + 2, front.z, 100, 2.8, 1.8, 2.8, .22);
        double radius = AuthoredCatacombs.enabled() ? AuthoredCatacombs.ARENA_RADIUS - 5 : 48;
        double roof = AuthoredCatacombs.enabled() ? AuthoredCatacombs.ARENA_BASE_Y + 44 : door.getY() + 22;
        // Stratified rings cover the whole room, instead of clustering around the doorway.
        for (int ring = 0; ring < 3; ring++) for (int sector = 0; sector < 8; sector++) {
            double angle = (sector + .5 * (ring & 1)) * Math.PI / 4;
            double r = radius * (ring + 1) / 3;
            Vec3 origin = new Vec3(.5 + Math.cos(angle) * r, roof, .5 + Math.sin(angle) * r);
            if (!level.getChunkSource().hasChunk(net.minecraft.util.Mth.floor(origin.x) >> 4,
                    net.minecraft.util.Mth.floor(origin.z) >> 4)) continue;
            queue(level, origin, new Vec3(Math.cos(angle) * .06, -.25 - ring * .05, Math.sin(angle) * .06), .45F + ring * .12F);
            level.sendParticles(net.krodark.asterion.Asterion.DOOR_SMOKE,
                    origin.x, origin.y, origin.z, 5, 1.2, .3, 1.2, .025);
        }
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
         
        for (var player : level.players()) if (ServerPlayNetworking.canSend(player, ArenaDebrisPayload.TYPE))
            ServerPlayNetworking.send(player, new ArenaDebrisPayload(List.of(), 0));
    }
    public static void clear() { PENDING.clear(); }
}
