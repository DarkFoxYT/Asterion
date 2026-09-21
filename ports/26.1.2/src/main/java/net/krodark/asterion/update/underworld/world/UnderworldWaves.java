package net.krodark.asterion.update.underworld.world;

/** CPU counterpart of limbo_water.vsh: noise-modulated Stokes waves and horizontal crest compression. */
public final class UnderworldWaves {
    public static final double CHOPPINESS = .9;
    public record Sample(double height, double dx, double dz, double curvature) { }
    private record Noise(double value, double dx, double dz) { }
    private static final double[] kx = {.065, -.088, .039, -.19};
    private static final double[] kz = {.042, .052, -.148, -.083};
    private static final double[] amplitude = {1.35, .95, .60, .28};
    private static final double[] offset = {.20, 1.8, 3.1, .7};
    private static final double[] warp = {8.0, -9.0, 7.0, -8.0};
    private UnderworldWaves() { }

    public static double height(double x, double z, double ticks) {
        // Invert the horizontal deformation so buoyancy samples the displayed world position.
        double qx = x, qz = z;
        for (int i = 0; i < 4; i++) {
            Sample s = sample(qx, qz, ticks);
            qx = x - s.dx * CHOPPINESS; qz = z - s.dz * CHOPPINESS;
        }
        return sample(qx, qz, ticks).height;
    }

    public static Sample sample(double x, double z, double ticks) {
        double seaZ = z;
        // Stretch the horizontal footprint by 1/.65 without increasing wave amplitude.
        x *= .65; z *= .65;
        ticks *= .42;
        Noise phaseA = noise(x * .055 + ticks * .0009, z * .055 - ticks * .0006);
        Noise phaseB = noise(x * .055 - ticks * .0007 + 71, z * .055 + ticks * .0008 - 43);
        Noise phaseC = noise(x * .055 + ticks * .0003 + 137, z * .055 - ticks * .0005 + 89);
        Noise phaseD = new Noise((phaseA.value+phaseB.value)*.5,(phaseA.dx+phaseB.dx)*.5,(phaseA.dz+phaseB.dz)*.5);
        Noise energy = noise(x * .011 - ticks * .00035 + 31, z * .011 + ticks * .0005 + 19);
        double t = Math.clamp((seaZ - 55) / 125, 0, 1);
        double exposure = .12 + .88 * t * t * (3 - 2 * t);
        double group = .55 + .45 * energy.value;
        double h = 0, dx = 0, dz = 0, curvature = 0;
        for (int i = 0; i < 4; i++) {
            Noise phase = i==0?phaseA:i==1?phaseB:i==2?phaseC:phaseD;
            Noise packet = i==0?phaseB:i==1?phaseC:i==2?phaseA:phaseB;
            double k = Math.hypot(kx[i], kz[i]), a = amplitude[i]*(.8+.4*packet.value);
            double angle = kx[i] * x + kz[i] * z - Math.sqrt(9.81 * k) * ticks / 20 + offset[i]
                    + (phase.value - .5) * warp[i];
            double c = Math.cos(angle), s = Math.sin(angle), c2 = 2 * c * c - 1, s2 = 2 * s * c;
            double slope = -a * (s + k * a * s2);
            h += a * (c + .5 * k * a * c2);
            double amplitudeSlope=amplitude[i]*.4*.055*(c+k*a*c2);
            dx += slope * (kx[i] + phase.dx * .055 * warp[i])+amplitudeSlope*packet.dx;
            dz += slope * (kz[i] + phase.dz * .055 * warp[i])+amplitudeSlope*packet.dz;
            curvature += a * k * k * (c + 2 * k * a * c2);
        }
        double ex = exposure * .45 * energy.dx * .011 * .65;
        double ez = exposure * .45 * energy.dz * .011 * .65 + group * .88 * 6 * t * (1 - t) / 125;
        double height=h*exposure*group, limiter=1;
        if(Math.abs(height)>1.8) {
            double bend=Math.tanh((Math.abs(height)-1.8)/.7);
            height=Math.copySign(1.8+.7*bend,height);limiter=1-bend*bend;
        }
        return new Sample(height, (dx * exposure * group * .65 + h * ex)*limiter,
                (dz * exposure * group * .65 + h * ez)*limiter, curvature * exposure * group * .4225*limiter);
    }

    private static double hash(int x, int z) {
        int h = x * 0x1f123bb5 ^ z * 0x5f356495;
        h ^= h >>> 16; h *= 0x45d9f3b; h ^= h >>> 16;
        return (h & 65535) / 65535.0;
    }
    private static Noise noise(double x, double z) {
        int ix = (int)Math.floor(x), iz = (int)Math.floor(z);
        double fx = x - ix, fz = z - iz;
        double ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        return new Noise(a + (b-a)*ux + (c-a)*uz + (a-b-c+d)*ux*uz,
                ((b-a)*(1-uz)+(d-c)*uz)*6*fx*(1-fx),
                ((c-a)*(1-ux)+(d-b)*ux)*6*fz*(1-fz));
    }
}
