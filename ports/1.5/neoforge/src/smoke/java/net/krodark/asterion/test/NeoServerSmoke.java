package net.krodark.asterion.test;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.network.ragdoll.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Dedicated-server-only checks, excluded from every release artifact. */
@Mod(value = "asterion_tests", dist = Dist.DEDICATED_SERVER)
public final class NeoServerSmoke {
    private net.minecraft.server.MinecraftServer pendingServer;
    private int startupTicks;
    private Runnable pendingChecks;
    public NeoServerSmoke() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
            if (pendingChecks != null) {
                var checks = pendingChecks;
                pendingChecks = null;
                checks.run();
            } else if (pendingServer != null && ++startupTicks >= 20) {
                var server = pendingServer;
                pendingServer = null;
                runChecks(server);
            }
        });
    }

    private void started(ServerStartedEvent event) {
        pendingServer = event.getServer();
        pendingServer.overworld().setChunkForced(0, 0, true);
    }

    private void runChecks(net.minecraft.server.MinecraftServer server) {
        var level = server.overworld();
        level.getChunk(0, 0);
        var player = FakePlayerFactory.get(level, new GameProfile(new UUID(7, 3), "AsterionServerCheck"));
        // FakePlayer has no socket. Supply an in-memory channel so capability
        // negotiation follows the normal unnegotiated-connection path.
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        try {
            var channelField = net.minecraft.network.Connection.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            channelField.set(player.connection.getConnection(), channel);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        player.setPos(0.5, 200, 0.5);
        level.addNewPlayer(player);
        var boss = new MinotaurEntity(Asterion.MINOTAUR, level);
        boss.setNoAi(true);
        boss.setPos(2, 200, 2);
        require(level.addFreshEntity(boss), "Boss fixture must spawn in a loaded chunk");
        pendingChecks = () -> {
        try {
            Method exit = method("exitTumble", ServerPlayer.class, TumbleExitPayload.class);
            Method relay = method("relayPose", ServerPlayer.class, RagdollPosePayload.class);
            Method canKill = method("clientKillable", net.minecraft.world.entity.LivingEntity.class);
            require(!(Boolean)canKill.invoke(null, player) && !(Boolean)canKill.invoke(null, boss),
                    "Dismemberment requests must protect players and the Minotaur");
            var brazier = new net.krodark.asterion.entity.CursedBrazierEntity(net.krodark.asterion.game.GameplayContent.CURSED_BRAZIER, level);
            var dragon = new net.minecraft.world.entity.boss.enderdragon.EnderDragon(net.minecraft.world.entity.EntityType.ENDER_DRAGON, level);
            var wither = new net.minecraft.world.entity.boss.wither.WitherBoss(net.minecraft.world.entity.EntityType.WITHER, level);
            var centipede = new net.krodark.asterion.entity.ScarletCentipedeEntity(Asterion.SCARLET_CENTIPEDE, level);
            require(!(Boolean)canKill.invoke(null, brazier) && !(Boolean)canKill.invoke(null, dragon)
                    && !(Boolean)canKill.invoke(null, wither) && (Boolean)canKill.invoke(null, centipede),
                    "Boss protection must preserve ordinary mob dismemberment");
            RagdollServerNetworking.markRagdolled(player, 60);
            require(RagdollServerNetworking.isRagdolled(player), "Ragdoll must activate on a dedicated server");
            require(player.gameMode.useItem(player, level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK),
                    net.minecraft.world.InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.FAIL,
                    "Server item-use guard must reject actions during a ragdoll");
            exit.invoke(null, player, new TumbleExitPayload(.5, 200, .5, 0, 0, 0, true));
            require(!RagdollServerNetworking.isRagdolled(player), "Finished tumble must release movement");
            relay.invoke(null, player, pose(player, player.getId(), 1, 1));
            require(!RagdollServerNetworking.isRagdolled(player), "Late pose must not restart a recovered tumble");
            map("RECOVERY_GRACE").clear();
            relay.invoke(null, player, pose(player, player.getId(), 2, Float.MAX_VALUE));
            require(!RagdollServerNetworking.isRagdolled(player), "Invalid quaternion must not enter physics");
            relay.invoke(null, player, pose(player, player.getId(), 3, 1));
            require(RagdollServerNetworking.isRagdolled(player), "Valid pose must activate a ragdoll");
            map("RAGDOLL_LEVELS").put(player.getUUID(), Level.NETHER);
            require(!RagdollServerNetworking.isRagdolled(player), "Ragdoll state must not cross dimensions");
            RagdollServerNetworking.resetAfterRespawn(player);
            relay.invoke(null, player, pose(player, player.getId(), 4, 1));
            require(!RagdollServerNetworking.isRagdolled(player), "Respawn grace must reject queued ragdoll packets");
            map("RESPAWN_GRACE").clear();

            var held = MinotaurEntity.class.getDeclaredField("DATA_HELD_PLAYER");
            held.setAccessible(true);
            @SuppressWarnings("unchecked")
            var accessor = (EntityDataAccessor<Integer>) held.get(null);
            boss.getEntityData().set(accessor, player.getId());
            require(MinotaurEntity.controlsPlayer(player), "Boss grab must retain server authority: player="
                    + player.position() + ", boss=" + boss.position() + ", alive=" + boss.isAlive()
                    + ", removed=" + boss.isRemoved() + ", held=" + boss.heldPlayerId() + ", playerId=" + player.getId()
                    + ", nearby=" + level.getEntitiesOfClass(MinotaurEntity.class, player.getBoundingBox().inflate(96)).size());
            Vec3 before = player.position();
            exit.invoke(null, player, new TumbleExitPayload(4, 200, 4, 4, 4, 4, false));
            require(player.position().equals(before), "Client tumble must not move a boss-held player");
            require(player.gameMode.useItem(player, level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK),
                    net.minecraft.world.InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.FAIL,
                    "Server item-use guard must reject actions during a boss grab");
            boss.getEntityData().set(accessor, -1);
            require(!MinotaurEntity.controlsPlayer(player), "Boss release must restore player authority");
            relay.invoke(null, player, pose(player, boss.getId(), 5, 1));
            var trackerField = RagdollServerNetworking.class.getDeclaredField("POSES");
            trackerField.setAccessible(true);
            var tracker = (RagdollPoseTracker) trackerField.get(null);
            require(tracker.accept(player.getUUID(), boss.getId(), 5, server.getTickCount()),
                    "Boss poses must be rejected before recording relay history");
            RagdollPoseTrackerChecks.main(new String[0]);
            Asterion.LOGGER.info("ASTERION_SERVER PASS: boss authority, ragdoll recovery, respawn, dimension isolation, invalid poses and relay load");
        } catch (Throwable error) {
            throw new AssertionError("Dedicated-server gameplay checks failed", error);
        } finally {
            RagdollServerNetworking.finishRagdoll(player);
            boss.discard();
            player.discard();
            channel.finishAndReleaseAll();
            level.setChunkForced(0, 0, false);
            server.halt(false);
        }
        };
    }

    private static RagdollPosePayload pose(ServerPlayer player, int entity, int sequence, float rotationW) {
        return new RagdollPosePayload(entity, sequence, List.of(new RagdollPosePayload.Part(1,
                (float)player.getX(), (float)player.getY(), (float)player.getZ(), 0, 0, 0, rotationW, 0, 0, 0)));
    }
    private static Method method(String name, Class<?>... args) throws ReflectiveOperationException {
        Method method = RagdollServerNetworking.class.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }
    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> map(String name) throws ReflectiveOperationException {
        var field = RagdollServerNetworking.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
