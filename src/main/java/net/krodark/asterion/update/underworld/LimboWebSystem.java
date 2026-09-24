package net.krodark.asterion.update.underworld;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.WebCutPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.BitSet;
import java.util.HashSet;
/** Server authority for virtual strands: player impulses, drag, and force/tension tearing. */
public final class LimboWebSystem {
    private static final Map<Long, BitSet> CUT = new HashMap<>();
    private LimboWebSystem() { }
    public static void initialize() { WebCutPayload.initialize(); ServerTickEvents.END_SERVER_TICK.register(LimboWebSystem::tick); }
    public static boolean cut(long key, int link) { BitSet bits=CUT.get(key);return bits!=null&&bits.get(link); }
    public static void sever(long key, int link) { if(link>=0)CUT.computeIfAbsent(key,ignored->new BitSet()).set(link); }
    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Asterion.LIMBO_LEVEL); if (level == null) return;
        HashSet<Integer> visited = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (visited.add(player.getId())) affect(level, player, false);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(20), other -> other != player && other.isAlive())) {
                if (visited.add(entity.getId())) affect(level, entity, true);
            }
        }
    }
    private static void affect(ServerLevel level, LivingEntity entity, boolean cutsOnContact) {
            Vec3 center = entity.position().add(0, entity.getBbHeight() * .48, 0), velocity = entity.getDeltaMovement();
            var playerParts = entity instanceof ServerPlayer player ? WebPlayerShape.parts(player) : null;
            double strongestContact = 0;
            Vec3 resistance = Vec3.ZERO;
            double grip = 0;
            for (WebPatch patch : WebPatchGenerator.around(level, center, 7)) for (int i = 0; i < patch.edges().size(); i++) {
                WebPatch.Edge edge = patch.edges().get(i);Vec3 a=patch.anchors().get(edge.a()),b=patch.anchors().get(edge.b());
                WebPlayerShape.Contact modelContact = playerParts == null ? null : WebPlayerShape.contact(a,b,playerParts);
                Vec3 contact = modelContact == null ? nearest(a,b,center) : modelContact.strand();
                Vec3 ab=b.subtract(a);double along=ab.lengthSqr()<1e-8?0:Math.clamp(contact.subtract(a).dot(ab)/ab.lengthSqr(),0,1);
                int link=patch.linkIndex(i,along);if(cut(patch.key(),link))continue;
                double distance = modelContact == null ? contact.distanceTo(center) : Math.max(0,modelContact.gap());
                double reach = modelContact == null ? 1.04 : .78;
                if (distance > reach) continue;
                // Contact stretches and catches silk. Only a hard collision tears it.
                double impact = velocity.length();
                if (impact > (cutsOnContact ? .85 : 1.15) || velocity.y < -1.05) {
                    sever(patch.key(),link); WebCutPayload.broadcast(level,contact,patch.key(),link); continue;
                }
                double engagement = Math.clamp((reach - distance) / (reach * .8), 0D, 1D);
                grip = 1D - (1D - grip) * (1D - .32D * engagement);
                if (engagement <= strongestContact) continue;
                strongestContact = engagement;
                Vec3 normal = center.subtract(contact);
                normal = normal.lengthSqr() < 1.0e-5 ? Vec3.ZERO : normal.normalize();
                resistance = normal.scale(.018D * engagement);
            }
            if (grip > 0) {
                // A single thread catches; a cluster can hold the player almost still.
                entity.setDeltaMovement(velocity.scale(1D - Math.min(.97D, grip)).add(resistance));
                if (velocity.y < 0) entity.resetFallDistance();
                // Player input is client-driven, so velocity alone cannot reliably hold
                // them. A short hidden slowdown lets dense clusters block forward input.
                if (entity instanceof ServerPlayer && grip > .08D)
                    entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 8, grip > .8D ? 5 : grip > .55D ? 3 : grip > .3D ? 1 : 0,
                            false, false, false));
            }
    }
    public static Vec3 nearest(Vec3 a, Vec3 b, Vec3 p) { Vec3 ab = b.subtract(a); double length = ab.lengthSqr();
        return length < 1.0e-8 ? a : a.add(ab.scale(Math.clamp(p.subtract(a).dot(ab) / length, 0, 1))); }
}
