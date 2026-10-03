package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Blends the first four flight ticks from the authored animated hand pose. */
final class PortWeaponRelease {
    record Release(Vec3 center, Quaternionf rotation, long tick) {}
    private static final java.util.Map<Long,Release> RELEASES = new java.util.HashMap<>();
    private static Object world;
    private static void syncWorld() {
        var level=Minecraft.getInstance().level;
        if(world!=level){RELEASES.clear();world=level;}
    }
    static void capture(int owner,int side,PoseStack poses) {
        syncWorld();var client=Minecraft.getInstance();if(client.level==null)return;
        if(RELEASES.size()>=192)RELEASES.clear();
        var matrix=poses.last().pose();
        var center=matrix.transformPosition(new Vector3f(0,(float)(side==0?MinotaurAxeEntity.CENTER_Y:MinotaurAxeEntity.SWORD_CENTER_Y),0));
        RELEASES.put(((long)owner<<2)+(side+1),new Release(client.gameRenderer.getMainCamera().getPosition().add(center.x,center.y,center.z),
                matrix.getUnnormalizedRotation(new Quaternionf()).normalize(),client.level.getGameTime()));
    }
    static Release get(int owner,int side) {
        syncWorld();var level=Minecraft.getInstance().level;
        var value=RELEASES.get(((long)owner<<2)+(side+1));
        return value!=null&&level!=null&&level.getGameTime()-value.tick()<8?value:null;
    }
}
