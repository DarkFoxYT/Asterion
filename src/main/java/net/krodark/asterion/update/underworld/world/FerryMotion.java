package net.krodark.asterion.update.underworld.world;

/** Horizontal ferry momentum, with wave-gradient gravity and water drag. */
public final class FerryMotion {
    private FerryMotion() { }

    public static double advance(double speed, int throttle, boolean piloted, boolean sailing, double forwardSlope) {
        if (!piloted && !sailing) return speed * .85;
        double thrust = piloted ? throttle * (throttle < 0 ? .00105 : .00125) : .00050;
        double drag = piloted ? (throttle == 0 ? .992 : .988) : .993;
        double slope = Math.clamp(forwardSlope, -.8, .8);
        double gravity = slope * (slope < 0 ? .0042 : .0026);
        double uphillDrag = Math.max(0, -slope) * .0035;
        return Math.clamp(speed * (drag - uphillDrag) + thrust + gravity, -.11, .25);
    }
}
