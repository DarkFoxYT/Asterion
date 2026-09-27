package net.krodark.asterion.entity;

 
public enum MinotaurRemains {
    LEFT_ARM(4, 3), RIGHT_ARM(4, 3), LEFT_LEG(6, 4), RIGHT_LEG(6, 4), TORSO(8, 5), HEAD(0, 0);

    public static final int LIMBS = 15;
    public static final int BODY = 31;
    public static final int ALL = 63;
    public final int minimumBones, boneVariation;
    MinotaurRemains(int minimumBones, int boneVariation) {
        this.minimumBones = minimumBones;
        this.boneVariation = boneVariation;
    }
    public int bit() { return 1 << ordinal(); }
    public boolean removed(int mask) { return (mask & bit()) != 0; }
    public static MinotaurRemains next(int mask) {
        for (var part : values()) if (!part.removed(mask)) return part;
        return null;
    }
    public static MinotaurRemains fromId(int id) { return id >= 0 && id < values().length ? values()[id] : null; }

     
    public static MinotaurRemains root(String name) {
        return switch (name) {
            case "leftshoulder" -> LEFT_ARM;
            case "rightshoulder" -> RIGHT_ARM;
            case "leftleg" -> LEFT_LEG;
            case "rightleg" -> RIGHT_LEG;
            case "head" -> HEAD;
            case "lowerbody", "full" -> TORSO;
            default -> null;
        };
    }
}
