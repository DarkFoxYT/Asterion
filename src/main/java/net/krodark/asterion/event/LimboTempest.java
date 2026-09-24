package net.krodark.asterion.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.network.chat.Component;

/** A shared, time-driven Limbo sea event; clients and server sample the same envelope. */
public final class LimboTempest {
    public static final long CYCLE = 24000;
    public static final long START = 12000;
    public static final long DURATION = 3600;

    private LimboTempest() { }

    public static double strength(double ticks) {
        double phase = Math.floorMod((long)Math.floor(ticks) - START, CYCLE)
                + (ticks - Math.floor(ticks));
        if (phase >= DURATION) return 0;
        return smooth(phase / 320.0) * (1.0 - smooth((phase - DURATION + 480.0) / 480.0));
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var level = server.getLevel(Asterion.LIMBO_LEVEL);
            if (level == null || level.players().isEmpty()) return;
            long phase = Math.floorMod(level.getGameTime() - START, CYCLE);
            if (phase != 0 && phase != DURATION) return;
            Component message = Component.literal(phase == 0
                    ? "The dead sea begins to heave. A tempest is rising."
                    : "The violent water slowly falls quiet.");
            level.players().forEach(player -> player.sendSystemMessage(message));
        });
    }
}
