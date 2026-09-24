package net.krodark.asterion.update.underworld;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** A small set of pose-aware body parts for silk contact, independent of the collision box. */
public final class WebPlayerShape {
    public record Part(Vec3 start, Vec3 end, double radius) {
        public Vec3 middle() { return start.lerp(end, .5); }
    }
    public record Contact(Vec3 strand, double gap) { }
    private WebPlayerShape() { }

    public static List<Part> parts(Player player) {
        Vec3 feet = player.position();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        if (player.isSwimming()) {
            Vec3 trunk = feet.add(0, .32, 0);
            return List.of(
                    new Part(trunk.add(forward.scale(-.40)), trunk.add(forward.scale(.35)), .23),
                    new Part(trunk.add(forward.scale(.40)), trunk.add(forward.scale(.68)), .18),
                    new Part(trunk.add(forward.scale(-.78)).add(right.scale(.14)), trunk.add(forward.scale(-.35)).add(right.scale(.14)), .12),
                    new Part(trunk.add(forward.scale(-.78)).add(right.scale(-.14)), trunk.add(forward.scale(-.35)).add(right.scale(-.14)), .12));
        }
        double scale = player.isCrouching() ? .82 : 1.0;
        return List.of(
                new Part(feet.add(0,.72*scale,0), feet.add(0,1.38*scale,0), .25),
                new Part(feet.add(0,1.45*scale,0), feet.add(0,1.70*scale,0), .18),
                new Part(feet.add(right.scale(.34)).add(0,.80*scale,0), feet.add(right.scale(.34)).add(0,1.34*scale,0), .12),
                new Part(feet.add(right.scale(-.34)).add(0,.80*scale,0), feet.add(right.scale(-.34)).add(0,1.34*scale,0), .12),
                new Part(feet.add(right.scale(.13)).add(0,.08,0), feet.add(right.scale(.13)).add(0,.75*scale,0), .12),
                new Part(feet.add(right.scale(-.13)).add(0,.08,0), feet.add(right.scale(-.13)).add(0,.75*scale,0), .12));
    }

    public static Contact contact(Vec3 a, Vec3 b, List<Part> parts) {
        Vec3 best = a;
        double gap = Double.POSITIVE_INFINITY;
        for (Part part : parts) for (double t : new double[]{0, .5, 1}) {
            Vec3 body = part.start.lerp(part.end, t);
            Vec3 strand = LimboWebSystem.nearest(a, b, body);
            double candidate = strand.distanceTo(body) - part.radius;
            if (candidate < gap) { gap = candidate; best = strand; }
        }
        return new Contact(best, gap);
    }
}
