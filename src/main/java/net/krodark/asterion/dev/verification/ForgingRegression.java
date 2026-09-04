package net.krodark.asterion.dev.verification;

import net.krodark.asterion.item.ForgeMaterialProfile;

/** Pure material-table contracts, kept outside Minecraft startup for fast build validation. */
public final class ForgingRegression {
    private static int checks;

    public static void main(String[] args) {
        for (int metal = 0; metal <= 8; metal++) {
            require(!ForgeMaterialProfile.id(metal).equals("none"), "Missing material id " + metal);
            require(ForgeMaterialProfile.hardness(metal) > 0, "Missing hardness " + metal);
            require(ForgeMaterialProfile.edge(metal) > 0, "Missing edge " + metal);
            require(ForgeMaterialProfile.damage(metal) > 0, "Missing damage " + metal);
            require(ForgeMaterialProfile.speed(metal) > 0, "Missing speed " + metal);
            require(ForgeMaterialProfile.durability(metal) > 0, "Missing durability " + metal);
            require(!ForgeMaterialProfile.trait(metal).equals("Composite"), "Missing trait " + metal);
        }
        for (int celestial : new int[]{4, 6, 7}) {
            require(ForgeMaterialProfile.damage(celestial) > ForgeMaterialProfile.damage(0),
                    "Celestial damage must exceed iron: " + celestial);
            require(ForgeMaterialProfile.speed(celestial) > ForgeMaterialProfile.speed(0),
                    "Celestial speed must exceed iron: " + celestial);
            require(ForgeMaterialProfile.durability(celestial) > ForgeMaterialProfile.durability(0),
                    "Celestial durability must exceed iron: " + celestial);
        }
        for (int metal = 0; metal <= 8; metal++) if (metal != 5) {
            require(ForgeMaterialProfile.damage(5) > ForgeMaterialProfile.damage(metal),
                    "Bonesteel is not strongest by damage");
            require(ForgeMaterialProfile.durability(5) > ForgeMaterialProfile.durability(metal),
                    "Bonesteel is not strongest by durability");
        }
        System.out.println("Forging regression: " + checks + " checks passed");
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
