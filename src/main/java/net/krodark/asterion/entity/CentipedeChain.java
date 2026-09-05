package net.krodark.asterion.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.ArrayList;

/** One persistent chain per entity; ticked on both sides and only sampled by render passes. */
public final class CentipedeChain {
    public static final int MAX_SEGMENTS = 32;
    private static final int SUBSTEPS = 4;
    public record Pose(Vec3 position, Vec3 normal, Vec3 forward) {}
    private final Pose[] current = new Pose[MAX_SEGMENTS];
    private final Pose[] previous = new Pose[MAX_SEGMENTS];
    private final Pose[][] displayHistory = new Pose[4][MAX_SEGMENTS];
    private final float[][] displayGait = new float[4][MAX_SEGMENTS], displaySpeed = new float[4][MAX_SEGMENTS];
    private final Pose[][] steps = new Pose[SUBSTEPS + 1][MAX_SEGMENTS];
    private final float[] gait = new float[MAX_SEGMENTS], previousGait = new float[MAX_SEGMENTS];
    private final float[] speed = new float[MAX_SEGMENTS], previousSpeed = new float[MAX_SEGMENTS];
    private final List<List<AABB>> interpolationBlocks = new ArrayList<>();
    private final CentipedeCollision[] tickCollision = new CentipedeCollision[MAX_SEGMENTS];
    private int count;
    private final CentipedeTrail trail = new CentipedeTrail();

    public CentipedeChain() {
        for (int i = 0; i < MAX_SEGMENTS; i++) interpolationBlocks.add(List.of());
    }

    public void tick(Vec3 head, Vec3 normal, Vec3 facing, int requestedCount, CentipedeCollision collision) {
        int active = Mth.clamp(requestedCount, 1, MAX_SEGMENTS);
        normal = CentipedeFrame.unit(normal, CentipedeFrame.DOWN);
        facing = CentipedeFrame.tangent(facing, normal, new Vec3(0, 0, -1));
        boolean reset = count == 0 || current[0].position.distanceToSqr(head) > 64;
        if (reset) count = 0;
        for (int i = count; i < active; i++) {
            Vec3 initial = i == 0 ? head : current[i - 1].position.subtract(facing.scale(CentipedeFrame.LINK_LENGTH));
            var contact = collision.resolve(initial, initial, normal, facing);
            current[i] = new Pose(contact.position(), contact.normal(), facing);
            previous[i] = current[i];
            for (Pose[] frame : displayHistory) frame[i] = null;
            gait[i] = previousGait[i] = speed[i] = previousSpeed[i] = 0;
        }
        count = active;
        if (reset) trail.reset(current[0]);
        System.arraycopy(current, 0, previous, 0, count);
        System.arraycopy(gait, 0, previousGait, 0, count);
        System.arraycopy(speed, 0, previousSpeed, 0, count);
        // Smaller constraint steps reduce the visible stop/pull cadence without dragging the
        // whole tail around the head or weakening collision. Links retain their world positions.
        Vec3 start = current[0].position;
        Vec3 startNormal = current[0].normal;
        double reach = 4 + start.distanceTo(head);
        for (int i = 0; i < count; i++) tickCollision[i] = collision.cachedIn(
                new AABB(current[i].position, i == 0 ? head : current[i - 1].position).inflate(reach));
        System.arraycopy(current, 0, steps[0], 0, count);
        for (int step = 1; step <= SUBSTEPS; step++) {
            double fraction = step / (double)SUBSTEPS;
            Vec3 stepNormal = CentipedeFrame.unit(startNormal.lerp(normal, fraction), normal);
            solve(start.lerp(head, fraction), stepNormal, facing);
            System.arraycopy(current, 0, steps[step], 0, count);
        }
        if (reset) {
            System.arraycopy(current, 0, previous, 0, count);
            for (Pose[] step : steps) System.arraycopy(current, 0, step, 0, count);
        }
        for (int i = 0; i < count; i++) {
            boolean newDisplay = reset || displayHistory[0][i] == null;
            if (newDisplay) {
                for (Pose[] frame : displayHistory) frame[i] = current[i];
            } else {
                for (int frame = 0; frame < 3; frame++) displayHistory[frame][i] = displayHistory[frame + 1][i];
                displayHistory[3][i] = current[i];
            }
            Pose a = previous[i], b = current[i];
            Vec3 movement = b.position.subtract(a.position);
            gait[i] += (float)Math.min(.6, movement.length()) * 3F;
            speed[i] = Mth.lerp(.3F, speed[i], (float)Mth.clamp(movement.length() / .31, 0, 1));
            for (int frame = 0; frame < 4; frame++) {
                displayGait[frame][i] = newDisplay || frame == 3 ? gait[i] : displayGait[frame + 1][i];
                displaySpeed[frame][i] = newDisplay || frame == 3 ? speed[i] : displaySpeed[frame + 1][i];
            }
            AABB area = CentipedeCollision.volume(a.position, CentipedeFrame.extents(a.normal, a.forward))
                    .minmax(CentipedeCollision.volume(b.position, CentipedeFrame.extents(b.normal, b.forward))).inflate(0.8);
            for (Pose[] frame : displayHistory) {
                Pose pose = frame[i];
                area = area.minmax(CentipedeCollision.volume(pose.position, CentipedeFrame.extents(pose.normal, pose.forward)));
            }
            interpolationBlocks.set(i, tickCollision[i].collect(area));
        }
    }

    private void solve(Vec3 head, Vec3 normal, Vec3 facing) {
        CentipedeCollision collision = tickCollision[0];
        var front = collision.followSurface(current[0].position, head, normal, current[0].forward);
        // Keep the head's probed surface blend; don't quantize it back to one block face.
        normal = front.normal();
        Vec3 frontFacing = CentipedeMotion.followHeading(current[0].forward, facing, normal, .085);
        front = collision.followSurface(front.position(), front.position(), normal, frontFacing);
        current[0] = new Pose(front.position(), normal, frontFacing);
        trail.record(current[0]);
        for (int i = 1; i < count; i++) {
            collision = tickCollision[i];
            Pose old = current[i];
            // Follow the route actually taken, not a straight chord through the corner.
            // The delayed support normal belongs to this part of the trail, not the head now.
            Pose target = trail.behind(i * CentipedeFrame.LINK_LENGTH);
            Vec3 desired = target.position;
            Pose leader = current[i - 1];
            Vec3 jointNormal = CentipedeFrame.unit(target.normal.lerp(leader.normal, .15), target.normal);
            Vec3 targetFacing = CentipedeFrame.tangent(leader.position.subtract(desired), jointNormal, target.forward);
            var contact = collision.followSurface(old.position, desired, jointNormal, old.forward);
            Vec3 newFacing = CentipedeMotion.followHeading(old.forward, targetFacing, contact.normal(), .12);
            // Resolve the final orientation too: turning a wide segment must not overlap a wall.
            contact = collision.followSurface(contact.position(), contact.position(), contact.normal(), newFacing);
            for (int pass = 0; pass < 3; pass++) {
                Vec3 separated = contact.position();
                for (int other = 0; other < count; other++) {
                    if (Math.abs(i - other) <= 1) continue;
                    separated = CentipedeBodyConstraint.separate(separated, current[other].position, contact.normal(), newFacing);
                }
                // Don't trade self-overlap for disconnected links. World collision takes
                // priority when there isn't enough room to completely spread the chain.
                Vec3 away = separated.subtract(leader.position);
                double maximum = CentipedeFrame.LINK_LENGTH + .25;
                if (away.lengthSqr() > maximum * maximum)
                    separated = leader.position.add(away.normalize().scale(maximum));
                contact = collision.followSurface(contact.position(), separated, contact.normal(), newFacing);
            }
            current[i] = new Pose(contact.position(), contact.normal(), newFacing);
        }
    }

    public Pose sample(int index, float partialTick) {
        if (count == 0) throw new IllegalStateException("Chain not initialized");
        int link = Mth.clamp(index, 0, count - 1);
        // Interpolate along the swept route, not a tick-long chord that cuts through
        // corners and then jumps outward when the render collision guard corrects it.
        double progress = Mth.clamp(partialTick, 0, 1) * SUBSTEPS;
        int step = Math.min((int)progress, SUBSTEPS - 1);
        double alpha = progress - step;
        Pose a = steps[step][link], b = steps[step + 1][link];
        Vec3 normal = CentipedeFrame.unit(a.normal.lerp(b.normal, alpha), b.normal);
        Vec3 forward = CentipedeFrame.tangent(a.forward.lerp(b.forward, alpha), normal, b.forward);
        Vec3 position = a.position.lerp(b.position, alpha);
        if (alpha > 0 && alpha < 1)
            position = CentipedeCollision.keepOutside(position, normal, forward, interpolationBlocks.get(link));
        return new Pose(position, normal, forward);
    }

    /** One extra tick of presentation history removes tick and packet cadence from the body and seats.
     * Cubic B-spline weights share both position and velocity at tick boundaries, never extrapolate,
     * and are independent of frame rate or the number of cameras/render passes sampling the chain. */
    public Pose sampleSmoothed(int index, float partialTick) {
        if (count == 0) throw new IllegalStateException("Chain not initialized");
        int link = Mth.clamp(index, 0, count - 1);
        double t = Mth.clamp(partialTick, 0, 1), t2 = t * t, t3 = t2 * t;
        double a = (1 - 3 * t + 3 * t2 - t3) / 6;
        double b = (4 - 6 * t2 + 3 * t3) / 6;
        double c = (1 + 3 * t + 3 * t2 - 3 * t3) / 6;
        double d = t3 / 6;
        Pose p = displayHistory[0][link], q = displayHistory[1][link];
        Pose r = displayHistory[2][link], s = displayHistory[3][link];
        Vec3 normal = CentipedeFrame.unit(blend(p.normal, q.normal, r.normal, s.normal, a, b, c, d), r.normal);
        Vec3 forward = CentipedeFrame.tangent(blend(p.forward, q.forward, r.forward, s.forward, a, b, c, d), normal, r.forward);
        Vec3 position = blend(p.position, q.position, r.position, s.position, a, b, c, d);
        position = CentipedeCollision.keepOutside(position, normal, forward, interpolationBlocks.get(link));
        return new Pose(position, normal, forward);
    }

    private static Vec3 blend(Vec3 p, Vec3 q, Vec3 r, Vec3 s, double a, double b, double c, double d) {
        return new Vec3(p.x * a + q.x * b + r.x * c + s.x * d,
                p.y * a + q.y * b + r.y * c + s.y * d,
                p.z * a + q.z * b + r.z * c + s.z * d);
    }

    public float smoothedGait(int index, float partial) { return sampleAnimation(displayGait, index, partial); }
    public float smoothedSpeed(int index, float partial) { return sampleAnimation(displaySpeed, index, partial); }

    private float sampleAnimation(float[][] frames, int index, float partial) {
        int link = Mth.clamp(index, 0, count - 1);
        float t = Mth.clamp(partial, 0, 1), t2 = t * t, t3 = t2 * t;
        return (frames[0][link] * (1 - 3 * t + 3 * t2 - t3) + frames[1][link] * (4 - 6 * t2 + 3 * t3)
                + frames[2][link] * (1 + 3 * t + 3 * t2 - 3 * t3) + frames[3][link] * t3) / 6;
    }

    public boolean initialized() { return count > 0; }

    public Vec3 limitHeadMotion(Vec3 motion) {
        if (count < 3) return motion;
        double allowed = 1;
        Vec3 head = current[0].position;
        Vec3 nose = head.add(current[0].forward.scale(1.45));
        for (int i = 2; i < count; i++) {
            allowed = Math.min(allowed, CentipedeBodyConstraint.movementFraction(head, motion, current[i].position));
            allowed = Math.min(allowed, CentipedeBodyConstraint.movementFraction(nose, motion, current[i].position));
        }
        return motion.scale(allowed);
    }

    public float gait(int index, float partial) {
        index = Mth.clamp(index, 0, count - 1);
        return Mth.lerp(Mth.clamp(partial, 0, 1), previousGait[index], gait[index]);
    }

    public float speed(int index, float partial) {
        index = Mth.clamp(index, 0, count - 1);
        return Mth.lerp(Mth.clamp(partial, 0, 1), previousSpeed[index], speed[index]);
    }
}
