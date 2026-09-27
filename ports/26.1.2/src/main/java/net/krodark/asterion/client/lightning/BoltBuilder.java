package net.krodark.asterion.client.lightning;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class BoltBuilder {
    private BoltBuilder() {
    }

    public record Branch(Vec3[] points, float strength) {
    }

    public static Vec3[] buildMainPath(Vec3 start, Vec3 end, int depth, double roughness, Random random) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        if (length < 1.0E-4D) return new Vec3[]{start, end};

        Vec3 forward = direction.scale(1.0D / length);
        Vec3 side = perpendicular(forward);
        Vec3 up = forward.cross(side).normalize();
        List<Vec3> points = new ArrayList<>();
        points.add(start);
        points.add(end);
        for (int pass = 0; pass < depth; pass++) {
            List<Vec3> next = new ArrayList<>(points.size() * 2);
            double amplitude = Math.min(3.4D, roughness * Math.sqrt(length) * Math.pow(0.52D, pass));
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = points.get(i);
                Vec3 b = points.get(i + 1);
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double offset = (random.nextDouble() * 2.0D - 1.0D) * amplitude;
                Vec3 radial = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                next.add(a);
                next.add(a.add(b).scale(0.5D).add(radial.scale(offset)));
            }
            next.add(end);
            points = next;
        }
        points = smooth(points);
        points.set(0, start);
        points.set(points.size() - 1, end);
        return points.toArray(Vec3[]::new);
    }

    public static Branch[] buildBranches(Vec3[] spine, int count, Random random) {
        if (spine.length < 6 || count <= 0) return new Branch[0];
        List<Branch> branches = new ArrayList<>(count);
        double mainLength = spine[0].distanceTo(spine[spine.length - 1]);
        int quality = net.krodark.asterion.AsterionConfig.INSTANCE.cinematicQuality;
        double maxBranchLength = Math.min(quality >= 2 ? 14.0D : 9.0D,
                2.8D + Math.sqrt(mainLength) * (quality >= 2 ? 0.58D : 0.40D));
        for (int i = 0; i < count; i++) {
            int anchorIndex = 2 + random.nextInt(spine.length - 4);
            Vec3 start = spine[anchorIndex];
            Vec3 tangent = spine[anchorIndex + 1].subtract(spine[anchorIndex - 1]).normalize();
            Vec3 side = perpendicular(tangent);
            Vec3 up = tangent.cross(side).normalize();
            double length = 0.7D + random.nextDouble() * maxBranchLength;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 radial = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 end = start.add(tangent.scale(length * (0.2D + random.nextDouble() * 0.35D)))
                    .add(radial.scale(length));
            List<Vec3> points = new ArrayList<>();
            points.add(start);
            points.add(end);
            for (int pass = 0; pass < 3; pass++) {
                List<Vec3> next = new ArrayList<>(points.size() * 2);
                double amplitude = length * 0.22D * Math.pow(0.55D, pass);
                for (int segment = 0; segment < points.size() - 1; segment++) {
                    Vec3 a = points.get(segment);
                    Vec3 b = points.get(segment + 1);
                    Vec3 jitter = perpendicular(b.subtract(a).normalize())
                            .scale((random.nextDouble() * 2.0D - 1.0D) * amplitude);
                    next.add(a);
                    next.add(a.add(b).scale(0.5D).add(jitter));
                }
                next.add(end);
                points = next;
            }
            float progress = anchorIndex / (float) (spine.length - 1);
            branches.add(new Branch(points.toArray(Vec3[]::new),
                    Mth.clamp(1.0F - progress * 0.6F, 0.32F, 1.0F)));
        }
        return branches.toArray(Branch[]::new);
    }

    private static List<Vec3> smooth(List<Vec3> input) {
        List<Vec3> result = new ArrayList<>(input.size());
        result.add(input.getFirst());
        for (int i = 1; i < input.size() - 1; i++) {
            result.add(input.get(i - 1).scale(0.16D).add(input.get(i).scale(0.68D))
                    .add(input.get(i + 1).scale(0.16D)));
        }
        result.add(input.getLast());
        return result;
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 seed = Math.abs(direction.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 result = direction.cross(seed);
        return result.lengthSqr() < 1.0E-8D ? new Vec3(1, 0, 0) : result.normalize();
    }
}
