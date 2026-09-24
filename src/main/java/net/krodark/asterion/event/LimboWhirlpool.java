package net.krodark.asterion.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.network.chat.Component;

/** Periodic, world-space whirlpool in the open Limbo sea. */
public final class LimboWhirlpool {
    public static final double X = 14, Z = 350, RADIUS = 58;
    public static final long CYCLE = 32000, START = 20000, DURATION = 6000;
    private LimboWhirlpool() { }

    public static double strength(double ticks) {
        long manual = LimboSeaCommands.whirlpoolStart();
        if (manual == LimboSeaCommands.STOPPED) return 0;
        double phase = manual >= 0 ? ticks - manual
                : Math.floorMod((long)Math.floor(ticks) - START, CYCLE) + ticks - Math.floor(ticks);
        if (phase < 0) return 0;
        if (phase >= DURATION) return 0;
        return smooth(phase / 800) * (1 - smooth((phase - DURATION + 1000) / 1000));
    }

    public static double funnel(double x, double z, double ticks) {
        double r = Math.hypot(x - X, z - Z);
        if (r >= RADIUS) return 0;
        double edge = 1 - smooth((r - 12) / (RADIUS - 12));
        return -7 * strength(ticks) * Math.exp(-r / 23) * edge;
    }

    public static double pull(double x, double z, double ticks) {
        double r = Math.hypot(x - X, z - Z);
        return r >= RADIUS || r < 2 ? 0 : strength(ticks) * (1 - smooth(r / RADIUS));
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
            Component message = Component.literal(phase == 0
                    ? "The dead sea starts to turn. A vast whirlpool is forming."
                    : "The whirlpool loosens its grip and sinks away.");
            level.players().forEach(player -> player.sendSystemMessage(message));
        });
    }
}
