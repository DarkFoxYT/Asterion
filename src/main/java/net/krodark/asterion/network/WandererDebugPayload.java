package net.krodark.asterion.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.WandererEntity;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;

/** A bounded, operator-only snapshot of the path of the dead person under the crosshair. */
public record WandererDebugPayload(int entityId, int state, int next, List<BlockPos> nodes)
        implements CustomPacketPayload {
    public static final Type<WandererDebugPayload> TYPE = new Type<>(Asterion.id("wanderer_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WandererDebugPayload> CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeVarInt(payload.state);
                buffer.writeVarInt(payload.next);
                buffer.writeVarInt(payload.nodes.size());
                for (BlockPos node : payload.nodes) buffer.writeBlockPos(node);
            }, buffer -> {
                int id = buffer.readVarInt(), state = buffer.readVarInt(), next = buffer.readVarInt();
                int count = buffer.readVarInt();
                if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid wanderer path length");
                List<BlockPos> nodes = new ArrayList<>(count);
                for (int i = 0; i < count; i++) nodes.add(buffer.readBlockPos());
                return new WandererDebugPayload(id, state, next, List.copyOf(nodes));
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                context.server().execute(() -> reply(context.player(), payload.entityId)));
    }

    private static void reply(ServerPlayer viewer, int id) {
        if (!viewer.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
                || !(viewer.level().getEntity(id) instanceof PathfinderMob dead)
                || !(dead instanceof WandererEntity || dead instanceof LimboSpiderEntity)
                || viewer.distanceToSqr(dead) > 32 * 32) return;
        var toward = dead.getEyePosition().subtract(viewer.getEyePosition()).normalize();
        if (viewer.getLookAngle().dot(toward) < .965) return;
        Path path = dead.getNavigation().getPath();
        List<BlockPos> nodes = new ArrayList<>();
        int next = 0;
        if (path != null) {
            next = path.getNextNodeIndex();
            for (int i = 0; i < Math.min(48, path.getNodeCount()); i++)
                nodes.add(path.getNode(i).asBlockPos());
        }
        if (dead instanceof LimboSpiderEntity spider && spider.debugGoal() != null) {
            nodes.clear(); nodes.add(BlockPos.containing(spider.debugGoal())); next = 0;
        }
        if (ServerPlayNetworking.canSend(viewer, TYPE))
            ServerPlayNetworking.send(viewer, new WandererDebugPayload(id,
                    dead instanceof WandererEntity wanderer ? wanderer.state().ordinal()
                            : ((LimboSpiderEntity)dead).state().ordinal(), next, List.copyOf(nodes)));
    }
}
