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
        Vec3 stop = door.add(inward.scale(width * .5 + 5));
        return start.lerp(stop, MinotaurAnimationTiming.entryWalkDistance(tick, 1));
    }
    public static double animationSeconds(double tick) {
        if (tick < MinotaurAnimationTiming.ENTRY_BREAK_TICK) return 0;
        if (tick < MinotaurAnimationTiming.ENTRY_WALK_END_TICK)
            return MinotaurAnimationTiming.entryWalkDistance(tick, 1) * MinotaurAnimationTiming.RUN_LENGTH;
        return MinotaurAnimationTiming.ENTRY_ROAR.seconds(tick);
    }
}
