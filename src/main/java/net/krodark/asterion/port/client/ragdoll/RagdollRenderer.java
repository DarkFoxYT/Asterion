package net.krodark.asterion.port.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.port.client.PortDeadSunEntryCinematic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RagdollRenderer {
    private static final float[][][] HUMANOID_ARMOR_UVS = createHumanoidArmorUvs();
    private RagdollRenderer() { }

    public static void submit(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        Minecraft client=Minecraft.getInstance();
        if(client.level==null) return;
        Vec3 camera=context.camera().getPosition();
        PoseStack poses=context.matrixStack();
        var buffers=context.consumers();
        if(poses==null || buffers==null) return;
        poses.pushPose(); poses.translate(-camera.x,-camera.y,-camera.z);
        for(RigidBodyPiece body:DismembermentEngine.INSTANCE.pieces()) {
            if(body.position.distanceToSqr(camera)>=96*96) continue;
            if(DismembermentEngine.isGripRegion(body.region)) {
                if(AsterionConfig.INSTANCE.ragdollEquipment) submitHeldItem(poses,buffers,body);
                continue;
            }
            double radius=body.halfExtents.length()+.064;
            if(context.frustum()!=null && !context.frustum().isVisible(new AABB(body.position,body.position).inflate(radius))) continue;
            var textures=RagdollTextureCompatibility.resolve(client.level.getEntity(body.entityId),body.texture,body.playerBody);
            renderBody(poses.last(),buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(textures.base(),false)),body);
            if(textures.emissive()!=null) renderBody(poses.last(),buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(textures.emissive())),body,true);
            if(AsterionConfig.INSTANCE.ragdollEquipment) for(ArmorDraw draw:armorDraws(body))
                renderEquipmentBox(poses.last(),buffers.getBuffer(net.minecraft.client.renderer.RenderType.armorCutoutNoCull(draw.texture)),draw);
        }
        poses.popPose();
    }

    private static void submitHeldItem(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers,
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
        float partial = Mth.clamp(client.getTimer().getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        Vec3 center = DismembermentEngine.INSTANCE.renderCenter(grip, partial);
        Quaternionf rotation = new Quaternionf(grip.previousOrientation).slerp(grip.orientation, partial);
        poses.pushPose();
        poses.translate(center.x, center.y, center.z);
        poses.mulPose(rotation);
        poses.scale(0.86F, 0.86F, 0.86F);
        ItemDisplayContext context = physicalRight ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        poses.mulPose(Axis.XP.rotationDegrees(-90)); poses.mulPose(Axis.YP.rotationDegrees(180));
        client.getItemRenderer().renderStatic(living,stack,context,!physicalRight,poses,buffers,client.level,sampleLight(center),OverlayTexture.NO_OVERLAY,grip.entityId);
        poses.popPose();
    }

    private static void renderBody(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body) {
        renderBody(pose, out, body, false);
    }

    private static void renderBody(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body, boolean emissive) {
        float partial = Mth.clamp(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true), 0, 1);
        Vec3 center = DismembermentEngine.INSTANCE.renderCenter(body, partial);
        Quaternionf rotation = new Quaternionf(body.previousOrientation).slerp(body.orientation, partial);
        if (!body.modelBoxes.isEmpty()) {
            PoseStack modelPose = new PoseStack();
            RagdollCompat.mulMatrix(modelPose,pose.pose());
            modelPose.translate(center.x, center.y, center.z);
            modelPose.mulPose(rotation);
            int light = emissive ? 0x00f000f0 : sampleLight(center);
            for (var box : body.modelBoxes) {
                if (box.overlay() && !outerLayerVisible(body)) continue;
                modelPose.pushPose();
                RagdollCompat.mulMatrix(modelPose,box.transform());
                //? if >=1.20.5 {
                box.cube().compile(modelPose.last(), out, light, OverlayTexture.NO_OVERLAY, -1);
                //?} else {
                /*box.cube().compile(modelPose.last(),out,light,OverlayTexture.NO_OVERLAY,1,1,1,1);
                *///?}
                modelPose.popPose();
            }
            return;
        }
        int light = emissive ? 0x00f000f0 : sampleLight(center);
        drawBox(pose, out, body, center, rotation, body.halfExtents, body.faceUvs, -1, light);
        if (body.overlayFaceUvs != null && outerLayerVisible(body)) {
            double dilation = body.region == 0 ? Math.min(.03125, body.halfExtents.x * .13)
                    : Math.min(.015625, Math.min(body.halfExtents.x, body.halfExtents.z) * .12);
            drawBox(pose, out, body, center, rotation, body.halfExtents.add(dilation, dilation, dilation), body.overlayFaceUvs, -1, light);
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
        return !(stack.getItem() instanceof net.minecraft.world.item.ElytraItem);
    }

    private static void addArmorSlot(List<ArmorDraw> draws, RigidBodyPiece body, ItemStack armor, boolean leggings) {
        ResourceLocation texture = armorTexture(armor, leggings);
        if (texture == null) return;
        double dilation = leggings ? 0.03125 : 0.0625;
        Vec3 shell = body.halfExtents.add(dilation, dilation, dilation);
        float[][] uvs = HUMANOID_ARMOR_UVS[Mth.clamp(DismembermentEngine.semanticRegion(body.region), 0, 5)];
        draws.add(new ArmorDraw(body, texture, shell, armorLayerColor(armor, texture), uvs));
        ResourceLocation overlay = armorOverlayTexture(texture);
        if (overlay != null) draws.add(new ArmorDraw(body, overlay, shell.add(.0008, .0008, .0008), 0xFFFFFFFF, uvs));
    }

    private static ResourceLocation armorTexture(ItemStack stack, boolean leggings) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor)) return null;
        //? if >=1.20.5 {
        var material=armor.getMaterial().value();
        if(material.layers().isEmpty()) return null;
        return material.layers().get(0).texture(leggings);
        //?} else {
        /*String name=armor.getMaterial().getName();
        ResourceLocation material=ResourceLocation.tryParse(name);
        return new ResourceLocation(material.getNamespace(),"textures/models/armor/"+material.getPath()+"_layer_"+(leggings?2:1)+".png");
        *///?}

    }

    private static int armorLayerColor(ItemStack stack, ResourceLocation texture) {
        //? if >=1.20.5 {
        return net.minecraft.world.item.component.DyedItemColor.getOrDefault(stack,0xFFFFFFFF);
        //?} else {
        /*return stack.getItem() instanceof net.minecraft.world.item.DyeableLeatherItem dye ? 0xFF000000|dye.getColor(stack) : 0xFFFFFFFF;
        *///?}

    }

    private static ResourceLocation armorOverlayTexture(ResourceLocation texture) {
        return texture.getPath().contains("leather_layer_") ? ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),texture.getPath().replace(".png","_overlay.png")) : null;
    }

    private static float[][][] createHumanoidArmorUvs() {
        HumanoidModel<LivingEntity> model = new HumanoidModel<>(LayerDefinition.create(
                HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 32).bakeRoot());
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float[][][] result = new float[parts.length][][];
        for (int region = 0; region < parts.length; region++)
            result[region] = DismembermentEngine.uvFaces(parts[region].getRandomCube(RandomSource.create(0xA6E0L + region)));
        return result;
    }

    private static void renderEquipmentBox(PoseStack.Pose pose, VertexConsumer out, ArmorDraw draw) {
        float partial = Mth.clamp(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true), 0, 1);
        Vec3 center = DismembermentEngine.INSTANCE.renderCenter(draw.body, partial);
        Quaternionf rotation = new Quaternionf(draw.body.previousOrientation).slerp(draw.body.orientation, partial);
        drawBox(pose, out, draw.body, center, rotation, draw.half, draw.uvs, draw.color);
    }

    private record ArmorDraw(RigidBodyPiece body, ResourceLocation texture, Vec3 half, int color, float[][] uvs) { }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body,
                                Vec3 center, Quaternionf rotation, Vec3 half, float[][] uvs) {
        drawBox(pose, out, body, center, rotation, half, uvs, 0xffffffff);
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body,
                                Vec3 center, Quaternionf rotation, Vec3 half, float[][] uvs,
                                int color) {
        drawBox(pose, out, body, center, rotation, half, uvs, color, sampleLight(center));
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer out, RigidBodyPiece body,
                                Vec3 center, Quaternionf rotation, Vec3 half, float[][] uvs,
                                int color, int light) {
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vector3f local = new Vector3f((float)((i & 1) == 0 ? -half.x : half.x),
                    (float)((i & 2) == 0 ? -half.y : half.y), (float)((i & 4) == 0 ? -half.z : half.z));
            rotation.transform(local); c[i] = center.add(local.x, local.y, local.z);
        }
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
        //? if >=1.20.5 {
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,(float)n.x,(float)n.y,(float)n.z);
        //?} else {
        /*out.vertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
        *///?}
    }
    private static int sampleLight(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0x00f000f0 : LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
    }
}
