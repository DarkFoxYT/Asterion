package net.krodark.asterion.entity;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;

/** Distance-spaced world history: a corner stays in the trail until the tail has passed it. */
public final class CentipedeTrail {
    private record Point(double distance, CentipedeChain.Pose pose) {}
    private final ArrayDeque<Point> points = new ArrayDeque<>();
    private static final double RETAIN = (CentipedeChain.MAX_SEGMENTS + 2) * CentipedeFrame.LINK_LENGTH;

    public void reset(CentipedeChain.Pose head) {
        points.clear();
        points.add(new Point(-RETAIN, new CentipedeChain.Pose(
                head.position().subtract(head.forward().scale(RETAIN)), head.normal(), head.forward())));
        points.add(new Point(0, head));
    }

    public void record(CentipedeChain.Pose pose) {
        if (points.isEmpty()) { reset(pose); return; }
        Point last = points.getLast();
        double moved = last.pose.position().distanceTo(pose.position());
        // Looking around must never advance the path or spin the stationary tail.
        if (moved < 1e-6) return;
        if (points.size() > 2) {
            var iterator = points.descendingIterator();
            iterator.next();
            Point previous = iterator.next();
            if (last.distance - previous.distance < .025) {
                points.removeLast();
                last = previous;
                moved = last.pose.position().distanceTo(pose.position());
            }
        }
        if (moved < 1e-6) return;
        points.addLast(new Point(last.distance + moved, pose));
        while (points.size() > 2 && (points.getLast().distance - points.getFirst().distance > RETAIN + 2
                || points.size() > 4096))
            points.removeFirst();
    }

    public CentipedeChain.Pose behind(double distance) {
        double at = points.getLast().distance - distance;
        var iterator = points.descendingIterator();
        Point newer = iterator.next();
        while (iterator.hasNext()) {
            Point older = iterator.next();
            if (older.distance <= at) {
                double alpha = Math.clamp((at - older.distance) / (newer.distance - older.distance), 0, 1);
                Vec3 normal = CentipedeFrame.unit(older.pose.normal().lerp(newer.pose.normal(), alpha), newer.pose.normal());
                Vec3 forward = CentipedeFrame.tangent(older.pose.forward().lerp(newer.pose.forward(), alpha), normal, newer.pose.forward());
                return new CentipedeChain.Pose(older.pose.position().lerp(newer.pose.position(), alpha), normal, forward);
            }
            newer = older;
        }
        return new CentipedeChain.Pose(newer.pose.position().subtract(newer.pose.forward().scale(newer.distance - at)),
                newer.pose.normal(), newer.pose.forward());
    }
}
