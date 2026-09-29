import net.krodark.asterion.util.LightingPolicy;

/** No game launch needed: verifies lighting decisions across settings and dimension changes. */
public final class LightingPolicySmoke {
    private static void equal(float actual, float expected, String label) {
        if (Float.floatToIntBits(actual) != Float.floatToIntBits(expected))
            throw new AssertionError(label + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        for (int quality = 0; quality <= 3; quality++) {
            for (boolean requested : new boolean[]{false, true}) {
                if (LightingPolicy.sceneBloom(false, requested, quality))
                    throw new AssertionError("Ordinary worlds or disconnects enabled scene bloom");
            }
            if (LightingPolicy.sceneBloom(true, false, quality))
                throw new AssertionError("Default settings bloom ordinary terrain");
            if (LightingPolicy.sceneBloom(true, true, quality) != (quality > 1))
                throw new AssertionError("Explicit scene bloom/low-quality preference lost");
        }
        // Out-of-range vanilla values from other mods must also pass through unchanged.
        for (float vanilla : new float[]{0, .25F, .5F, 1, 5, Float.NaN}) {
            for (int configured : new int[]{-1, 0, 25, 50, 75, 100, 200}) {
                equal(LightingPolicy.brightness(false, configured, vanilla), vanilla,
                        "Overworld/Nether/End/other mod dimension");
                equal(LightingPolicy.brightness(true, -1, vanilla), vanilla, "Vanilla preference");
            }
        }
        equal(LightingPolicy.brightness(true, 0, .75F), 0, "Moody inside Asterion");
        equal(LightingPolicy.brightness(true, 25, .75F), .25F, "Custom inside Limbo");
        equal(LightingPolicy.brightness(true, 200, .75F), 1, "Upper bound");
        // Enter, leave, disconnect and join a different world; no prior decision may leak.
        for (boolean inside : new boolean[]{false, true, false, true, false, false}) {
            equal(LightingPolicy.brightness(inside, 100, .25F), inside ? 1 : .25F, "Transition");
            if (LightingPolicy.sceneBloom(inside, true, 3) != inside)
                throw new AssertionError("Scene bloom leaked across a world change");
        }
        System.out.println("PASS default/opt-in bloom at every quality, vanilla brightness, dimension changes and disconnects");
    }
}
