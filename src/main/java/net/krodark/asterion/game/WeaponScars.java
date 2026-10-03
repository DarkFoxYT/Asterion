package net.krodark.asterion.game;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.network.BossTelegraphPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Bounded, temporary surface marks; never edits terrain or loads chunks. */
public final class WeaponScars {
    private static int serial = -1;
    private WeaponScars() { }
    public static void line(ServerLevel level, Vec3 a, Vec3 b) {
        Vec3 delta = b.subtract(a).multiply(1, 0, 1);
        double length = delta.length();
        if (length < .05 || length > 16) return;
        var payload = new BossTelegraphPayload(a, delta.normalize(), (float)length, 600,
                BossTelegraphPayload.SCAR, serial--, 0, .065F, 0);
        for (var player : level.players()) if (player.position().distanceToSqr(a) < 96 * 96
                && ServerPlayNetworking.canSend(player, BossTelegraphPayload.TYPE)) ServerPlayNetworking.send(player, payload);
    }
    public static void arc(ServerLevel level, Vec3 origin, float yaw, double radius) {
        double aim = Math.toRadians(yaw + 90);
        Vec3 previous = origin.add(Math.cos(aim - Math.PI / 4) * radius, 0, Math.sin(aim - Math.PI / 4) * radius);
        for (int i = 1; i <= 8; i++) {
            double angle = aim - Math.PI / 4 + Math.PI / 2 * i / 8;
            Vec3 next = origin.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            line(level, previous, next); previous = next;
        }
    }
    public static void wallSlash(ServerLevel level, net.minecraft.world.entity.Entity attacker, float yaw, float tilt) {
        Vec3 origin=attacker.position().add(0,attacker.getBbHeight()*.5,0);
        Vec3 forward=Vec3.directionFromRotation(0,yaw);
        var hit=level.clip(new net.minecraft.world.level.ClipContext(origin,origin.add(forward.scale(7)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,attacker));
        if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection().getAxis().isVertical()
                || !level.getFluidState(hit.getBlockPos()).isEmpty()) return;
        Vec3 normal=hit.getDirection().getUnitVec3(), center=hit.getLocation().add(normal.scale(.012));
        var payload=new BossTelegraphPayload(center,normal,3.5F,600,BossTelegraphPayload.WALL_SCAR,serial--,tilt,.06F,0);
        for(var player:level.players())if(player.position().distanceToSqr(center)<96*96
                && ServerPlayNetworking.canSend(player,BossTelegraphPayload.TYPE))ServerPlayNetworking.send(player,payload);
    }
}
