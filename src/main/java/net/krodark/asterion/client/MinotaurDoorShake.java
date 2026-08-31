package net.krodark.asterion.client;

import net.krodark.asterion.block.MinotaurDoorBlockEntity;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Local camera feedback from synchronized door motion, independent of the maze dimension. */
public final class MinotaurDoorShake {
    private static final Set<MinotaurDoorBlockEntity> DOORS = Collections.newSetFromMap(new WeakHashMap<>());
    private MinotaurDoorShake() { }

    public static void track(MinotaurDoorBlockEntity door) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        if (camera.distanceToSqr(Vec3.atCenterOf(door.getBlockPos())) < 24 * 24) DOORS.add(door);
    }

    public static DeadSunClientEvents.Sample sample(Vec3 camera, float partialTick) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            DOORS.clear();
            return DeadSunClientEvents.Sample.NONE;
        }
        double strength = 0;
        var iterator = DOORS.iterator();
        while (iterator.hasNext()) {
            var door = iterator.next();
            if (door.isRemoved() || door.getLevel() != level) {
                iterator.remove();
                continue;
            }
            double distance = camera.distanceTo(Vec3.atCenterOf(door.getBlockPos()).add(0, 2, 0));
            if (distance > 24) { iterator.remove(); continue; }
            double proximity = Math.max(0, 1 - distance / 18);
            strength += door.movementRumble(partialTick) * proximity * proximity;
        }
        // Nearby doors never stack into an aggressive camera effect.
        strength = Math.min(1, strength);
        if (strength <= 0) return DeadSunClientEvents.Sample.NONE;
        double time = level.getGameTime() + partialTick;
        double sway = Math.sin(time * 1.35) * strength;
        double tremor = Math.sin(time * 1.9 + .8) * strength;
        return new DeadSunClientEvents.Sample(new Vec3(sway * .008, tremor * .006, 0),
                (float)(sway * .09), (float)(tremor * .065), Vec3.ZERO, 0);
    }
}
