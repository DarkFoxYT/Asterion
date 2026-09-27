package net.krodark.asterion.client;

/** Optional Flashback integration: use the replay/export clock, never wall time. */
public final class FlashbackAnimationClock {
    private static final java.lang.reflect.Method IN_REPLAY = probe("isInReplay");
    private static final java.lang.reflect.Method VISUAL_MILLIS = probe("getVisualMillis");
    private FlashbackAnimationClock() { }
    private static java.lang.reflect.Method probe(String name) {
        try {
            return Class.forName("com.moulberry.flashback.Flashback", false,
                    FlashbackAnimationClock.class.getClassLoader()).getMethod(name);
        } catch (ReflectiveOperationException | LinkageError unavailable) { return null; }
    }
    public static double tick() {
        if (IN_REPLAY == null || VISUAL_MILLIS == null) return Double.NaN;
        try {
            return Boolean.TRUE.equals(IN_REPLAY.invoke(null))
                    ? ((Number)VISUAL_MILLIS.invoke(null)).doubleValue() / 50.0 : Double.NaN;
        } catch (ReflectiveOperationException | LinkageError unavailable) { return Double.NaN; }
    }
}
