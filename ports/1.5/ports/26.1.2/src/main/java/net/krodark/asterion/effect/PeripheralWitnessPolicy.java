package net.krodark.asterion.effect;

/** Encounter rules independent of the renderer and of the player's frame rate. */
public final class PeripheralWitnessPolicy {
    public static final int MAX_TEST_WITNESSES = 8;
    public static final int MAX_NATURAL_WITNESSES = 3;
    public static final int FOCUS_LIMIT = 16;
    public static final int VISIBLE_LIMIT = 60;
    public static final int LIFETIME = 200;
    private PeripheralWitnessPolicy() {}

    public static boolean onScreen(double x, double y, double depth) {
        return depth > 0 && Math.abs(x) <= 1.15 && Math.abs(y) <= 1.15;
    }

    public static boolean focused(double x, double y, double depth) {
        return onScreen(x, y, depth) && Math.abs(x) < .34 && Math.abs(y) < .40;
    }

    public static double peripheralOpacity(double x, double y, double depth) {
        if (!onScreen(x, y, depth)) return 0;
        return clamp((Math.abs(x) - .80) / .10);
    }

    public static float opacity(int age, int focusTicks, int visibleTicks, boolean persistent) {
        double arrival = clamp(age / 8.0);
        if (persistent) return (float) arrival;
        return (float) (arrival * Math.min(clamp((LIFETIME - age) / 24.0),
                Math.min(clamp((FOCUS_LIMIT - focusTicks) / 8.0), clamp((VISIBLE_LIMIT - visibleTicks) / 16.0))));
    }

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
