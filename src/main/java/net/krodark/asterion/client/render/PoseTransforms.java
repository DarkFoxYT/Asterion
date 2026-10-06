package net.krodark.asterion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public final class PoseTransforms {
    private PoseTransforms() { }
    public static void apply(PoseStack poses, Matrix4fc matrix) { poses.mulPose(matrix); }
    public static void apply(PoseStack poses, Quaternionfc rotation) {
        //? if >=26.3 {
        /*poses.rotate(rotation);
        *///?} else {
        poses.mulPose(rotation);
        //?}
    }
}
