package net.krodark.asterion.network;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.LimboWebSystem;
import net.krodark.asterion.update.underworld.WebPatch;
import net.krodark.asterion.update.underworld.WebPatchGenerator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public record WebCutPayload(long key, int link) implements CustomPacketPayload {
    public static final Type<WebCutPayload> TYPE = new Type<>(Asterion.id("web_cut"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WebCutPayload> CODEC = CustomPacketPayload.codec(
            (p, b) -> { b.writeLong(p.key); b.writeVarInt(p.link); }, b -> new WebCutPayload(b.readLong(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC); PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> context.server().execute(() -> handle(context.player(), payload)));
    }
    private static void handle(ServerPlayer player, WebCutPayload request) {
        if (!player.level().dimension().equals(Asterion.LIMBO_LEVEL) || request.link < 0 || request.link > 4096) return;
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle(), end = eye.add(look.scale(player.blockInteractionRange() + .75));
        for (WebPatch patch : WebPatchGenerator.around(player.level(), eye, 7)) if (patch.key() == request.key && request.link < patch.linkCount()) {
            int base=0;Vec3 a=null,b=null;
            for(int edgeIndex=0;edgeIndex<patch.edges().size();edgeIndex++){int pieces=patch.pieces(edgeIndex);if(request.link<base+pieces){WebPatch.Edge edge=patch.edges().get(edgeIndex);Vec3 from=patch.anchors().get(edge.a()),to=patch.anchors().get(edge.b());int local=request.link-base;a=from.lerp(to,local/(double)pieces);b=from.lerp(to,(local+1)/(double)pieces);break;}base+=pieces;}
            if (a!=null&&segmentDistanceSqr(eye,end,a,b)<=.20*.20) { LimboWebSystem.sever(request.key,request.link); broadcast((ServerLevel)player.level(),a.lerp(b,.5),request.key,request.link); }
            return;
        }
    }
    public static void broadcast(ServerLevel level, Vec3 point, long key, int link) {
        WebCutPayload payload = new WebCutPayload(key, link);
        for (ServerPlayer player : level.players()) if (player.distanceToSqr(point) < 72 * 72 && ServerPlayNetworking.canSend(player, TYPE)) ServerPlayNetworking.send(player, payload);
    }
    private static double segmentDistanceSqr(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
        // Closest points on two finite segments (Real-Time Collision Detection, Christer Ericson).
        Vec3 d1=q1.subtract(p1), d2=q2.subtract(p2), r=p1.subtract(p2); double a=d1.dot(d1), e=d2.dot(d2), f=d2.dot(r), s, t;
        if (a<=1e-8 && e<=1e-8) return p1.distanceToSqr(p2);
        if (a<=1e-8) { s=0; t=Math.clamp(f/e,0,1); }
        else { double c=d1.dot(r); if(e<=1e-8){t=0;s=Math.clamp(-c/a,0,1);} else {double b=d1.dot(d2),den=a*e-b*b;s=den==0?0:Math.clamp((b*f-c*e)/den,0,1);t=(b*s+f)/e;if(t<0){t=0;s=Math.clamp(-c/a,0,1);}else if(t>1){t=1;s=Math.clamp((b-c)/a,0,1);}} }
        return p1.add(d1.scale(s)).distanceToSqr(p2.add(d2.scale(t)));
    }
}
