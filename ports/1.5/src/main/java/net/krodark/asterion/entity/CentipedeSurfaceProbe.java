package net.krodark.asterion.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

 
public final class CentipedeSurfaceProbe {
    public record Approach(Direction face, double gap, Vec3 normal) {}
    private CentipedeSurfaceProbe() {}

    public static Approach ahead(AABB body, Vec3 motion, Direction support, Iterable<AABB> blocks) {
        double speed = motion.length();
        if (speed < .015) return null;
        Vec3 heading = motion.scale(1 / speed);
        double reach = .10;
        double best = Double.MAX_VALUE;
        Approach result = null;
        for (Direction face : Direction.values()) {
            Vec3 normal = directionVector(face);
            if (Math.abs(normal.dot(directionVector(support))) > .5 || heading.dot(normal) < .25) continue;
            for (AABB block : blocks) {
                double gap = switch (face) {
                    case EAST -> block.minX - body.maxX;
                    case WEST -> body.minX - block.maxX;
                    case UP -> block.minY - body.maxY;
                    case DOWN -> body.minY - block.maxY;
                    case SOUTH -> block.minZ - body.maxZ;
                    case NORTH -> body.minZ - block.maxZ;
                };
                if (gap < -.02 || gap > reach) continue;
                double along = Math.max(0, gap) / heading.dot(normal);
                AABB arrived = body.move(heading.scale(along + .025));
                if (!arrived.intersects(block) || along >= best) continue;
                result = new Approach(face, gap, directionVector(support));
                best = along;
            }
        }
        return result;
    }

    public static Approach aroundEdge(AABB body, Vec3 motion, Direction support, List<AABB> blocks) {
        if (motion.lengthSqr() < .000225) return null;
        Vec3 supportNormal = directionVector(support);
        Vec3 heading = CentipedeFrame.tangent(motion, supportNormal, motion);
        AABB nextSupport = body.move(heading.scale(.4)).move(supportNormal.scale(.34));
         
        for (AABB block : blocks) if (nextSupport.intersects(block)) return null;
        for (Direction travel : Direction.values()) {
            Vec3 travelNormal = directionVector(travel);
            if (heading.dot(travelNormal) < .7
                    || Math.abs(travelNormal.dot(supportNormal)) > .5) continue;
            for (AABB block : blocks) {
                if (!body.move(supportNormal.scale(.34)).intersects(block)) continue;
                double edge = switch (travel) {
                    case EAST -> block.maxX - body.minX;
                    case WEST -> body.maxX - block.minX;
                    case UP -> block.maxY - body.minY;
                    case DOWN -> body.maxY - block.minY;
                    case SOUTH -> block.maxZ - body.minZ;
                    case NORTH -> body.maxZ - block.minZ;
                };
                if (edge < -.02 || edge > .18) continue;
                Direction face = travel.getOpposite();
                Vec3 faceNormal = directionVector(face);
                if (!body.move(faceNormal.scale(.12)).intersects(block)) continue;
                return new Approach(face, edge, CentipedeFrame.unit(
                        supportNormal.lerp(faceNormal, .4), supportNormal));
            }
        }
        return null;
    }

    private static Vec3 directionVector(Direction direction) {
        return Vec3.atLowerCornerOf(direction.getNormal());
    }
}
