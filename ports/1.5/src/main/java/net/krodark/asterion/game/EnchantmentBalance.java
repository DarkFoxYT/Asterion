package net.krodark.asterion.game;

/** Shared balance rules, independent of registry and loader APIs. */
public final class EnchantmentBalance {
    private EnchantmentBalance() { }
    public static double swimMultiplier(int level) { return 1 + .35 * Math.max(0, Math.min(3, level)); }
    public static double heavyPenalty(double original, int level) {
        return original + (1 - original) * Math.max(0, Math.min(3, level)) / 3.0;
    }
    public static float minosBonus(int level, double width, double height, boolean minotaur) {
        level = Math.max(0, Math.min(3, level));
        if (level == 0 || !Double.isFinite(width) || !Double.isFinite(height)) return 0;
        double size = Math.max(0, Math.min(4, (width * height - 1.17) / 1.17));
        if (minotaur) size = 4;
        return (float)(level * (.5 + 1.5 * size));
    }
    public static int curseInterval(int level) { return level >= 2 ? 100 : 200; }
    public static int stolenDurability(int level, int repairNeeded, int donorRemaining) {
        return Math.max(0, Math.min(Math.min(5, level), Math.min(repairNeeded, donorRemaining)));
    }
}
