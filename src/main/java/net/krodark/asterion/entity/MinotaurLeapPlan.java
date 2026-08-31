package net.krodark.asterion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A bounded, terrain-checked arc. Execution uses normal entity movement/collision, never teleportation. */
public record MinotaurLeapPlan(Vec3 start, Vec3 landing, double rise, int ticks) {
    public Vec3 point(double tick) {
        double t = Math.clamp(tick / ticks, 0, 1);
        return start.lerp(landing, t).add(0, 4 * rise * t * (1 - t), 0);
    }

    public static MinotaurLeapPlan find(ServerLevel level, MinotaurEntity boss, Vec3 target) {
        Vec3 start = boss.position();
        double distance = target.subtract(start).horizontalDistance();
        if (distance < 3 || distance > 36 || Math.abs(target.y - start.y) > 10) return null;
        Vec3 towardBoss = start.subtract(target).multiply(1, 0, 1).normalize();
        AABB local = boss.getBoundingBox().move(start.scale(-1)).deflate(.035);
        for (double setback : new double[]{0, 1.5, 3, 4.5}) {
            Vec3 candidate = target.add(towardBoss.scale(setback));
            Vec3 landing = supportedLanding(level, boss, local, candidate);
            if (landing == null || landing.subtract(start).horizontalDistance() < 3) continue;
            int ticks = (int)Math.clamp(Math.ceil(landing.subtract(start).horizontalDistance() / .85), 14, 38);
            for (double rise : new double[]{3.5, 5, 7, 9, 12}) {
                MinotaurLeapPlan plan = new MinotaurLeapPlan(start, landing, rise, ticks);
                Vec3 previous = start;
                boolean clear = true;
                for (int step = 1; step <= ticks * 3; step++) {
                    Vec3 next = plan.point(step / 3.0);
                    if (!clear(level, boss, local.move(previous).expandTowards(next.subtract(previous)))) {
                        clear = false; break;
                    }
                    previous = next;
                }
                if (clear) return plan;
            }
        }
        return null;
    }

    private static Vec3 supportedLanding(ServerLevel level, MinotaurEntity boss, AABB local, Vec3 target) {
        // Search near the target's feet, then progressively lower surfaces. Do not choose a ceiling above them.
        for (int offset = 1; offset >= -9; offset--) {
            BlockPos floor = BlockPos.containing(target).offset(0, offset - 1, 0);
            if (!level.hasChunk(floor.getX() >> 4, floor.getZ() >> 4)) continue;
            var shape = level.getBlockState(floor).getCollisionShape(level, floor);
            if (shape.isEmpty()) continue;
            Vec3 feet = new Vec3(target.x, floor.getY() + shape.max(Direction.Axis.Y), target.z);
            if (clear(level, boss, local.move(feet))) return feet;
        }
        return null;
    }

    private static boolean clear(ServerLevel level, MinotaurEntity boss, AABB box) {
        for (int x = (int)Math.floor(box.minX) >> 4; x <= (int)Math.floor(box.maxX) >> 4; x++)
            for (int z = (int)Math.floor(box.minZ) >> 4; z <= (int)Math.floor(box.maxZ) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        return !level.getBlockCollisions(boss, box).iterator().hasNext();
    }
}
