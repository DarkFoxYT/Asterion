package net.krodark.labyrinth.client.lightning;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.labyrinth.LabyrinthConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Camera-facing, multi-layer fractal lightning adapted from the supplied Amalgam renderer. */
public final class BetterLightningRenderer {
    private BetterLightningRenderer() {
    }

    public static void draw(VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                            Vec3 start, Vec3 end, float charge, long frame) {
        double distance = start.distanceTo(end);
        if (distance < 0.02D) return;
        Random random = new Random(mix(frame ^ Double.doubleToLongBits(distance)));
        int subdivisions = distance > 120.0D ? 6 : distance > 48.0D ? 5 : 4;
        int branchCount = Mth.clamp(3 + Mth.floor(distance / 22.0D), 3, 11);
        double roughness = Mth.clamp(0.48D + distance * 0.0022D, 0.48D, 0.84D);
        Vec3[] spine = BoltBuilder.buildMainPath(start, end, subdivisions, roughness, random);
        BoltBuilder.Branch[] branches = BoltBuilder.buildBranches(spine, branchCount, random);
        // The channel grows subtly with length, but remains much slimmer than the old fixed-width bolt.
        float distanceWidth = (float) Mth.clamp(0.042D + Math.sqrt(distance) * 0.0052D, 0.045D, 0.115D);
        float width = distanceWidth * (0.66F + charge * 0.34F);
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        int coreR = color(config.deadSunCoreR);
        int coreG = color(config.deadSunCoreG);
        int coreB = color(config.deadSunCoreB);
        int coronaR = color(config.deadSunCoronaR);
        int coronaG = color(config.deadSunCoronaG);
        int coronaB = color(config.deadSunCoronaB);

        drawPath(out, pose, camera, spine, width * 1.58F, coronaR, coronaG, coronaB, 64, true, frame, 0);
        drawPath(out, pose, camera, spine, width, coreR, coreG, coreB, 220, true, frame, 1);
        drawPath(out, pose, camera, spine, width * 0.28F, 255, 238, 188, 255, true, frame, 2);
        for (BoltBuilder.Branch branch : branches) {
            drawPath(out, pose, camera, branch.points(), width * 0.52F * branch.strength(),
                    coronaR, coronaG, coronaB, 126, false, frame, 3);
            drawPath(out, pose, camera, branch.points(), width * 0.13F * branch.strength(),
                    coreR, coreG, coreB, 238, false, frame, 4);
        }
    }

    private static void drawPath(VertexConsumer out, PoseStack.Pose pose, Vec3 camera,
                                 Vec3[] points, float width, int red, int green, int blue,
                                 int alpha, boolean crossed, long frame, int layer) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 a = points[i];
            Vec3 b = points[i + 1];
            Vec3 tangent = b.subtract(a);
            if (tangent.lengthSqr() < 1.0E-8D) continue;
            tangent = tangent.normalize();
            Vec3 toCamera = camera.subtract(a.add(b).scale(0.5D));
            if (toCamera.lengthSqr() < 1.0E-8D) toCamera = new Vec3(0, 1, 0);
            Vec3 side = toCamera.normalize().cross(tangent);
            if (side.lengthSqr() < 1.0E-8D) side = perpendicular(tangent);
            side = side.normalize();
            Vec3 second = tangent.cross(side).normalize();
            float taperA = taper(i / (float) (points.length - 1));
            float taperB = taper((i + 1) / (float) (points.length - 1));
            long noise = mix(frame + i * 0x9E3779B97F4A7C15L + layer * 31L);
            float flash = 0.88F + ((noise >>> 56) & 0xFF) / 255.0F * 0.12F;
            int r = Math.min(255, Math.round(red * flash));
            int g = Math.min(255, Math.round(green * flash));
            int bl = Math.min(255, Math.round(blue * flash));
            ribbon(out, pose, a, b, side, width * taperA, width * taperB, r, g, bl, alpha);
            if (crossed) ribbon(out, pose, a, b, second, width * taperA * 0.78F,
                    width * taperB * 0.78F, r, g, bl, Math.round(alpha * 0.86F));
        }
    }

    private static void ribbon(VertexConsumer out, PoseStack.Pose pose, Vec3 a, Vec3 b,
                               Vec3 side, float widthA, float widthB,
                               int red, int green, int blue, int alpha) {
        Vec3 ao = a.add(side.scale(widthA));
        Vec3 ai = a.subtract(side.scale(widthA));
        Vec3 bo = b.add(side.scale(widthB));
        Vec3 bi = b.subtract(side.scale(widthB));
        vertex(out, pose, ao, red, green, blue, alpha);
        vertex(out, pose, ai, red, green, blue, alpha);
        vertex(out, pose, bi, red, green, blue, alpha);
        vertex(out, pose, bo, red, green, blue, alpha);
        vertex(out, pose, bo, red, green, blue, alpha);
        vertex(out, pose, bi, red, green, blue, alpha);
        vertex(out, pose, ai, red, green, blue, alpha);
        vertex(out, pose, ao, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 point,
                               int red, int green, int blue, int alpha) {
        out.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(red, green, blue, alpha);
    }

    private static int color(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }

    private static float taper(float progress) {
        // Fine terminals with a readable middle, instead of swelling into the target's head/body.
        return 0.34F + (float) Math.sin(progress * Math.PI) * 0.66F;
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 seed = Math.abs(direction.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return direction.cross(seed).normalize();
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
