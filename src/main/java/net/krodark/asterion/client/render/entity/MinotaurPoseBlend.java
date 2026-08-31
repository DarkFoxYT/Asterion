package net.krodark.asterion.client.render.entity;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import java.util.*;

/** Crossfades from the last pose actually shown, including an interrupted transition. */
final class MinotaurPoseBlend {
    private static final DataTicket<Frame> FRAME = DataTickets.create("asterion_pose_blend", Frame.class);
    private static final Map<MinotaurEntity, History> HISTORIES = new WeakHashMap<>();
    private record Frame(History history, int pose, double age) { }
    private static final class History {
        int pose = -1;
        double start, lastAge = -1;
        Map<String, float[]> last = new HashMap<>(), from = Map.of();
    }
    static void capture(MinotaurEntity boss, EntityRenderState state, float partial) {
        if (partial == 1) partial = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        state.addGeckolibData(FRAME, new Frame(HISTORIES.computeIfAbsent(boss, key -> new History()),
                boss.animationState().ordinal(), boss.tickCount + partial));
    }
    static void apply(RenderPassInfo<EntityRenderState> pass, BoneSnapshots bones) {
        Frame frame = pass.getOrDefaultGeckolibData(FRAME, null);
        if (frame == null) return;
        History history = frame.history;
        double age = Math.max(history.lastAge, frame.age);
        boolean changed = frame.pose != history.pose;
        if (changed) {
            history.from = history.last;
            history.pose = frame.pose;
            history.start = age;
        }
        float t = (float)Math.clamp((age - history.start) / 6, 0, 1);
        float blend = t * t * (3 - 2 * t);
        Map<String, float[]> next = new HashMap<>();
        for (var bone : pass.model().boneLookup().get().values()) {
            var pose = bones.get(bone);
            float[] target = {pose.getRotX(), pose.getRotY(), pose.getRotZ(), pose.getTranslateX(),
                    pose.getTranslateY(), pose.getTranslateZ(), pose.getScaleX(), pose.getScaleY(), pose.getScaleZ()};
            float[] previous = history.from.get(bone.name());
            if (previous != null && t < 1) for (int i = 0; i < 9; i++) {
                float difference = target[i] - previous[i];
                if (i < 3) difference = (float)Math.atan2(Math.sin(difference), Math.cos(difference));
                target[i] = previous[i] + difference * blend;
            }
            pose.setRotation(target[0], target[1], target[2]);
            pose.setTranslation(target[3], target[4], target[5]);
            pose.setScale(target[6], target[7], target[8]);
            next.put(bone.name(), target);
        }
        history.last = next;
        history.lastAge = age;
        if (t >= 1) history.from = Map.of();
    }
}
