package net.krodark.asterion.update.underworld;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.WebCutPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.BitSet;
/** Server authority for virtual strands: player impulses, drag, and force/tension tearing. */
public final class LimboWebSystem {
    private static final Map<Long, BitSet> CUT = new HashMap<>();
    private LimboWebSystem() { }
    public static void initialize() { WebCutPayload.initialize(); ServerTickEvents.END_SERVER_TICK.register(LimboWebSystem::tick); }
    public static boolean cut(long key, int link) { BitSet bits=CUT.get(key);return bits!=null&&bits.get(link); }
    public static void sever(long key, int link) { if(link>=0)CUT.computeIfAbsent(key,ignored->new BitSet()).set(link); }
    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Asterion.LIMBO_LEVEL); if (level == null) return;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            Vec3 center = player.position().add(0, player.getBbHeight() * .48, 0), velocity = player.getDeltaMovement();
            for (WebPatch patch : WebPatchGenerator.around(level, center, 7)) for (int i = 0; i < patch.edges().size(); i++) {
                WebPatch.Edge edge = patch.edges().get(i);Vec3 a=patch.anchors().get(edge.a()),b=patch.anchors().get(edge.b());
                Vec3 contact = nearest(a,b,center);Vec3 ab=b.subtract(a);double along=ab.lengthSqr()<1e-8?0:Math.clamp(contact.subtract(a).dot(ab)/ab.lengthSqr(),0,1);
                int link=patch.linkIndex(i,along);if(cut(patch.key(),link))continue;
                double distance = contact.distanceTo(center); if (distance > .86) continue;
                if (velocity.length() > .31 || velocity.y < -.44) { sever(patch.key(),link); WebCutPayload.broadcast(level,contact,patch.key(),link); continue; }
                Vec3 normal = center.subtract(contact); normal = normal.lengthSqr() < 1.0e-5 ? new Vec3(0, 1, 0) : normal.normalize();
                Vec3 spring = normal.scale((.82 - distance) * .16).subtract(velocity.scale(.86));
                player.setDeltaMovement(velocity.add(spring)); player.resetFallDistance();
            }
        }
    }
    public static Vec3 nearest(Vec3 a, Vec3 b, Vec3 p) { Vec3 ab = b.subtract(a); double length = ab.lengthSqr();
        return length < 1.0e-8 ? a : a.add(ab.scale(Math.clamp(p.subtract(a).dot(ab) / length, 0, 1))); }
}
