package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CentipedeRideGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            var id = new AtomicInteger();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (var pos : BlockPos.betweenClosed(-8, 199, -44, 8, 199, 24)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 18);
                for (var pos : BlockPos.betweenClosed(-8, 200, -25, 8, 230, -25)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 18);
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, 0, 202, 0, Set.of(), 180, 0, true);
                player.setInvulnerable(true);
                var mount = Asterion.SCARLET_CENTIPEDE.create(level, EntitySpawnReason.COMMAND);
                mount.setPos(0, 200, 0); mount.setChainSegmentCount(7);
                mount.setNoAi(true); level.addFreshEntity(mount); id.set(mount.getId());
                if (!mount.mountSegment(player, 0)) throw new AssertionError("Could not board centipede");
            });
            context.waitTicks(25);
            var last = new AtomicReference<Vec3>();
            var velocity = new AtomicReference<>(Vec3.ZERO);
            double[] largest = {0, 0, 0};
            var endOfTick = new AtomicReference<Vec3>();
            context.getInput().holdKey(o -> o.keyUp);
            for (int tick = 0; tick < 170; tick++) {
                context.waitTicks(1);
                final int sample = tick;
                context.runOnClient(c -> {
                    var mount = (ScarletCentipedeEntity)c.level.getEntity(id.get());
                    if (c.player.getVehicle() != mount) throw new AssertionError("Rider was dismounted");
                    Vec3 startOfTick = mount.passengerPosition(c.player, 0);
                    if (endOfTick.get() != null) largest[2] = Math.max(largest[2], startOfTick.distanceTo(endOfTick.get()));
                    Vec3 seat = mount.passengerPosition(c.player, 1);
                    endOfTick.set(seat);
                    if (sample == 55) c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    if (sample == 115) c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    if (sample == 110) {
                        try {
                            var field = ScarletCentipedeEntity.class.getDeclaredField("DATA_ATTACHED_SURFACE");
                            field.setAccessible(true);
                            @SuppressWarnings("unchecked") var key = (net.minecraft.network.syncher.EntityDataAccessor<Integer>)field.get(null);
                            mount.getEntityData().set(key, net.minecraft.core.Direction.DOWN.ordinal());
                            if (mount.attachedSurface() != net.minecraft.core.Direction.NORTH)
                                throw new AssertionError("Delayed floor acknowledgement interrupted the driver's wall climb");
                        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                    }
                    if (last.get() != null) {
                        Vec3 motion = seat.subtract(last.get());
                        largest[0] = Math.max(largest[0], motion.length());
                        if (sample > 10) largest[1] = Math.max(largest[1], motion.subtract(velocity.get()).length());
                        velocity.set(motion);
                    }
                    last.set(seat);
                    if (sample == 169 && (mount.getY() < 220 || mount.attachedSurface() != net.minecraft.core.Direction.NORTH))
                        throw new AssertionError("Real forward input did not climb the wall");
                });
            }
            context.getInput().releaseKey(o -> o.keyUp);
            Asterion.LOGGER.info("RIDE maximum displacement={} acceleration={} tick-boundary jump={}", largest[0], largest[1], largest[2]);
            if (largest[0] > .65 || largest[1] > .45 || largest[2] > .025)
                throw new AssertionError("Ridden pose snapped across a movement tick");
            Asterion.LOGGER.info("PASS: real ridden floor-to-wall travel, continuous seat interpolation, delayed face acknowledgement");
            context.takeScreenshot("centipede-ridden-wall");
        }
    }
}
