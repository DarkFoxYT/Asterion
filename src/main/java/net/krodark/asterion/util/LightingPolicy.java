package net.krodark.asterion.util;

/** Scene-wide lighting is opt-in and confined to the mod's dimensions. */
public final class LightingPolicy {
    private LightingPolicy() { }

    public static boolean sceneBloom(boolean insideAsterion, boolean requested, int quality) {
        return insideAsterion && requested && quality > 1;
    }

    public static float brightness(boolean insideAsterion, int configuredPercent, float vanilla) {
        if (!insideAsterion || configuredPercent < 0) return vanilla;
        return Math.max(0, Math.min(100, configuredPercent)) / 100F;
    }
}
