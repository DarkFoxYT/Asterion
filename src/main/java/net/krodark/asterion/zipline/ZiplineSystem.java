package net.krodark.asterion.zipline;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.ZiplineAnchorBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Persistent anchors with lightweight, server-authoritative rider motion. */
public final class ZiplineSystem {
    private static final Map<net.minecraft.world.level.Level, Set<ZiplineAnchorBlockEntity>> ANCHORS = new WeakHashMap<>();
    private static final Map<UUID, Ride> RIDERS = new HashMap<>();
    private record Ride(ZiplineAnchorBlockEntity anchor, double progress) {}
    private ZiplineSystem() {}
    public static void register(ZiplineAnchorBlockEntity anchor) {
        if (anchor.getLevel() != null) ANCHORS.computeIfAbsent(anchor.getLevel(), ignored ->
                Collections.newSetFromMap(new IdentityHashMap<>())).add(anchor);
    }
    public static void unregister(ZiplineAnchorBlockEntity anchor) {
        Set<ZiplineAnchorBlockEntity> set = ANCHORS.get(anchor.getLevel()); if (set != null) set.remove(anchor);
        RIDERS.entrySet().removeIf(entry -> entry.getValue().anchor == anchor);
    }
    public static void toggle(Player player) {
        if (RIDERS.remove(player.getUUID()) != null) return;
        Set<ZiplineAnchorBlockEntity> anchors = ANCHORS.get(player.level());
        if (anchors == null) return;
        ZiplineAnchorBlockEntity best = null; double bestT = 0, bestDistance = 2.75D;
        for (ZiplineAnchorBlockEntity anchor : anchors) {
            if (!anchor.primary() || anchor.other() == null || anchor.isRemoved()) continue;
            Vec3 a = anchor.getBlockPos().getCenter(), b = anchor.other().getCenter();
            for (int sample = 0; sample <= 32; sample++) {
                double t = sample / 32D, distance = point(a, b, t).distanceTo(player.position());
                if (distance < bestDistance) { bestDistance = distance; best = anchor; bestT = t; }
            }
        }
        if (best != null) RIDERS.put(player.getUUID(), new Ride(best, bestT));
    }
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Ride>> iterator = RIDERS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Ride ride = entry.getValue();
            if (player == null || ride.anchor.isRemoved() || ride.anchor.other() == null
                    || player.isShiftKeyDown() || !(player.getMainHandItem().is(Asterion.ZIPLINE_HOOK)
                    || player.getOffhandItem().is(Asterion.ZIPLINE_HOOK))) { iterator.remove(); continue; }
            Vec3 a = ride.anchor.getBlockPos().getCenter(), b = ride.anchor.other().getCenter();
            Vec3 tangent = tangent(a, b, ride.progress).normalize();
            double facing = player.getLookAngle().dot(tangent);
            if (Math.abs(facing) < .12D) facing = .12D * (facing < 0 ? -1 : 1);
            double downhill = -tangent.y * Math.signum(facing);
            double speed = Math.clamp(.18D + Math.max(0, downhill) * .22D, .12D, .42D);
            double length = Math.max(1, a.distanceTo(b));
            double next = ride.progress + Math.signum(facing) * speed / length;
            if (next <= 0 || next >= 1) { iterator.remove(); continue; }
            Vec3 position = point(a, b, next).subtract(0, player.getBbHeight() * .72D, 0);
            player.setPos(position.x, position.y, position.z);
            player.setDeltaMovement(tangent.scale(Math.signum(facing) * speed));
            player.fallDistance = 0;
            entry.setValue(new Ride(ride.anchor, next));
        }
    }
    public static Vec3 point(Vec3 a, Vec3 b, double t) {
        double distance = a.distanceTo(b);
        double sag = Math.min(5D, .055D * distance + .0012D * distance * distance);
        return a.lerp(b, t).subtract(0, sag * 4D * t * (1D - t), 0);
    }
    private static Vec3 tangent(Vec3 a, Vec3 b, double t) {
        double epsilon = .01D;
        return point(a, b, Math.min(1, t + epsilon)).subtract(point(a, b, Math.max(0, t - epsilon)));
    }
}
