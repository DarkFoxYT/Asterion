package net.krodark.asterion.entity;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Swept, conservative body volumes against actual block-shape boxes (including slabs/stairs). */
public final class CentipedeCollision {
    private static final double SKIN = 0.025D;
    private static final double REACH = 0.45D;
    private final Geometry geometry;

    @FunctionalInterface
    public interface Geometry { Iterable<AABB> boxes(AABB region); }
    public record Contact(Vec3 position, Vec3 normal) {}

    public CentipedeCollision(Geometry geometry) { this.geometry = geometry; }

    public List<AABB> collect(AABB region) {
        List<AABB> blocks = new ArrayList<>();
        for (AABB box : geometry.boxes(region)) if (box.intersects(region)) blocks.add(box);
        return List.copyOf(blocks);
    }

    /** Reuse the world's voxel shapes through all substeps of one tick. Outlier moves
     * fall back to the live geometry rather than silently losing collisions. */
    public CentipedeCollision cachedIn(AABB area) {
        List<AABB> blocks = collect(area);
        return new CentipedeCollision(query -> query.minX >= area.minX && query.maxX <= area.maxX
                && query.minY >= area.minY && query.maxY <= area.maxY
                && query.minZ >= area.minZ && query.maxZ <= area.maxZ ? blocks : geometry.boxes(query));
    }

    /** Stateless render interpolation guard, using geometry captured by the last game tick. */
    public static Vec3 keepOutside(Vec3 position, Vec3 normal, Vec3 forward, List<AABB> blocks) {
        return pushOut(position, CentipedeFrame.extents(normal, forward), blocks, normal.scale(-1));
    }

    public Contact resolve(Vec3 from, Vec3 wanted, Vec3 normal, Vec3 forward) {
        Vec3 extents = CentipedeFrame.extents(normal, forward);
        AABB region = volume(from, extents).minmax(volume(wanted, extents)).inflate(2.0D);
        List<AABB> blocks = collect(region);

        Vec3 start = pushOut(from, extents, blocks, normal.scale(-1));
        Vec3 position = sweep(start, wanted.subtract(from), extents, blocks);
        Vec3 contactNormal = normal;
        double best = Double.MAX_VALUE;
        double gapToSurface = 0;

        // Prefer the current support at corners; steering never supplies the belly normal.
        for (AABB block : blocks) for (int axis = 0; axis < 3; axis++) for (int sign : new int[]{-1, 1}) {
            int other = (axis + 1) % 3, last = (axis + 2) % 3;
            if (component(position, other) < min(block, other) - component(extents, other) * 0.7
                    || component(position, other) > max(block, other) + component(extents, other) * 0.7
                    || component(position, last) < min(block, last) - component(extents, last) * 0.7
                    || component(position, last) > max(block, last) + component(extents, last) * 0.7) continue;
            double face = sign > 0 ? max(block, axis) : min(block, axis);
            double distance = (component(position, axis) - face) * sign;
            if (distance < 0) continue;
            double gap = distance - component(extents, axis);
            if (gap < -SKIN || gap > REACH) continue;
            Vec3 outward = axisVector(axis, sign);
            double score = Math.abs(gap - SKIN) + (1.0D + normal.dot(outward)) * 0.18D;
            if (score < best) {
                best = score;
                contactNormal = outward.scale(-1);
                gapToSurface = gap;
            }
        }

        if (best != Double.MAX_VALUE) {
            Vec3 newExtents = CentipedeFrame.extents(contactNormal, forward);
            // Close a small contact gap with a swept move, never teleport through the surface.
            Vec3 snap = contactNormal.scale(Math.max(0, gapToSurface - SKIN));
            position = sweep(position, snap, newExtents, blocks);
            position = pushOut(position, newExtents, blocks, contactNormal.scale(-1));
        }
        return new Contact(position, contactNormal);
    }

    /** Follow a validated, delayed surface frame without snapping its blend back to a
     * cardinal face. The full rotated volume is still swept and kept outside blocks. */
    public Contact followSurface(Vec3 from, Vec3 wanted, Vec3 normal, Vec3 forward) {
        Vec3 half = CentipedeFrame.extents(normal, forward);
        List<AABB> blocks = collect(volume(from, half).minmax(volume(wanted, half)).inflate(2));
        Vec3 start = pushOut(from, half, blocks, normal.scale(-1));
        Vec3 position = sweep(start, wanted.subtract(start), half, blocks);
        return new Contact(pushOut(position, half, blocks, normal.scale(-1)), normal);
    }

    public static AABB volume(Vec3 center, Vec3 half) {
        return new AABB(center.x - half.x, center.y - half.y, center.z - half.z,
                center.x + half.x, center.y + half.y, center.z + half.z);
    }

    private static Vec3 sweep(Vec3 from, Vec3 move, Vec3 half, List<AABB> blocks) {
        AABB box = volume(from, half);
        Vec3 result = Vec3.ZERO;
        int first = Math.abs(move.x) > Math.abs(move.z) ? 0 : 2;
        for (int axis : new int[]{1, first, 2 - first}) {
            double amount = component(move, axis);
            for (AABB obstacle : blocks) amount = clip(box, obstacle, axis, amount);
            Vec3 step = axisVector(axis, amount);
            box = box.move(step);
            result = result.add(step);
        }
        return from.add(result);
    }

    private static double clip(AABB moving, AABB fixed, int axis, double amount) {
        int other = (axis + 1) % 3, last = (axis + 2) % 3;
        if (max(moving, other) <= min(fixed, other) || min(moving, other) >= max(fixed, other)
                || max(moving, last) <= min(fixed, last) || min(moving, last) >= max(fixed, last)) return amount;
        if (amount > 0 && max(moving, axis) <= min(fixed, axis))
            return Math.min(amount, Math.max(0, min(fixed, axis) - max(moving, axis) - SKIN));
        if (amount < 0 && min(moving, axis) >= max(fixed, axis))
            return Math.max(amount, Math.min(0, max(fixed, axis) - min(moving, axis) + SKIN));
        return amount;
    }

    private static Vec3 pushOut(Vec3 center, Vec3 half, List<AABB> blocks, Vec3 preferredUp) {
        Vec3 position = center;
        for (int iteration = 0; iteration < 12; iteration++) {
            AABB body = volume(position, half);
            Vec3 correction = null;
            double best = Double.MAX_VALUE;
            for (AABB block : blocks) if (body.intersects(block)) {
                for (int axis = 0; axis < 3; axis++) for (int sign : new int[]{-1, 1}) {
                    double offset = sign > 0 ? max(block, axis) - min(body, axis) + SKIN
                            : min(block, axis) - max(body, axis) - SKIN;
                    double score = Math.abs(offset) + (1 - preferredUp.dot(axisVector(axis, sign))) * 0.015;
                    if (score < best) { best = score; correction = axisVector(axis, offset); }
                }
            }
            if (correction == null) return position;
            position = position.add(correction);
        }
        return position;
    }

    private static double component(Vec3 p, int axis) { return axis == 0 ? p.x : axis == 1 ? p.y : p.z; }
    private static double min(AABB b, int axis) { return axis == 0 ? b.minX : axis == 1 ? b.minY : b.minZ; }
    private static double max(AABB b, int axis) { return axis == 0 ? b.maxX : axis == 1 ? b.maxY : b.maxZ; }
    private static Vec3 axisVector(int axis, double length) {
        return axis == 0 ? new Vec3(length, 0, 0) : axis == 1 ? new Vec3(0, length, 0) : new Vec3(0, 0, length);
    }
}
