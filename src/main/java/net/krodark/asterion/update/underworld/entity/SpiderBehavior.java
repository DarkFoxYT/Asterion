package net.krodark.asterion.update.underworld.entity;

/** Deterministic encounter decisions, independent of navigation/rendering. */
public final class SpiderBehavior {
    private SpiderBehavior() { }
    public static boolean night(long time) { return Math.floorMod(time,24000L)>=12000; }
    public static boolean ambush(int approachTicks,double distance,double previous,boolean noticed,int waitingTicks) {
        return waitingTicks>8 && distance<23 && (noticed || approachTicks>22 && distance>previous+.06 || distance<5);
    }
    public static boolean flee(float health,float maximum,float hit) { return health<maximum*.5F || hit>30F; }
}
