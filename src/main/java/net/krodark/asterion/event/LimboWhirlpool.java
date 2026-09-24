package net.krodark.asterion.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.network.chat.Component;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;

/** Periodic, world-space whirlpool in the open Limbo sea. */
public final class LimboWhirlpool {
    public static final double X = 16, Z = 352, RADIUS = 120;
    public static final long CYCLE = 48000, START = 20000, DURATION = 28000;
    private static int centerX = 16, centerZ = 352;
    private LimboWhirlpool() { }
    public static int x() { return centerX; }
    public static int z() { return centerZ; }
    public static void setCenter(int x, int z) {
        centerX = Math.clamp(Math.round(x / 4F) * 4, -400, 400);
        centerZ = Math.clamp(Math.round(z / 4F) * 4, 100, 900);
    }
    public static void placeAhead(net.minecraft.server.level.ServerLevel level) {
        var ferry = level.getEntity(CharonsFerryEntity.SHARED_ID);
        if (ferry instanceof CharonsFerryEntity boat) {
            double yaw = Math.toRadians(boat.getYRot());
            double targetZ = Math.max(110, boat.getZ() + Math.max(45, Math.cos(yaw) * 78));
            double targetX = boat.getX() - Math.sin(yaw) * 78;
            setCenter((int)Math.round(targetX), (int)Math.round(targetZ));
        } else setCenter((int)Math.round(UnderworldTerrain.riverCenter(170)), 170);
    }

    public static double strength(double ticks) {
        long manual = LimboSeaCommands.whirlpoolStart();
        if (manual == LimboSeaCommands.STOPPED) return 0;
        double phase = manual >= 0 ? ticks - manual
                : Math.floorMod((long)Math.floor(ticks) - START, CYCLE) + ticks - Math.floor(ticks);
        if (phase < 0) return 0;
        if (manual >= 0) return smooth(phase / 1200);
        if (phase >= DURATION) return 0;
        return smooth(phase / 1200) * (1 - smooth((phase - DURATION + 1600) / 1600));
    }

    public static double funnel(double x, double z, double ticks) {
        double r = Math.hypot(x - centerX, z - centerZ);
        if (r >= RADIUS) return 0;
        double edge = 1 - smooth((r - 24) / (RADIUS - 24));
        double core = 1 - smooth(r / 24);
        return strength(ticks) * (-14 * Math.exp(-r / 55) * edge - 4 * core * core);
    }

    public static double pull(double x, double z, double ticks) {
        double r = Math.hypot(x - centerX, z - centerZ);
        return r >= RADIUS ? 0 : strength(ticks) * (1 - smooth(r / RADIUS));
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var level = server.getLevel(Asterion.LIMBO_LEVEL);
            if (level == null || level.players().isEmpty()) return;
            if (LimboSeaCommands.whirlpoolStart() != LimboSeaCommands.NATURAL) return;
            long phase = Math.floorMod(level.getGameTime() - START, CYCLE);
            if (phase != 0 && phase != DURATION) return;
            if (phase == 0) { placeAhead(level); LimboSeaCommands.sync(level); }
            Component message = Component.literal(phase == 0
                    ? "The dead sea starts to turn. A vast whirlpool is forming."
                    : "The whirlpool loosens its grip and sinks away.");
            level.players().forEach(player -> player.sendSystemMessage(message));
        });
    }
}
