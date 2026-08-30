package net.krodark.asterion.network;

import net.krodark.asterion.Asterion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.ArrayList;

/** Bounded debris batches carry initial conditions, never per-frame physics updates. */
public record ArenaDebrisPayload(List<Fragment> fragments, long seed) implements CustomPacketPayload {
    public static final int MAX_FRAGMENTS = 96;
    public ArenaDebrisPayload { fragments = List.copyOf(fragments); if (fragments.size() > MAX_FRAGMENTS) throw new IllegalArgumentException("Debris batch too large"); }
    public record Fragment(Vec3 position, Vec3 velocity) { }
    public static final Type<ArenaDebrisPayload> TYPE = new Type<>(Asterion.id("arena_debris"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaDebrisPayload> CODEC = StreamCodec.of(
        (out, p) -> {
            out.writeLong(p.seed); out.writeVarInt(p.fragments.size());
            for (Fragment f : p.fragments) {
                out.writeDouble(f.position.x); out.writeDouble(f.position.y); out.writeDouble(f.position.z);
                out.writeFloat((float)f.velocity.x); out.writeFloat((float)f.velocity.y); out.writeFloat((float)f.velocity.z);
            }
        }, in -> {
            long seed = in.readLong(); int count = in.readVarInt();
            if (count < 0 || count > MAX_FRAGMENTS) throw new IllegalArgumentException("Invalid debris count");
            var fragments = new ArrayList<Fragment>(count);
            for (int i = 0; i < count; i++) fragments.add(new Fragment(new Vec3(in.readDouble(), in.readDouble(), in.readDouble()),
                    new Vec3(in.readFloat(), in.readFloat(), in.readFloat())));
            return new ArenaDebrisPayload(fragments, seed);
        });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
