package net.krodark.asterion;

import net.minecraft.util.Mth;

public enum GreekRune {
    ALPHA("Alpha", "Α"),
    BETA("Beta", "Β"),
    GAMMA("Gamma", "Γ"),
    DELTA("Delta", "Δ"),
    EPSILON("Epsilon", "Ε"),
    ZETA("Zeta", "Ζ"),
    ETA("Eta", "Η"),
    THETA("Theta", "Θ"),
    IOTA("Iota", "Ι"),
    KAPPA("Kappa", "Κ"),
    LAMBDA("Lambda", "Λ"),
    MU("Mu", "Μ"),
    NU("Nu", "Ν"),
    XI("Xi", "Ξ"),
    OMICRON("Omicron", "Ο"),
    PI("Pi", "Π"),
    RHO("Rho", "Ρ"),
    SIGMA("Sigma", "Σ"),
    TAU("Tau", "Τ"),
    UPSILON("Upsilon", "Υ"),
    PHI("Phi", "Φ"),
    CHI("Chi", "Χ"),
    PSI("Psi", "Ψ"),
    OMEGA("Omega", "Ω");

    private static final GreekRune[] ORDERED = values();
    private final String displayName;
    private final String glyph;

    GreekRune(String displayName, String glyph) {
        this.displayName = displayName;
        this.glyph = glyph;
    }

    public String displayName() { return displayName; }
    public String glyph() { return glyph; }
    public int layer() { return ordinal(); }

    public static GreekRune forRadius(double x, double z) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        double outerRadius = config.mazeRadiusCells * (double)config.cellSize;
        double radius = Math.max(Math.abs(x), Math.abs(z));
        double inward = 1.0D - Mth.clamp(radius / Math.max(1.0D, outerRadius), 0.0D, 1.0D);
        int layer = Math.min(ORDERED.length - 1, Mth.floor(inward * ORDERED.length));
        return ORDERED[layer];
    }

    public GreekRune previous() {
        return ORDERED[Math.max(0, ordinal() - 1)];
    }

    public GreekRune next() {
        return ORDERED[Math.min(ORDERED.length - 1, ordinal() + 1)];
    }
}
