package net.krodark.asterion.physics;

/** Optional physics integration without loading third-party classes on either side. */
public final class PhysicsCompatibility {
    private static final boolean SABLE = PhysicsCompatibility.class.getClassLoader()
            .getResource("dev/ryanhcode/sable/SableCommonEvents.class") != null;
    private PhysicsCompatibility() {}
    /** Avoid competing player collision solvers; normal damage and knockback remain. */
    public static boolean externalPlayerPhysics() { return SABLE; }
}
