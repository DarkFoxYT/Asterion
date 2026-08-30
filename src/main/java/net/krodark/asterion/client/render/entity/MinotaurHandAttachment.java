package net.krodark.asterion.client.render.entity;

import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/** Visual attachment to the evaluated hand bone; gameplay remains server-authoritative. */
public final class MinotaurHandAttachment {
    private record Anchor(Object level, long tick, Vec3 hand) { }
    private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();
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
