package net.krodark.asterion.client.render.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

final class WeaponChainAttachments {
    private record Anchor(Object level, long tick, Vec3 point) { }
    private static final Map<Long, Anchor> HANDS = new HashMap<>();
    static void capture(int owner, int side, Vec3 point) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (HANDS.size() > 128) HANDS.clear();
        HANDS.put(((long)owner << 1) | (side > 0 ? 1 : 0), new Anchor(level, level.getGameTime(), point));
    }
    static Vec3 hand(int owner, int side) {
        var level = Minecraft.getInstance().level;
        Anchor anchor = HANDS.get(((long)owner << 1) | (side > 0 ? 1 : 0));
        return anchor != null && level == anchor.level && level.getGameTime() - anchor.tick <= 2 ? anchor.point : null;
    }
}
