package net.krodark.asterion.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.WebPatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Server-authored geometry only: no web blocks or entities. */
public record WebSpinPayload(long key,Vec3 a,Vec3 b,Vec3 normalA,Vec3 normalB) implements CustomPacketPayload {
    public static final Type<WebSpinPayload> TYPE=new Type<>(Asterion.id("web_spin"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WebSpinPayload> CODEC=CustomPacketPayload.codec(
            (p,b)->{b.writeLong(p.key);write(b,p.a);write(b,p.b);write(b,p.normalA);write(b,p.normalB);},
            b->new WebSpinPayload(b.readLong(),read(b),read(b),read(b),read(b)));
    private static void write(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 read(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public WebPatch patch(){return new WebPatch(key,List.of(a,b),List.of(normalA,normalB),List.of(new WebPatch.Edge(0,1)));}
    public static void initialize(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);}
    public static void send(ServerPlayer player,WebPatch patch){
        if(ServerPlayNetworking.canSend(player,TYPE))ServerPlayNetworking.send(player,new WebSpinPayload(patch.key(),patch.anchors().get(0),patch.anchors().get(1),patch.normals().get(0),patch.normals().get(1)));
    }
}
