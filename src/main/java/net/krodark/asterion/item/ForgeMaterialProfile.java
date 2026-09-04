package net.krodark.asterion.item;

/** One authoritative progression table shared by pouring, creative previews and assembly. */
public final class ForgeMaterialProfile {
    private ForgeMaterialProfile() { }

    public static int hardness(int metal) { return value(metal, 9, 6, 4, 15, 17, 24, 20, 18, 5); }
    public static int edge(int metal) { return value(metal, 10, 7, 5, 16, 18, 25, 22, 20, 6); }
    public static int conductivity(int metal) { return value(metal, 3, 13, 10, 2, 11, 4, 14, 18, 12); }
    public static int weight(int metal) { return value(metal, 8, 7, 10, 12, 8, 7, 6, 7, 10); }
    public static int damage(int metal) { return value(metal, 10, 7, 5, 17, 20, 28, 24, 22, 6); }
    public static int speed(int metal) { return value(metal, 8, 11, 7, 5, 14, 16, 18, 20, 10); }
    public static int durability(int metal) { return value(metal, 10, 6, 4, 18, 21, 30, 25, 22, 5); }

    public static String id(int metal) {
        return switch (metal) {
            case 0 -> "iron"; case 1 -> "copper"; case 2 -> "tarnished_gold";
            case 3 -> "netherite"; case 4 -> "celestial_bronze"; case 5 -> "bone_steel";
            case 6 -> "celestial_steel"; case 7 -> "celestial_gold"; case 8 -> "gold";
            default -> "none";
        };
    }

    public static String trait(int metal) {
        return switch (metal) {
            case 0 -> "Reliable"; case 1 -> "Conductive"; case 2 -> "Weathered";
            case 3 -> "Nether-tempered"; case 4 -> "Radiant"; case 5 -> "Apex Bonesteel";
            case 6 -> "Star-tempered"; case 7 -> "Solar-swift"; case 8 -> "Malleable";
            default -> "Composite";
        };
    }

    private static int value(int metal, int iron, int copper, int tarnishedGold, int netherite,
                             int celestialBronze, int bonesteel, int celestialSteel,
                             int celestialGold, int gold) {
        return switch (metal) {
            case 0 -> iron; case 1 -> copper; case 2 -> tarnishedGold; case 3 -> netherite;
            case 4 -> celestialBronze; case 5 -> bonesteel; case 6 -> celestialSteel;
            case 7 -> celestialGold; case 8 -> gold; default -> 1;
        };
    }
}
