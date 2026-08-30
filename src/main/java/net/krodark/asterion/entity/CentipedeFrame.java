package net.krodark.asterion.entity;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Coordinate contract shared by the physics, seats and GeckoLib adapter. */
public final class CentipedeFrame {
    public static final float MODEL_SCALE = 2.0F;
    public static final double LINK_LENGTH = 1.875D;
    public static final double HALF_WIDTH = 0.83D;
    public static final double HALF_HEIGHT = 0.63D;
    public static final double HALF_LENGTH = 0.95D;
    public static final double CLEARANCE = HALF_HEIGHT + 0.025D;
    public static final Vec3 DOWN = new Vec3(0, -1, 0);
    private CentipedeFrame() {}

    public static Vec3 unit(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() > 1.0E-10 ? value.normalize() : fallback;
    }

    public static Vec3 tangent(Vec3 direction, Vec3 normal, Vec3 previous) {
        Vec3 result = direction.subtract(normal.scale(direction.dot(normal)));
        if (result.lengthSqr() < 1.0E-8)
            result = previous.subtract(normal.scale(previous.dot(normal)));
        if (result.lengthSqr() < 1.0E-8) {
            Vec3 axis = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(0, 0, -1);
            result = axis.subtract(normal.scale(axis.dot(normal)));
        }
        return result.normalize();
    }

    /** Heading may turn, but +Y is ALWAYS the surface's outward normal (belly is -Y). */
    public static Quaternionf rotation(Vec3 inwardNormal, Vec3 forward) {
        Vec3 normal = unit(inwardNormal, DOWN);
        Vec3 up = normal.scale(-1);
        Vec3 facing = tangent(forward, normal, new Vec3(0, 0, -1));
        Vec3 right = facing.cross(up).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f()
                .setColumn(0, vector(right)).setColumn(1, vector(up))
                .setColumn(2, vector(facing.scale(-1)))).normalize();
    }

    /** BoneSnapshot.translate mirrors X; ignoring this mirrors the trail around the head. */
    public static Vector3f boneTranslation(Vec3 worldOffset) {
        float units = 16.0F / MODEL_SCALE;
        return new Vector3f((float)-worldOffset.x * units,
                (float)worldOffset.y * units, (float)worldOffset.z * units);
    }

    /** RenderUtil/BoneSnapshot rotate Z, then Y, then X -- not XYZ. */
    public static Vector3f boneAngles(Quaternionf rotation) {
        Matrix3f m = new Matrix3f().set(rotation);
        double cosine = Math.hypot(m.m00(), m.m01());
        float y = (float)Math.atan2(-m.m02(), cosine);
        if (cosine < 1.0E-5) {
            // At +/-90 degrees X and Z are coupled. Choose X=0 and preserve their combined turn.
            return new Vector3f(0, y, (float)Math.atan2(-m.m10(), m.m11()));
        }
        return new Vector3f((float)Math.atan2(m.m12(), m.m22()), y,
                (float)Math.atan2(m.m01(), m.m00()));
    }

    public static Vec3 extents(Vec3 normal, Vec3 forward) {
        Vec3 up = normal.scale(-1);
        Vec3 facing = tangent(forward, normal, new Vec3(0, 0, -1));
        Vec3 right = facing.cross(up).normalize();
        return new Vec3(Math.abs(right.x) * HALF_WIDTH + Math.abs(up.x) * HALF_HEIGHT + Math.abs(facing.x) * HALF_LENGTH,
                Math.abs(right.y) * HALF_WIDTH + Math.abs(up.y) * HALF_HEIGHT + Math.abs(facing.y) * HALF_LENGTH,
                Math.abs(right.z) * HALF_WIDTH + Math.abs(up.z) * HALF_HEIGHT + Math.abs(facing.z) * HALF_LENGTH);
    }

    private static Vector3f vector(Vec3 value) {
        return new Vector3f((float)value.x, (float)value.y, (float)value.z);
    }
}
