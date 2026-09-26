package net.krodark.asterion.update.underworld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class WebPersistenceSmoke {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var field=WebSavedState.class.getDeclaredField("CODEC");field.setAccessible(true);
        var codec=(Codec<WebSavedState>)field.get(null);
        var state=new WebSavedState();
        var patch=new WebPatch(Long.MIN_VALUE,List.of(new Vec3(-12,5,30),new Vec3(9,5,30)),
                List.of(new Vec3(1,0,0),new Vec3(-1,0,0)),List.of(new WebPatch.Edge(0,1)));
        state.spun.put(patch.key(),patch);
        BitSet cuts=new BitSet();cuts.set(0);cuts.set(37);state.cuts.put(patch.key(),cuts);
        var restored=codec.parse(JsonOps.INSTANCE,codec.encodeStart(JsonOps.INSTANCE,state).getOrThrow()).getOrThrow();
        if(!restored.spun.get(patch.key()).equals(patch) || !restored.cuts.get(patch.key()).equals(cuts)
                || restored.spun.get(patch.key()).intact(0,restored.cuts.get(patch.key())))
            throw new AssertionError("Silk changed across save/load");
        var empty=codec.parse(JsonOps.INSTANCE,new com.google.gson.JsonObject()).getOrThrow();
        if(!empty.spun.isEmpty() || !empty.cuts.isEmpty())throw new AssertionError("Legacy save defaults");
        System.out.println("PASS silk topology, signed keys, cuts, and legacy save round trips");
    }
}
