package net.krodark.asterion.entity;

import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.world.phys.Vec3;

/** Shared cinematic path: server movement, render interpolation and camera framing. */
public final class MinotaurEntranceMotion {
    private MinotaurEntranceMotion() { }
    public static double setback(double width) { return Math.max(8, width * .5 + 5); }
    public static Vec3 point(double tick, double width) {
        var facing = MinotaurArenaEntrances.BOSS_ENTRANCE;
        Vec3 door = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(facing));
        Vec3 inward = facing.getOpposite().getUnitVec3();
        Vec3 start = door.subtract(inward.scale(setback(width)));
        Vec3 launch = door.add(inward.scale(width * .5 + 5));
        Vec3 landing = new Vec3(.5, door.y, .5);
        if (tick < MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK) {
            double progress = MinotaurAnimationTiming.entryWalkDistance(tick, 1);
            return start.lerp(launch, progress);
        }
        double t = Math.clamp((tick - MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK)
                / (MinotaurAnimationTiming.ENTRY_LAND_TICK - MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK), 0, 1);
        return launch.lerp(landing, t).add(0, 48 * t * (1 - t), 0);
    }
    public static double animationSeconds(double tick) {
        if (tick < MinotaurAnimationTiming.ENTRY_BREAK_TICK) return 0;
        if (tick < MinotaurAnimationTiming.ENTRY_CROUCH_TICK)
            return MinotaurAnimationTiming.entryWalkDistance(tick, 1) * MinotaurAnimationTiming.RUN_LENGTH;
        if (tick < MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK)
            return (tick - MinotaurAnimationTiming.ENTRY_CROUCH_TICK)
                    / (MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK - MinotaurAnimationTiming.ENTRY_CROUCH_TICK) * .6667;
        if (tick < MinotaurAnimationTiming.ENTRY_LAND_TICK - 6) {
            // The authored leap reaches extension at .875s; never replay its crouch in midair.
            double t = Math.clamp((tick - MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK) / 8, 0, 1);
            return .6667 + t * (.9583 - .6667);
        }
        if (tick < MinotaurAnimationTiming.ENTRY_LAND_TICK)
            return (tick - (MinotaurAnimationTiming.ENTRY_LAND_TICK - 6)) / 6 * .1667;
        if (tick < MinotaurAnimationTiming.ENTRY_WALK_END_TICK)
            return .1667 + (tick - MinotaurAnimationTiming.ENTRY_LAND_TICK)
                    / (MinotaurAnimationTiming.ENTRY_WALK_END_TICK - MinotaurAnimationTiming.ENTRY_LAND_TICK) * (.9583 - .1667);
        return MinotaurAnimationTiming.ENTRY_ROAR.seconds(tick);
    }
}
