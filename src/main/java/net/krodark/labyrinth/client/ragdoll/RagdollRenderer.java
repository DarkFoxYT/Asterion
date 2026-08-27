package net.krodark.labyrinth.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RagdollRenderer {
    private RagdollRenderer() { }

    public static void submit(PoseStack poses, LevelRenderState state, SubmitNodeCollector collector) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Vec3 camera = state.cameraRenderState.pos;
        List<RigidBodyPiece> bodies = new ArrayList<>();
        for (RigidBodyPiece piece : DismembermentEngine.INSTANCE.pieces())
            if (!DismembermentEngine.isGripRegion(piece.region)
                    && piece.position.distanceToSqr(camera) < 96 * 96) bodies.add(piece);
        if (client.player != null && client.options.getCameraType().isFirstPerson()
                && DismembermentEngine.INSTANCE.isPlayerTumbling(client.player.getId()))
            bodies.removeIf(p -> p.entityId == client.player.getId() && p.region == 0);
        if (bodies.isEmpty()) return;
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        Map<net.minecraft.resources.Identifier, List<RigidBodyPiece>> byTexture = new HashMap<>();
        for (RigidBodyPiece body : bodies) byTexture.computeIfAbsent(body.texture, ignored -> new ArrayList<>()).add(body);
        byTexture.forEach((texture, parts) -> collector.submitCustomGeometry(poses,
                RenderTypes.entityTranslucent(texture, false),
                (pose, vertices) -> parts.forEach(part -> renderBody(pose, vertices, part))));
        poses.popPose();
    }

    private static void renderBody(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body) {
        float partial = Mth.clamp(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true), 0, 1);
        Vec3 center = body.previous.lerp(body.position, partial);
        Quaternionf rotation = new Quaternionf(body.previousOrientation).slerp(body.orientation, partial);
        drawBox(pose, out, body, center, rotation, body.halfExtents, body.faceUvs);
        if (body.overlayFaceUvs != null && outerLayerVisible(body)) {
            double dilation = body.region == 0 ? Math.min(.03125, body.halfExtents.x * .13)
                    : Math.min(.015625, Math.min(body.halfExtents.x, body.halfExtents.z) * .12);
            drawBox(pose, out, body, center, rotation, body.halfExtents.add(dilation, dilation, dilation), body.overlayFaceUvs);
        }
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body,
                                Vec3 center, Quaternionf rotation, Vec3 half, float[][] uvs) {
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vector3f local = new Vector3f((float)((i & 1) == 0 ? -half.x : half.x),
                    (float)((i & 2) == 0 ? -half.y : half.y), (float)((i & 4) == 0 ? -half.z : half.z));
            rotation.transform(local); c[i] = center.add(local.x, local.y, local.z);
        }
        int light = sampleLight(center), white = 0xffffffff;
        face(pose,out,c,0,4,6,2,white,uvs[0],light); face(pose,out,c,1,3,7,5,white,uvs[1],light);
        face(pose,out,c,0,1,5,4,white,uvs[2],light); face(pose,out,c,2,6,7,3,white,uvs[3],light);
        face(pose,out,c,0,2,3,1,white,uvs[4],light); face(pose,out,c,4,5,7,6,white,uvs[5],light);
    }

    private static boolean outerLayerVisible(RigidBodyPiece body) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(body.entityId) instanceof Player player)) return false;
        return player.isModelPartShown(switch (DismembermentEngine.semanticRegion(body.region)) {
            case 0 -> PlayerModelPart.HAT; case 2 -> PlayerModelPart.RIGHT_SLEEVE;
            case 3 -> PlayerModelPart.LEFT_SLEEVE; case 4 -> PlayerModelPart.RIGHT_PANTS_LEG;
            case 5 -> PlayerModelPart.LEFT_PANTS_LEG; default -> PlayerModelPart.JACKET;
        });
    }

    private static void face(PoseStack.Pose pose, VertexConsumer out, Vec3[] c, int a, int b, int d, int e,
                             int color, float[] uv, int light) {
        Vec3 n = RagdollMath.safeNormalize(c[b].subtract(c[a]).cross(c[e].subtract(c[a])), new Vec3(0,1,0));
        vertex(pose,out,c[a],n,color,uv[0],uv[1],light); vertex(pose,out,c[b],n,color,uv[2],uv[3],light);
        vertex(pose,out,c[d],n,color,uv[4],uv[5],light); vertex(pose,out,c[e],n,color,uv[6],uv[7],light);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 p, Vec3 n, int color,
                               float u, float v, int light) {
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,(float)n.x,(float)n.y,(float)n.z);
    }
    private static int sampleLight(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0x00f000f0 : LevelRenderer.getLightCoords(mc.level, BlockPos.containing(p));
    }
}
