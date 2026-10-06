package net.krodark.asterion.update.underworld.world;

/** Deterministic buoyancy/free-fall step shared by gameplay and regression checks. */
public final class FerryHeave {
    public record Step(double height, double speed, boolean airborne, double impact) { }
    private FerryHeave() { }
    public static Step advance(double height, double speed, boolean airborne, double target) {
        double error = target-height;
        if (!airborne && error < -.65 && speed > -.18) airborne = true;
        if (airborne) {
            speed = Math.max(-.72,speed-.042);
            double next = height+speed;
            if (next <= target) return new Step(target,.08,false,Math.min(1,Math.abs(speed)/.55));
            return new Step(next,speed,true,0);
        }
        double restoring = error*(error<0?.17:.11);
        double damping = speed*(speed<0?.22:.36);
        speed = Math.clamp(speed+restoring-damping,-.38,.27);
        return new Step(height+speed,speed,false,0);
    }
}
