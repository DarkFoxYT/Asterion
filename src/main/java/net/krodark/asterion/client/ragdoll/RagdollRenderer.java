package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.krodark.asterion.client.light.HeldItemDynamicLights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.DeadSunEntryCinematic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
    private static final float[][][] HUMANOID_ARMOR_UVS = createHumanoidArmorUvs();
    private RagdollRenderer() { }

    public static void submit(PoseStack poses, LevelRenderState state, SubmitNodeCollector collector) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || DismembermentEngine.INSTANCE.pieces().isEmpty()) return;
        Vec3 camera = state.cameraRenderState.pos;
        List<RigidBodyPiece> bodies = new ArrayList<>();
        List<RigidBodyPiece> grips = new ArrayList<>();
        for (RigidBodyPiece piece : DismembermentEngine.INSTANCE.pieces()) {
            if (piece.position.distanceToSqr(camera) >= 96 * 96) continue;
            if (DismembermentEngine.isGripRegion(piece.region)) grips.add(piece);
            else bodies.add(piece);
        }
        if (client.player != null && client.options.getCameraType().isFirstPerson()
                && !DeadSunEntryCinematic.isActive()
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
            Map<Identifier, List<ArmorDraw>> armorByTexture = new HashMap<>();
            for (RigidBodyPiece body : bodies)
                for (ArmorDraw draw : armorDraws(body))
                    armorByTexture.computeIfAbsent(draw.texture, ignored -> new ArrayList<>()).add(draw);
            armorByTexture.forEach((texture, draws) -> collector.submitCustomGeometry(poses,
                    RenderTypes.armorCutoutNoCull(texture),
                    (pose, vertices) -> draws.forEach(draw -> renderEquipmentBox(pose, vertices, draw))));
            for (RigidBodyPiece grip : grips) submitHeldItem(poses, collector, grip);
        }
        poses.popPose();
    }

    private static void submitHeldItem(PoseStack poses, SubmitNodeCollector collector,
                                       RigidBodyPiece grip) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        LivingEntity living = client.level.getEntity(grip.entityId) instanceof LivingEntity found ? found : null;
        boolean physicalRight = grip.region == 30;
        HumanoidArm physicalArm = physicalRight ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        ItemStack stack = grip.heldItem;
        if (living != null) {
            boolean mainHand = physicalArm == living.getMainArm();
            ItemStack current = mainHand ? living.getMainHandItem() : living.getOffhandItem();
            if (!current.isEmpty()) {
                stack = current;
                grip.heldItem = current.copy();
            }
        }
        if (stack.isEmpty()) return;
        float partial = Mth.clamp(client.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        Vec3 center = grip.previous.lerp(grip.position, partial);
        Quaternionf rotation = new Quaternionf(grip.previousOrientation).slerp(grip.orientation, partial);
        poses.pushPose();
        poses.translate(center.x, center.y, center.z);
        poses.mulPose(rotation);
        poses.scale(0.86F, 0.86F, 0.86F);
        ItemStackRenderState itemState = new ItemStackRenderState();
        ItemDisplayContext context = physicalRight ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (living != null) client.getItemModelResolver().updateForLiving(itemState, stack, context, living);
        else client.getItemModelResolver().updateForTopItem(itemState, stack, context, client.level, null, grip.entityId);
        if (itemState.isEmpty()) { poses.popPose(); return; }
        if (living != null) HeldItemDynamicLights.updateRagdollHand(living, physicalArm, stack, center);
        poses.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poses.mulPose(Axis.YP.rotationDegrees(180.0F));
        itemState.submit(poses, collector, sampleLight(center), OverlayTexture.NO_OVERLAY, 0);
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

    private static List<ArmorDraw> armorDraws(RigidBodyPiece body) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!DismembermentEngine.isAnatomicalRegion(body.region) || minecraft.level == null) return List.of();
        LivingEntity living = minecraft.level.getEntity(body.entityId) instanceof LivingEntity found ? found : null;
        ItemStack head = living == null ? body.headEquipment : living.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = living == null ? body.chestEquipment : living.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = living == null ? body.legEquipment : living.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = living == null ? body.footEquipment : living.getItemBySlot(EquipmentSlot.FEET);
        List<ArmorDraw> draws = new ArrayList<>(4);
        switch (DismembermentEngine.semanticRegion(body.region)) {
            case 0 -> addArmorSlot(draws, body, head, false);
            case 1 -> { if (rendersAsBodyArmor(chest)) addArmorSlot(draws, body, chest, false); addArmorSlot(draws, body, legs, true); }
            case 2, 3 -> { if (rendersAsBodyArmor(chest)) addArmorSlot(draws, body, chest, false); }
            case 4, 5 -> { addArmorSlot(draws, body, legs, true); addArmorSlot(draws, body, feet, false); }
            default -> { }
        }
        return draws;
    }

    private static boolean rendersAsBodyArmor(ItemStack stack) {
        if (stack.isEmpty()) return true;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null || equippable.assetId().isEmpty()
                || !equippable.assetId().get().identifier().getPath().contains("elytra");
    }

    private static void addArmorSlot(List<ArmorDraw> draws, RigidBodyPiece body, ItemStack armor, boolean leggings) {
        Identifier texture = armorTexture(armor, leggings);
        if (texture == null) return;
        double dilation = leggings ? 0.03125 : 0.0625;
        Vec3 shell = body.halfExtents.add(dilation, dilation, dilation);
        float[][] uvs = HUMANOID_ARMOR_UVS[Mth.clamp(DismembermentEngine.semanticRegion(body.region), 0, 5)];
        draws.add(new ArmorDraw(body, texture, shell, armorLayerColor(armor, texture), uvs));
        Identifier overlay = armorOverlayTexture(texture);
        if (overlay != null) draws.add(new ArmorDraw(body, overlay, shell.add(.0008, .0008, .0008), 0xFFFFFFFF, uvs));
    }

    private static Identifier armorTexture(ItemStack stack, boolean leggings) {
        if (stack.isEmpty()) return null;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) return null;
        Identifier asset = equippable.assetId().get().identifier();
        return Identifier.fromNamespaceAndPath(asset.getNamespace(), "textures/entity/equipment/"
                + (leggings ? "humanoid_leggings/" : "humanoid/") + asset.getPath() + ".png");
    }

    private static int armorLayerColor(ItemStack stack, Identifier texture) {
        return texture.getPath().endsWith("/leather.png") ? DyedItemColor.getOrDefault(stack, -6265536) : 0xFFFFFFFF;
    }

    private static Identifier armorOverlayTexture(Identifier texture) {
        if (!texture.getPath().endsWith("/leather.png")) return null;
        return Identifier.fromNamespaceAndPath(texture.getNamespace(),
                texture.getPath().substring(0, texture.getPath().length() - "leather.png".length()) + "leather_overlay.png");
    }

    private static float[][][] createHumanoidArmorUvs() {
        HumanoidModel<HumanoidRenderState> model = new HumanoidModel<>(LayerDefinition.create(
                HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 32).bakeRoot());
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float[][][] result = new float[parts.length][][];
        for (int region = 0; region < parts.length; region++)
            result[region] = DismembermentEngine.uvFaces(parts[region].getRandomCube(RandomSource.create(0xA6E0L + region)));
        return result;
    }

    private static void renderEquipmentBox(PoseStack.Pose pose, VertexConsumer out, ArmorDraw draw) {
        float partial = Mth.clamp(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true), 0, 1);
        Vec3 center = draw.body.previous.lerp(draw.body.position, partial);
        Quaternionf rotation = new Quaternionf(draw.body.previousOrientation).slerp(draw.body.orientation, partial);
        drawBox(pose, out, draw.body, center, rotation, draw.half, draw.uvs, draw.color);
    }

    private record ArmorDraw(RigidBodyPiece body, Identifier texture, Vec3 half, int color, float[][] uvs) { }

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
