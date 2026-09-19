package net.krodark.asterion.port.client.ragdoll;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Adapts model/skin snapshots while keeping the main branch's physical solver unchanged. */
public final class RagdollCompat {
    static void mulMatrix(com.mojang.blaze3d.vertex.PoseStack poses,org.joml.Matrix4f matrix) {
        //? if >=1.20.5 {
        poses.mulPose(matrix);
        //?} else {
        /*poses.mulPoseMatrix(matrix);
        *///?}
    }
    static com.mojang.blaze3d.vertex.PoseStack.Pose copyPose(com.mojang.blaze3d.vertex.PoseStack.Pose pose) {
        var copy=new com.mojang.blaze3d.vertex.PoseStack();
        copy.last().pose().set(pose.pose()); copy.last().normal().set(pose.normal());
        return copy.last();
    }
    record Skin(ResourceLocation texture, ResourceLocation cape, String model) { }
    static Skin skin(AbstractClientPlayer player) {
        //? if >=1.20.5 {
        var skin=player.getSkin();
        return new Skin(skin.texture(),skin.capeTexture(),skin.model().id());
        //?} else {
        /*return new Skin(player.getSkinTextureLocation(),player.getCloakTextureLocation(),player.getModelName());
        *///?}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    static void setup(EntityModel model, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        model.prepareMobModel(living,living.walkAnimation.position(),living.walkAnimation.speed(),1);
        model.setupAnim(living,living.walkAnimation.position(),living.walkAnimation.speed(),living.tickCount,living.getYHeadRot()-living.yBodyRot,living.getXRot());
    }
    static ModelPart root(EntityModel<?> model) {
        if (model instanceof HierarchicalModel<?> hierarchical) return hierarchical.root();
        Map<String,ModelPart> children=new LinkedHashMap<>();
        if(model instanceof HumanoidModel<?> humanoid) {
            children.put("head",humanoid.head);children.put("hat",humanoid.hat);children.put("body",humanoid.body);
            children.put("right_arm",humanoid.rightArm);children.put("left_arm",humanoid.leftArm);
            children.put("right_leg",humanoid.rightLeg);children.put("left_leg",humanoid.leftLeg);
            if(model instanceof PlayerModel<?> player) {
                children.put("jacket",player.jacket);children.put("right_sleeve",player.rightSleeve);children.put("left_sleeve",player.leftSleeve);
                children.put("right_pants",player.rightPants);children.put("left_pants",player.leftPants);
            }
        } else {
            // Models before render states expose their root parts as fields.
            // Read the actual objects, preserving custom geometry and UVs.
            for(Class<?> type=model.getClass();type!=Object.class;type=type.getSuperclass())
                for(var field:type.getDeclaredFields()) if(ModelPart.class.isAssignableFrom(field.getType())) try {
                    if(field.trySetAccessible()) { var part=(ModelPart)field.get(model); if(part!=null) children.putIfAbsent(field.getName(),part); }
                } catch(IllegalAccessException ignored) { }
        }
        return new ModelPart(List.of(),children);
    }
    private static final List<java.lang.reflect.Method> PLAYBACK=findPlayback();
    private static List<java.lang.reflect.Method> findPlayback() {
        var methods=new ArrayList<java.lang.reflect.Method>();
        for(String name:new String[]{"com.moulberry.flashback.Flashback","com.replaymod.replay.ReplayModReplay"}) try {
            var type=Class.forName(name,false,RagdollCompat.class.getClassLoader());
            for(String method:new String[]{"isInReplay","isExporting","isReplaying","isPlayback"}) try {
                var probe=type.getMethod(method);
                if(java.lang.reflect.Modifier.isStatic(probe.getModifiers()) && probe.getReturnType()==boolean.class) methods.add(probe);
            } catch(NoSuchMethodException ignored) { }
        } catch(ClassNotFoundException | LinkageError ignored) { }
        return List.copyOf(methods);
    }
    public static boolean isPlayback(Minecraft client) {
        if(client.player!=null && client.gameRenderer.getMainCamera().getEntity()!=null && client.gameRenderer.getMainCamera().getEntity()!=client.player) return true;
        for(var probe:PLAYBACK) try { if((boolean)probe.invoke(null)) return true; } catch(ReflectiveOperationException ignored) { }
        return false;
    }
}
