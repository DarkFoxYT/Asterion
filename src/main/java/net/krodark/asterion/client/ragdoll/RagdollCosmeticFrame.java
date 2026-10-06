package net.krodark.asterion.client.ragdoll;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Map Essential's model-space cosmetic submission to a single physical body part. */
final class RagdollCosmeticFrame {
    static Matrix4f attachment(Vec3 center, Quaternionf rotation, float scale, Matrix4f source) {
        return new Matrix4f().translation((float) center.x, (float) center.y, (float) center.z)
                .rotate(rotation).scale(scale).mul(new Matrix4f(source).invert()).translate(0, -1.5F, 0);
    }
}
