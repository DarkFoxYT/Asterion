package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** One interpolated, server-synchronized deck frame for players, camera and boat. */
public final class FerryDeckRender {
    public static final RenderStateDataKey<Quaternionf> TILT = RenderStateDataKey.create();
    private FerryDeckRender() { }
    public static Quaternionf tilt(CharonsFerryEntity boat, float partial) {
        float yaw = (180 - boat.getYRot(partial)) * Mth.DEG_TO_RAD;
        return new Quaternionf().rotationY(yaw).rotateX(boat.rockingPitch(partial) * Mth.DEG_TO_RAD)
                .rotateZ(boat.rockingRoll(partial) * Mth.DEG_TO_RAD).rotateY(-yaw);
    }
    public static Vec3 feet(Entity player, CharonsFerryEntity boat, float partial) {
        Vec3 position = player.getPosition(partial), center = boat.getPosition(partial);
        var normal = tilt(boat, partial).transform(new org.joml.Vector3f(0, 1, 0));
        double y = center.y + net.krodark.asterion.update.underworld.world.FerryHull.PIVOT
                + (net.krodark.asterion.update.underworld.world.FerryHull.DECK - net.krodark.asterion.update.underworld.world.FerryHull.PIVOT + .01) / normal.y
                - (normal.x * (position.x - center.x) + normal.z * (position.z - center.z)) / normal.y;
        if (player.getVehicle() == boat) y -= net.krodark.asterion.update.underworld.world.FerryHull.RIDER_DROP;
        return new Vec3(position.x, y, position.z);
    }
}
