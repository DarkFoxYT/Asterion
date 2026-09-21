package net.krodark.asterion.entity;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;

 
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
        return sampler().behind(distance);
    }

    /** Samples increasing distances in one traversal; discard before recording another point. */
    public Sampler sampler() { return new Sampler(); }

    public final class Sampler {
        private final java.util.Iterator<Point> iterator = points.descendingIterator();
        private Point newer = iterator.next();
        private Point older = iterator.hasNext() ? iterator.next() : null;
        private final double headDistance = newer.distance;
        private double lastDistance = Double.NEGATIVE_INFINITY;

        public CentipedeChain.Pose behind(double distance) {
            if (distance < lastDistance) throw new IllegalArgumentException("Sample distances must increase");
            lastDistance = distance;
            double at = headDistance - distance;
            while (older != null) {
                if (older.distance <= at) {
                    double alpha = Math.clamp((at - older.distance) / (newer.distance - older.distance), 0, 1);
                    Vec3 normal = CentipedeFrame.unit(older.pose.normal().lerp(newer.pose.normal(), alpha), newer.pose.normal());
                    Vec3 forward = CentipedeFrame.tangent(older.pose.forward().lerp(newer.pose.forward(), alpha), normal, newer.pose.forward());
                    return new CentipedeChain.Pose(older.pose.position().lerp(newer.pose.position(), alpha), normal, forward);
                }
                newer = older;
                older = iterator.hasNext() ? iterator.next() : null;
            }
            return new CentipedeChain.Pose(newer.pose.position().subtract(newer.pose.forward().scale(newer.distance - at)),
                    newer.pose.normal(), newer.pose.forward());
        }
    }
}
