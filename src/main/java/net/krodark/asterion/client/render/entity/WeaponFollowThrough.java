package net.krodark.asterion.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;

/** Small damped wrist deflection driven by the animated hand's angular velocity. */
final class WeaponFollowThrough {
    private static final Map<Long, Spring> SPRINGS = new HashMap<>();
    private static final class Spring {
        final Quaternionf hand = new Quaternionf();
        final Vector3f angle = new Vector3f(), velocity = new Vector3f();
        double age = Double.NaN;
    }
    static void apply(PoseStack poses, int owner, int side, double age, boolean heavy) {
        if (owner < 0) return;
        if (SPRINGS.size() > 256) SPRINGS.entrySet().removeIf(e -> age - e.getValue().age > 100);
        Spring spring = SPRINGS.computeIfAbsent(((long)owner << 2) | side, ignored -> new Spring());
        Quaternionf hand = poses.last().pose().getUnnormalizedRotation(new Quaternionf()).normalize();
        double elapsed = age - spring.age;
        if (!Double.isFinite(elapsed) || elapsed < 0 || elapsed > 4) {
            spring.hand.set(hand); spring.angle.zero(); spring.velocity.zero(); spring.age = age;
        } else if (elapsed > .0001) {
            Quaternionf delta = new Quaternionf(spring.hand).conjugate().mul(hand).normalize();
            if (delta.w < 0) delta.mul(-1);
            Vector3f target = new Vector3f(delta.x, delta.y, delta.z)
                    .mul((float)(-(heavy ? .75 : .48) / elapsed));
            if (target.length() > .22F) target.normalize().mul(.22F);
            int steps = Math.max(1, (int)Math.ceil(elapsed / .2));
            float dt = (float)elapsed / steps;
            for (int i = 0; i < steps; i++) {
                spring.velocity.add(new Vector3f(target).sub(spring.angle).mul(.55F * dt));
                spring.velocity.mul((float)Math.exp(-1.15 * dt));
                spring.angle.fma(dt, spring.velocity);
            }
            if (spring.angle.length() > .24F) spring.angle.normalize().mul(.24F);
            spring.hand.set(hand); spring.age = age;
        }
        net.krodark.asterion.client.render.PoseTransforms.apply(poses, new Quaternionf().rotationXYZ(spring.angle.x, spring.angle.y, spring.angle.z));
    }
}
