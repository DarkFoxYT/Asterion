package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
    private static final Identifier ARMOR_PLATE_TEXTURE = Identifier.parse(
            "minecraft:textures/block/white_concrete.png");
    private static final float[][] FULL_UVS = {
            {0, 0, 1, 0, 1, 1, 0, 1}, {0, 0, 1, 0, 1, 1, 0, 1},
            {0, 0, 1, 0, 1, 1, 0, 1}, {0, 0, 1, 0, 1, 1, 0, 1},
            {0, 0, 1, 0, 1, 1, 0, 1}, {0, 0, 1, 0, 1, 1, 0, 1}
    };
    private RagdollRenderer() { }

    public static void submit(PoseStack poses, LevelRenderState state, SubmitNodeCollector collector) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Vec3 camera = state.cameraRenderState.pos;
        List<RigidBodyPiece> bodies = new ArrayList<>();
        List<RigidBodyPiece> grips = new ArrayList<>();
        for (RigidBodyPiece piece : DismembermentEngine.INSTANCE.pieces()) {
            if (piece.position.distanceToSqr(camera) >= 96 * 96) continue;
            if (DismembermentEngine.isGripRegion(piece.region)) grips.add(piece);
            else bodies.add(piece);
        }
        if (client.player != null && client.options.getCameraType().isFirstPerson()
                && DismembermentEngine.INSTANCE.isPlayerTumbling(client.player.getId()))
            bodies.removeIf(p -> p.entityId == client.player.getId() && p.region == 0);
        if (bodies.isEmpty() && grips.isEmpty()) return;
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        Map<net.minecraft.resources.Identifier, List<RigidBodyPiece>> byTexture = new HashMap<>();
        for (RigidBodyPiece body : bodies) byTexture.computeIfAbsent(body.texture, ignored -> new ArrayList<>()).add(body);
        byTexture.forEach((texture, parts) -> collector.submitCustomGeometry(poses,
                RenderTypes.entityTranslucent(texture, false),
                (pose, vertices) -> parts.forEach(part -> renderBody(pose, vertices, part))));
        if (AsterionConfig.INSTANCE.ragdollEquipment) {
            List<RigidBodyPiece> armored = bodies.stream().filter(RagdollRenderer::hasArmor).toList();
            if (!armored.isEmpty()) collector.submitCustomGeometry(poses,
                    RenderTypes.entityTranslucent(ARMOR_PLATE_TEXTURE, false),
                    (pose, vertices) -> armored.forEach(part -> renderArmor(pose, vertices, part)));
            for (RigidBodyPiece grip : grips) submitHeldItem(poses, collector, grip);
        }
        poses.popPose();
    }

    private static void submitHeldItem(PoseStack poses, SubmitNodeCollector collector,
                                       RigidBodyPiece grip) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getEntity(grip.entityId) instanceof Player player)) return;
        boolean physicalRight = grip.region == 30;
        boolean mainHand = physicalRight == (player.getMainArm() == HumanoidArm.RIGHT);
        ItemStack stack = mainHand ? player.getMainHandItem() : player.getOffhandItem();
        if (stack.isEmpty()) return;
        float partial = Mth.clamp(client.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        Vec3 center = grip.previous.lerp(grip.position, partial);
        Quaternionf rotation = new Quaternionf(grip.previousOrientation).slerp(grip.orientation, partial);
        poses.pushPose();
        poses.translate(center.x, center.y, center.z);
        poses.mulPose(rotation);
        // The grip is a real physics body, so the rendered item follows collisions and angular
        // inertia instead of being reconstructed from the hidden standing player model.
        poses.scale(0.86F, 0.86F, 0.86F);
        ItemStackRenderState itemState = new ItemStackRenderState();
        ItemDisplayContext context = physicalRight ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        client.getItemModelResolver().updateForLiving(itemState, stack, context, player);
        itemState.submit(poses, collector, sampleLight(center), OverlayTexture.NO_OVERLAY,
                player.getId() * 31 + grip.region);
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

    private static boolean hasArmor(RigidBodyPiece body) {
        return !armorFor(body).isEmpty() && DismembermentEngine.isAnatomicalRegion(body.region);
    }

    private static ItemStack armorFor(RigidBodyPiece body) {
        if (body.region == 0) return body.headEquipment;
        if (body.region == 1 || body.region == 2 || body.region == 3
                || body.region == 9 || body.region == 10) return body.chestEquipment;
        if (body.region == 11 || body.region == 12) return body.footEquipment;
        if (body.region == 4 || body.region == 5) return body.legEquipment;
        return ItemStack.EMPTY;
    }

    private static void renderArmor(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body) {
        float partial = Mth.clamp(Minecraft.getInstance().getDeltaTracker()
                .getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        Vec3 center = body.previous.lerp(body.position, partial);
        Quaternionf rotation = new Quaternionf(body.previousOrientation).slerp(body.orientation, partial);
        double plate = body.region == 0 ? 0.055D : 0.038D;
        drawBox(pose, out, body, center, rotation, body.halfExtents.add(plate, plate, plate),
                FULL_UVS, armorColor(armorFor(body)));
    }

    private static int armorColor(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("netherite")) return 0xFF342D3B;
        if (path.contains("diamond")) return 0xFF45D6D0;
        if (path.contains("gold")) return 0xFFFFC83D;
        if (path.contains("iron")) return 0xFFD8D8D8;
        if (path.contains("chain")) return 0xFF8F969E;
        if (path.contains("copper")) return 0xFFC46A42;
        if (path.contains("turtle")) return 0xFF4F9C72;
        if (path.contains("leather")) return 0xFF8A4F32;
        return 0xFF9CA4AD;
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body,
                                Vec3 center, Quaternionf rotation, Vec3 half, float[][] uvs) {
        drawBox(pose, out, body, center, rotation, half, uvs, 0xffffffff);
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body,
                                Vec3 center, Quaternionf rotation, Vec3 half, float[][] uvs,
                                int color) {
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vector3f local = new Vector3f((float)((i & 1) == 0 ? -half.x : half.x),
                    (float)((i & 2) == 0 ? -half.y : half.y), (float)((i & 4) == 0 ? -half.z : half.z));
            rotation.transform(local); c[i] = center.add(local.x, local.y, local.z);
        }
        int light = sampleLight(center);
        face(pose,out,c,0,4,6,2,color,uvs[0],light); face(pose,out,c,1,3,7,5,color,uvs[1],light);
        face(pose,out,c,0,1,5,4,color,uvs[2],light); face(pose,out,c,2,6,7,3,color,uvs[3],light);
        face(pose,out,c,0,2,3,1,color,uvs[4],light); face(pose,out,c,4,5,7,6,color,uvs[5],light);
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
