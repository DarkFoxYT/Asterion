package net.krodark.asterion.physics;

import net.minecraft.world.phys.Vec3;

/** Six plates exactly partition the original 3.5 by 5 block door leaf. */
public final class DoorFracture {
    public static final int FIRST_VARIANT=10, COUNT=6;
    public static final Vec3 HALF=new Vec3(.875,5.0/6,.25);
    private DoorFracture() { }
    public static Vec3 center(int index) {
        if (index<0 || index>=COUNT) throw new IllegalArgumentException("Door fragment index");
        return new Vec3(index%2==0?.875:-.875,(index/2-1)*5.0/3,0);
    }
    public static String model(int variant) { return "minotaur_door_fragment_"+(variant-FIRST_VARIANT); }
}
