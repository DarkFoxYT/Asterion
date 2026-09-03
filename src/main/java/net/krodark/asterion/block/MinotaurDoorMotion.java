package net.krodark.asterion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Authored hinge/emitter coordinates, shared by rendering, dust and detached leaves. */
public final class MinotaurDoorMotion {
    public static final int WIDTH = 7, HEIGHT = 5, OPEN_TICKS = 72, BREAK_TICK = 112;
    public static final float OPEN_ANGLE = (float)Math.toRadians(100);
    public static final float BREAK_ANGLE = (float)Math.toRadians(58);
    private static final int[] IMPACTS = {14, 44, 78};
    private MinotaurDoorMotion() { }

    public static float ease(float t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static float breachAngle(float tick) {
        if (tick >= 96) return BREAK_ANGLE * ease((tick - 96) / 16);
        float angle = 0;
        for (int impact : IMPACTS) {
            float t = tick - impact;
            if (t >= 0 && t < 16)
                angle += (float)(Math.sin(Math.min(1, t / 3) * Math.PI / 2)
                        * Math.exp(-Math.max(0, t - 3) / 4) * Math.toRadians(9 + impact / 9));
        }
        return angle;
    }

    public static float yaw(Direction facing) {
        return switch (facing) {
            case SOUTH -> (float)Math.PI;
            case WEST -> (float)Math.PI / 2;
            case EAST -> -(float)Math.PI / 2;
            default -> 0;
        };
    }

    public static Vec3 toWorld(BlockPos root, Direction facing, Vec3 local) {
        Direction right = facing.getClockWise();
        return new Vec3(root.getX() + .5 + right.getStepX() * local.x - facing.getStepX() * local.z,
                root.getY() + local.y,
                root.getZ() + .5 + right.getStepZ() * local.x - facing.getStepZ() * local.z);
    }

    public static Vec3 leafPoint(int side, float angle, double x, double y) {
        // Bedrock model X is mirrored by GeckoLib: rightdoor hinge -48 becomes +3 blocks.
        double hinge = side * 3.0, dx = side * x - hinge, rotation = side * angle;
        return new Vec3(hinge + dx * Math.cos(rotation), y, -dx * Math.sin(rotation));
    }

    public static Vec3 emitter(BlockPos root, Direction facing, int side, float angle) {
        return toWorld(root, facing, leafPoint(side, angle, 5.0 / 16.0, 2.0 / 16.0));
    }

    public static Vec3 leafCenter(BlockPos root, Direction facing, int side, float angle) {
        return toWorld(root, facing, leafPoint(side, angle, 1.75, 2.5));
    }
}
