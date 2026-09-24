package net.krodark.asterion.event;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.WandererEntity;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/** A single server-owned moving front. Side corridors are outside its narrow collision lane. */
public final class DeadStampede {
    private static final Map<ServerLevel, Wave> WAVES = new WeakHashMap<>();
    private static final int GATHER_TICKS = 100;
    private static final int RUN_TICKS = 240;
    private record Wave(double startZ, long started) { }

    private DeadStampede() { }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerLevel level = server.getLevel(Asterion.LIMBO_LEVEL);
            if (level == null) return;
            Wave wave = WAVES.get(level);
            if (wave == null && level.getGameTime() % 8000 == 0) {
                for (ServerPlayer player : level.players()) {
                    double z = player.getZ();
                    if (z > UnderworldTerrain.SPAWN_Z + 70 && z < -80
                            && Math.abs(player.getX() - (UnderworldTerrain.riverCenter(z) - 15)) < 7) {
                        wave = new Wave(z - 45, level.getGameTime());
                        WAVES.put(level, wave);
                        spawnRunners(level, wave.startZ);
                        for (ServerPlayer listener : level.players())
                            listener.sendSystemMessage(Component.literal("A roar of footsteps gathers behind you."));
                        break;
                    }
                }
            }
            if (wave == null) return;
            int age = (int)(level.getGameTime() - wave.started);
            if (age >= GATHER_TICKS + RUN_TICKS) { WAVES.remove(level); return; }
            if (age < GATHER_TICKS) return;
            double frontZ = wave.startZ + (age - GATHER_TICKS) * .20;
            double centerX = UnderworldTerrain.riverCenter(frontZ) - 15;
            AABB crush = new AABB(centerX - 4, UnderworldTerrain.WATER_Y - 2,
                    frontZ - 1.3, centerX + 4, UnderworldTerrain.WATER_Y + 11, frontZ + 1.3);
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && !player.isCreative() && crush.intersects(player.getBoundingBox()))
                    player.hurtServer(level, level.damageSources().cramming(), 8F);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var dead = Commands.literal("dead")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("stampede").then(Commands.literal("start").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            if (!level.dimension().equals(Asterion.LIMBO_LEVEL)) return 0;
                            double z = ctx.getSource().getPosition().z - 45;
                            WAVES.put(level, new Wave(z, level.getGameTime()));
                            spawnRunners(level, z);
                            ctx.getSource().sendSuccess(() -> Component.literal("The dead are gathering. The wave arrives in five seconds."), true);
                            return 1;
                        })).then(Commands.literal("stop").executes(ctx -> {
                            WAVES.remove(ctx.getSource().getLevel());
                            ctx.getSource().sendSuccess(() -> Component.literal("Stopped the dead stampede."), true);
                            return 1;
                        })));
            var states = Commands.literal("state");
            for (WandererEntity.State state : WandererEntity.State.values()) {
                if (state == WandererEntity.State.STAMPEDE) continue;
                states.then(Commands.literal(state.name().toLowerCase()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = ctx.getSource().getLevel();
                    WandererEntity closest = level.getEntitiesOfClass(WandererEntity.class,
                                    player.getBoundingBox().inflate(32)).stream()
                            .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                    if (closest == null) return 0;
                    closest.debugState(state, level, player);
                    ctx.getSource().sendSuccess(() -> Component.literal("Nearest dead: " + state.name()), false);
                    return 1;
                }));
            }
            dead.then(states);
            dispatcher.register(Commands.literal("asterion").then(dead));
        });
    }

    public static double front(ServerLevel level) {
        Wave wave = WAVES.get(level);
        if (wave == null) return Double.NaN;
        long age = level.getGameTime() - wave.started;
        return wave.startZ + Math.max(0, age - GATHER_TICKS) * .20;
    }

    private static void spawnRunners(ServerLevel level, double frontZ) {
        double laneX = UnderworldTerrain.riverCenter(frontZ) - 15;
        if (level.getEntitiesOfClass(WandererEntity.class,
                new AABB(laneX - 12, UnderworldTerrain.MIN_Y, frontZ - 8,
                        laneX + 12, UnderworldTerrain.MAX_Y, frontZ + 12)).size() >= 8) return;
        for (int i = 0; i < 6; i++) {
            int z = (int)Math.floor(frontZ) - (i / 3) * 3;
            int x = (int)Math.round(UnderworldTerrain.riverCenter(z) - 15) + i % 3 - 1;
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
            var feet = LimboWanderers.findFloor(level, x, z,
                    UnderworldTerrain.WATER_Y + 15, UnderworldTerrain.WATER_Y - 5);
            if (feet == null) continue;
            var runner = Asterion.WANDERER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (runner == null) break;
            runner.setPos(x + .5, feet.getY(), z + .5);
            if (level.noCollision(runner)) level.addFreshEntity(runner);
        }
    }

    public static boolean gathering(ServerLevel level) {
        Wave wave = WAVES.get(level);
        return wave != null && level.getGameTime() - wave.started < GATHER_TICKS;
    }
}
