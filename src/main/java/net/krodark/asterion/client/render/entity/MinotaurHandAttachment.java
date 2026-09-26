package net.krodark.asterion.client.render.entity;

import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

 
public final class MinotaurHandAttachment {
    private record Anchor(Object level, long tick, Vec3 hand) { }
    private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();
    private static final Map<Integer, Anchor> UP_ANCHORS = new HashMap<>();
    public static void captureUp(int playerId, Vec3 up) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (UP_ANCHORS.size() > 64) UP_ANCHORS.clear();
        UP_ANCHORS.put(playerId, new Anchor(level, level.getGameTime(), up));
    }
    public static org.joml.Quaternionf rotation(Entity player) {
        Anchor hand = ANCHORS.get(player.getId()), up = UP_ANCHORS.get(player.getId());
        if (feet(player) == null || up == null || up.level != hand.level || up.tick != hand.tick)
            return null;
        Vec3 axis = up.hand.subtract(hand.hand);
        if (axis.lengthSqr() < 0.000001) return null;
        return new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), axis.normalize().toVector3f());
    }
    private MinotaurHandAttachment() { }
    public static void capture(int playerId, Vec3 hand) {
        var level = Minecraft.getInstance().level;
        if (level == null || hand == null) return;
        if (ANCHORS.size() > 64) ANCHORS.clear();
        ANCHORS.put(playerId, new Anchor(level, level.getGameTime(), hand));
    }
    public static Vec3 feet(Entity player) {
        Anchor anchor = ANCHORS.get(player.getId());
        if (anchor == null || anchor.level != player.level() || player.level().getGameTime() - anchor.tick > 2
                || !MinotaurEntity.isHeld(player)) return null;
        return anchor.hand.add(0, -player.getBbHeight() * .52, 0);
    }
}
