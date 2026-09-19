package net.krodark.asterion.port.client;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import java.util.*;
import static net.krodark.asterion.port.client.PortMinotaurPose.Key.*;
final class PortMinotaurPose {
    enum Key { REMOVED_PARTS,HARVESTED,LOOK_YAW,LOOK_PITCH,EYE_TINT,GRAB_WEIGHT,GRAB_YAW,GRAB_PITCH,GRAB_EXTENSION,GRAB_ARM,HELD_PLAYER,AXE_ACTION,AUTHORED_POSE,IDLE_PHASE,IDLE_WEIGHT,HORN_WEIGHT,RAGE_WEIGHT,ATTACK_TICKS,COLLAPSE,DOOR_ENTRY }
    private static final Map<MinotaurEntity,State> STATES=new WeakHashMap<>();
    static final class State {
        final Map<Key,Object> data=new EnumMap<>(Key.class);
        final LookPose look=new LookPose(); final GrabPose grab=new GrabPose();
        final Map<String,float[]> last=new HashMap<>(),from=new HashMap<>();
        int animation=-1; double start,age=-1;
        void addGeckolibData(Key key,Object value){data.put(key,value);}
        @SuppressWarnings("unchecked") <T> T getOrDefaultGeckolibData(Key key,T fallback){return (T)data.getOrDefault(key,fallback);}
    }
    static boolean attackCue(MinotaurEntity boss) {
        return !boss.isDefeatedBoss() && !boss.isHarvested() && boss.doorEntryTicks() <= 0
                && (boss.bossAttackAnimationTicks() > 0 || boss.animationState() == MinotaurEntity.AnimationState.WARNING
                || boss.animationState() == MinotaurEntity.AnimationState.CHARGE_RUN);
    }
    static int eyeTint(MinotaurEntity boss) {
        State state=STATES.get(boss);
        int tint=state==null?0xFFD8FFFF:state.getOrDefaultGeckolibData(EYE_TINT,0xFFD8FFFF);
        if (attackCue(boss)) tint = 0xFF55FF66;
        float strength=boss.collapseAnimationTicks()>45 && boss.collapseAnimationTicks()<118?.04F:boss.doorEntryTicks()>0?1F:PortEmissiveConfig.minotaurEyeStrength();
        return (Math.round((tint>>>24)*strength)<<24)|(tint&0xFFFFFF);
    }
    static void apply(MinotaurEntity minotaur,Collection<?> model,float partialTick) {
        State state=STATES.computeIfAbsent(minotaur,key->new State()); Bones bones=new Bones(model.stream().filter(GeoBone.class::isInstance).map(GeoBone.class::cast).toList());
        try {
        applyPose(minotaur, state, bones, partialTick);
        } finally {
            // Procedural changes are render-only. Leaving these flags set makes
            // GeckoLib skip resetting unanimated channels on the next frame.
            for (GeoBone bone : bones.all) bone.resetStateChanges();
        }
    }
    private static void applyPose(MinotaurEntity minotaur, State state, Bones bones, float partialTick) {
        {
        float bodyYaw = Mth.rotLerp(partialTick, minotaur.yBodyRotO, minotaur.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, minotaur.yHeadRotO, minotaur.yHeadRot);
        float targetYaw = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -72F, 72F);
        float targetPitch = Mth.clamp(Mth.lerp(partialTick, minotaur.xRotO, minotaur.getXRot()), -34F, 42F);

        LookPose pose = state.look;
        float frameTicks = state.age < 0 ? 1 : Math.max(0F, Math.min(2F, (float)(minotaur.tickCount+partialTick-state.age)));
        float blend = 1.0F - (float)Math.pow(0.72D, frameTicks);
        pose.yaw += Mth.wrapDegrees(targetYaw - pose.yaw) * blend;
        pose.pitch += (targetPitch - pose.pitch) * blend;
        float desiredIdle = minotaur.animationState() == MinotaurEntity.AnimationState.IDLE
                && !minotaur.isPerformingReach() ? 1.0F : 0.0F;
        pose.idleWeight += (desiredIdle - pose.idleWeight)
                * (1.0F - (float)Math.pow(0.82D, frameTicks));
        state.addGeckolibData(LOOK_YAW, pose.yaw * Mth.DEG_TO_RAD);
        state.addGeckolibData(LOOK_PITCH, pose.pitch * Mth.DEG_TO_RAD);
        state.addGeckolibData(IDLE_PHASE, (minotaur.tickCount + partialTick) * 0.055F);
        state.addGeckolibData(IDLE_WEIGHT, pose.idleWeight);


        state.addGeckolibData(AUTHORED_POSE, !minotaur.isPerformingGrab());
        state.addGeckolibData(HORN_WEIGHT, minotaur.isSpineCharging() ? 1.0F : 0.0F);
        state.addGeckolibData(RAGE_WEIGHT, minotaur.rage() / 12.0F);
        state.addGeckolibData(ATTACK_TICKS, minotaur.bossAttackAnimationTicks());
        state.addGeckolibData(COLLAPSE, minotaur.collapseAnimationTicks() > 0 ? minotaur.collapseAnimationTicks() + partialTick : 0F);
        state.addGeckolibData(DOOR_ENTRY, minotaur.doorEntryTicks() > 0 ? minotaur.doorEntryTicks() - 1 + partialTick : -1F);


        GrabPose grab = state.grab;
        float grabTicks = minotaur.grabAttackTicks() + partialTick;
        float desiredGrab = 0.0F;
        float grabYaw = 0.0F;
        float grabPitch = 0.0F;
        float grabExtension = 0.65F;
        int liveArm = minotaur.reachArmSide();
        if (liveArm != 0) grab.arm = liveArm;
        if (minotaur.isPerformingReach() && !minotaur.isChainGrappleActive() && liveArm != 0) {
            desiredGrab = minotaur.isPerformingGrab()
                    ? grabTicks < 11 ? smoother(grabTicks / 11.0F)
                    : grabTicks < 49 ? 1.0F : 1.0F - smoother((grabTicks - 49) / 12.0F)
                    : grabTicks < 12 ? smoother(grabTicks / 12.0F)
                    : grabTicks < 43 ? 1.0F : 1.0F - smoother((grabTicks - 43) / 6.0F);
            Entity targetEntity = minotaur.level().getEntity(minotaur.grabTargetEntityId());
            if (targetEntity != null && minotaur.heldPlayerId() == targetEntity.getId()) {
                // The held player's position follows this arm. Aiming back at it
                // would create a feedback loop during the grab and throw.
                grabYaw = grab.yaw;
                grabPitch = grab.pitch;
                grabExtension = grab.extension;
            } else if (targetEntity != null) {
                Vec3 shoulderCenter = minotaur.getPosition(partialTick).add(0.0D, minotaur.getBbHeight() * 0.68D, 0.0D);
                Vec3 targetCenter = targetEntity.getPosition(partialTick).add(0.0D, targetEntity.getBbHeight() * 0.52D, 0.0D);
                Vec3 delta = targetCenter.subtract(shoulderCenter);
                double horizontal = Math.max(0.001D, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
                float worldYaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
                grabYaw = Mth.clamp(Mth.wrapDegrees(worldYaw - bodyYaw), -78.0F, 78.0F) * Mth.DEG_TO_RAD;
                grabPitch = Mth.clamp((float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG),
                        -62.0F, 48.0F) * Mth.DEG_TO_RAD;
                grabExtension = Mth.clamp((float)(delta.length() / (minotaur.getBbHeight() * 0.72D)),
                        0.42F, 1.0F);
            }
        }
        float grabBlend = 1.0F - (float)Math.pow(0.38D, frameTicks);
        grab.weight += (desiredGrab - grab.weight) * grabBlend;
        grab.yaw += (grabYaw - grab.yaw) * grabBlend;
        grab.pitch += (grabPitch - grab.pitch) * grabBlend;
        grab.extension += (grabExtension - grab.extension) * grabBlend;
        state.addGeckolibData(GRAB_WEIGHT, grab.weight);
        state.addGeckolibData(GRAB_YAW, grab.yaw);
        state.addGeckolibData(GRAB_PITCH, grab.pitch);
        state.addGeckolibData(GRAB_EXTENSION, grab.extension);
        state.addGeckolibData(GRAB_ARM, grab.arm);
        state.addGeckolibData(HELD_PLAYER, minotaur.heldPlayerId());
        state.addGeckolibData(AXE_ACTION, minotaur.isAxeAttackActive());
        float rage = minotaur.rage() / 12.0F;
        if (minotaur.doorEntryTicks() > 0) {
            state.addGeckolibData(EYE_TINT, 0xFFD8FFFF);
        } else if (rage > 0.001F) {

            float pulse = 0.88F + 0.12F * Mth.sin((minotaur.tickCount + partialTick)
                    * (0.18F + rage * 0.38F));
            int red = 255;
            int green = Mth.floor(Mth.lerp(rage, 190.0F, 18.0F) * pulse);
            int blue = Mth.floor(Mth.lerp(rage, 92.0F, 6.0F) * pulse);
            int alpha = Mth.floor(Mth.lerp(rage, 185.0F, 255.0F));
            state.addGeckolibData(EYE_TINT, alpha << 24 | red << 16 | green << 8 | blue);
        } else if (minotaur.isExtremeBoss()) {
            float damage = minotaur.bossDamageFraction();
            int red = Mth.floor(Mth.lerp(damage, 205.0F, 255.0F));
            state.addGeckolibData(EYE_TINT, 0xFF000000 | red << 16 | 0xFFFF);
        } else state.addGeckolibData(EYE_TINT, 0xFFD8FFFF);
        }
        boolean tracking = minotaur.isPerformingGrab() || minotaur.animationState() == MinotaurEntity.AnimationState.IDLE
                || minotaur.animationState() == MinotaurEntity.AnimationState.WALK
                || minotaur.animationState() == MinotaurEntity.AnimationState.CHASE;
        if (!tracking || minotaur.doorEntryTicks() > 0 || minotaur.collapseAnimationTicks() > 0) {
            blend(minotaur,state,bones,partialTick); return;
        }
        float yaw = state.getOrDefaultGeckolibData(LOOK_YAW, 0.0F);
        float pitch = state.getOrDefaultGeckolibData(LOOK_PITCH, 0.0F);

        if (!state.getOrDefaultGeckolibData(AXE_ACTION, false))
            rotateBone(bones, "body", -yaw * 0.18F, -pitch * 0.14F);
        rotateBone(bones, "neck", -yaw * 0.32F, -pitch * 0.31F);
        rotateBone(bones, "head", -yaw * 0.50F, -pitch * 0.55F);

        float idle = state.getOrDefaultGeckolibData(IDLE_WEIGHT, 0.0F);
        if (idle > 0.001F) {
            float phase = state.getOrDefaultGeckolibData(IDLE_PHASE, 0.0F);
            float breath = Mth.sin(phase) * idle;
            float shift = Mth.sin(phase * 0.47F + 1.1F) * idle;
            rotateBone3(bones, "body", breath * 0.025F, shift * 0.032F, shift * 0.018F);
            rotateBone3(bones, "neck", -breath * 0.018F, -shift * 0.020F, 0.0F);
            rotateBone3(bones, "head", breath * 0.012F, shift * 0.026F, -shift * 0.009F);
            rotateBone3(bones, "leftshoulder", -breath * 0.018F, 0.0F, shift * 0.012F);
            rotateBone3(bones, "rightshoulder", -breath * 0.018F, 0.0F, -shift * 0.012F);
        }

        float rage = state.getOrDefaultGeckolibData(RAGE_WEIGHT, 0.0F);
        if (rage > 0.001F) {
            float phase = state.getOrDefaultGeckolibData(IDLE_PHASE, 0.0F);
            float pulse = Mth.sin(phase * (2.2F + rage * 1.8F));
            rotateBone3(bones, "body", -0.035F * rage + pulse * 0.012F * rage,
                    pulse * 0.018F * rage, 0.0F);
            rotateBone3(bones, "leftshoulder", -0.10F * rage, 0.0F,
                    -0.055F * rage + pulse * 0.018F * rage);
            rotateBone3(bones, "rightshoulder", -0.10F * rage, 0.0F,
                    0.055F * rage - pulse * 0.018F * rage);
            rotateBone3(bones, "head", 0.025F * rage, pulse * 0.012F * rage, 0.0F);
        }

        float grab = state.getOrDefaultGeckolibData(GRAB_WEIGHT, 0.0F);
        if (grab > 0.001F) {
            float targetYaw = state.getOrDefaultGeckolibData(GRAB_YAW, 0.0F);
            float targetPitch = state.getOrDefaultGeckolibData(GRAB_PITCH, 0.0F);
            float extension = state.getOrDefaultGeckolibData(GRAB_EXTENSION, 0.65F);
            int arm = state.getOrDefaultGeckolibData(GRAB_ARM, 1);
            applyArmReach(bones, arm, targetYaw, targetPitch, extension, grab);
            rotateBone3(bones, "body", targetPitch * 0.12F * grab,
                    -targetYaw * 0.18F * grab, 0.0F);
        }

        float horn = state.getOrDefaultGeckolibData(HORN_WEIGHT, 0.0F);
        if (horn > 0.001F) {
            int ticks = state.getOrDefaultGeckolibData(ATTACK_TICKS, 0);
            float lowered = ticks <= 28 ? smoother(ticks / 28.0F) : 1.0F;
            float runBob = ticks > 28 ? Mth.sin((ticks - 28) * 0.52F) * 0.035F : 0.0F;
            rotateBone3(bones, "lowerbody", -(0.08F + lowered * 0.13F - runBob * 0.4F) * horn,
                    0.0F, 0.0F);
            rotateBone3(bones, "body", -(0.18F + lowered * 0.27F + runBob) * horn, 0.0F, 0.0F);
            rotateBone3(bones, "neck", -(0.25F + lowered * 0.36F - runBob) * horn, 0.0F, 0.0F);
            rotateBone3(bones, "head", -(0.24F + lowered * 0.43F) * horn, 0.0F, 0.0F);
            translateBone(bones, "neck", 0.0F, -0.35F * lowered * horn, -0.85F * lowered * horn);
            translateBone(bones, "head", 0.0F, -0.55F * lowered * horn, -1.65F * lowered * horn);
            rotateBone3(bones, "lefthorn", -0.08F * lowered * horn, 0.0F, -0.06F * horn);
            rotateBone3(bones, "righthorn", -0.08F * lowered * horn, 0.0F, 0.06F * horn);
        }
        blend(minotaur,state,bones,partialTick);
    }

    private static void applyArmReach(Bones bones, int side, float yaw, float pitch,
                                      float extension, float weight) {
        boolean right = side >= 0;
        float sign = right ? 1.0F : -1.0F;
        String shoulder = right ? "rightshoulder" : "leftshoulder";
        String upperArm = right ? "rightarm" : "leftarm";
        String lowerArm = right ? "lowerrightarm" : "lowerleftarm";
        float elbowBend = Mth.lerp(extension, 0.22F, 0.08F);

        rotateBone3(bones, shoulder, pitch * 0.22F * weight,
                -yaw * 0.54F * weight, sign * 0.13F * weight);
        rotateBone3(bones, upperArm, (-1.35F + pitch * 0.72F) * weight,
                (sign * 0.20F - yaw * 0.58F) * weight, sign * 0.18F * weight);
        rotateBone3(bones, lowerArm, -elbowBend * weight,
                (sign * 0.13F - yaw * 0.24F) * weight,
                -sign * (0.28F + (1.0F - extension) * 0.18F) * weight);

    }

    private static void rotateBone(Bones bones, String name, float yaw, float pitch) {
        bones.ifPresent(name, snapshot -> snapshot.setRotation(
                snapshot.getRotX() + pitch, snapshot.getRotY() + yaw, snapshot.getRotZ()));
    }

    private static void rotateBone3(Bones bones, String name, float pitch, float yaw, float roll) {
        bones.ifPresent(name, snapshot -> snapshot.setRotation(snapshot.getRotX() + pitch,
                snapshot.getRotY() + yaw, snapshot.getRotZ() + roll));
    }

    private static void translateBone(Bones bones, String name, float x, float y, float z) {
        bones.ifPresent(name, snapshot -> snapshot.setTranslation(snapshot.getTranslateX() + x,
                snapshot.getTranslateY() + y, snapshot.getTranslateZ() + z));
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static final class LookPose {
        private float yaw;
        private float pitch;
        private float idleWeight;
    }

    private static final class GrabPose {
        private float weight;
        private float yaw;
        private float pitch;
        private float extension = 0.65F;
        private int arm = 1;
    }


    private static void blend(MinotaurEntity boss,State history,Bones bones,float partial) {
        double age=Math.max(history.age,boss.tickCount+partial);
        if(history.animation!=boss.animationState().ordinal()) {
            history.from.clear();history.last.forEach((name,values)->history.from.put(name,values.clone()));
            history.animation=boss.animationState().ordinal();history.start=age;
        }
        float t=Mth.clamp((float)((age-history.start)/6),0,1),blend=t*t*(3-2*t);
        for(GeoBone bone:bones.all) {
            float[] target=history.last.computeIfAbsent(bone.getName(),key->new float[9]);
            target[0]=bone.getRotX();target[1]=bone.getRotY();target[2]=bone.getRotZ();
            target[3]=bone.getPosX();target[4]=bone.getPosY();target[5]=bone.getPosZ();
            target[6]=bone.getScaleX();target[7]=bone.getScaleY();target[8]=bone.getScaleZ();
            float[] previous=history.from.get(bone.getName());
            if(previous!=null && t<1) for(int i=0;i<9;i++) {
                float difference=target[i]-previous[i];if(i<3)difference=(float)Math.atan2(Math.sin(difference),Math.cos(difference));
                target[i]=previous[i]+difference*blend;
            }
            var pose=new BonePose(bone);pose.setRotation(target[0],target[1],target[2]);pose.setTranslation(target[3],target[4],target[5]);
            bone.setScaleX(target[6]);bone.setScaleY(target[7]);bone.setScaleZ(target[8]);
        }
        history.age=age;if(t>=1)history.from.clear();
    }
    private record Bones(Collection<GeoBone> all) {
        void ifPresent(String name,java.util.function.Consumer<BonePose> consumer) {
            for(GeoBone bone:all) if(bone.getName().equals(name)){consumer.accept(new BonePose(bone));return;}
        }
    }
    private record BonePose(GeoBone bone) {
        float getRotX(){return bone.getRotX();} float getRotY(){return bone.getRotY();} float getRotZ(){return bone.getRotZ();}
        float getTranslateX(){return bone.getPosX();} float getTranslateY(){return bone.getPosY();} float getTranslateZ(){return bone.getPosZ();}
        void setRotation(float x,float y,float z){bone.setRotX(x);bone.setRotY(y);bone.setRotZ(z);}
        void setTranslation(float x,float y,float z){bone.setPosX(x);bone.setPosY(y);bone.setPosZ(z);}
    }
}
