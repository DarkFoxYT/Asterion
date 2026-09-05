package net.krodark.asterion.dev.verification;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.ChainLiftBlockEntity;
import net.krodark.asterion.entity.ChainLiftEntity;
import net.krodark.asterion.game.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class ChainLiftGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            var ref = new AtomicReference<ChainLiftEntity>();
            BlockPos base = new BlockPos(20, 180, 20);
            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (BlockPos p : BlockPos.betweenClosed(16, 179, 16, 26, 179, 26)) level.setBlock(p, Asterion.SHALE.defaultBlockState(), 18);
                for (BlockPos p : BlockPos.betweenClosed(18, 193, 18, 22, 193, 22)) level.setBlock(p, Asterion.SHALE.defaultBlockState(), 18);
                var player = mc.getPlayerList().getPlayers().getFirst();
                var stack = new net.minecraft.world.item.ItemStack(ChainLiftContent.ITEM);
                var placement = new net.minecraft.world.item.context.BlockPlaceContext(level, player, net.minecraft.world.InteractionHand.MAIN_HAND,
                        stack, new net.minecraft.world.phys.BlockHitResult(base.getBottomCenter(), net.minecraft.core.Direction.UP, base.below(), false));
                ((net.minecraft.world.item.BlockItem)ChainLiftContent.ITEM).place(placement);
                check(level.getBlockState(base).is(ChainLiftContent.ANCHOR), "Player lift item could not be placed");
                check(ChainLiftBlockEntity.findCeiling(level, base) == 193, "Lift ceiling scan failed");
                player.teleportTo(level, 20.5, 190.5, 20.5, Set.of(), 0, 15, true);
                player.setInvulnerable(true);
                var skeleton = AncientContent.SKELETON.create(level, EntitySpawnReason.COMMAND);
                skeleton.setPos(24, 181, 23);
                skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(skeleton.blockPosition()), EntitySpawnReason.COMMAND, null);
                skeleton.setNoAi(true); level.addFreshEntity(skeleton);
                check(skeleton.getMaxHealth() == 36 && skeleton.getMainHandItem().is(Asterion.CELESTIAL_BRONZE_SWORD), "Ancient skeleton equipment or attributes missing");
            });
            context.waitTicks(40);
            server.runOnServer(mc -> {
                var lifts = mc.overworld().getEntitiesOfClass(ChainLiftEntity.class, new AABB(17, 180, 17, 24, 195, 24));
                check(lifts.size() == 1, "Anchor did not create exactly one lift"); ref.set(lifts.getFirst());
                check(ref.get().getY() == ref.get().topY(), "Lift did not wait at its upper home stop");
                mc.getPlayerList().getPlayers().getFirst().teleportTo(20.5, ref.get().topY() + .5, 20.5);
            });
            context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
            context.waitTicks(4);
            context.takeScreenshot("chain-lift-boarded");
            for (int sample = 0; sample < 16; sample++) {
                context.waitTicks(20);
                if (sample == 6 || sample == 14) {
                    AtomicReference<Double> walkingX = new AtomicReference<>();
                    context.runOnClient(c -> walkingX.set(c.player.getX()));
                    context.getInput().holdKey(options -> options.keyRight);
                    context.waitTicks(6);
                    context.getInput().releaseKey(options -> options.keyRight);
                    context.waitTicks(4);
                    context.runOnClient(c -> check(Math.abs(c.player.getX()-walkingX.get()) > .3,
                            "Real strafe input could not move across the moving deck"));
                    server.runOnServer(mc -> check(Math.abs(mc.getPlayerList().getPlayers().getFirst().getX()-walkingX.get()) > .2,
                            "Server rejected walking on the lift"));
                    context.getInput().holdKey(options -> options.keyLeft);
                    context.waitTicks(6);
                    context.getInput().releaseKey(options -> options.keyLeft);
                    context.waitTicks(4);
                    if (sample == 6) {
                        context.getInput().holdKey(options -> options.keyJump);
                        context.waitTicks(1);
                        context.getInput().releaseKey(options -> options.keyJump);
                        context.waitTicks(3);
                        context.runOnClient(c -> check(c.player.getY() - c.level.getEntity(ref.get().getId()).getY() > 1,
                                "Lift pinned the player instead of allowing a jump"));
                        context.waitTicks(20);
                    }
                }
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    check(!player.isPassenger(), "Lift mounted or locked the player");
                    check(Math.abs(player.getY() - ref.get().getY() - .5) < .19,
                            "Server rider left deck: " + player.getY() + "/" + ref.get().getY());
                });
                context.runOnClient(c -> {
                    var lift = c.level.getEntity(ref.get().getId());
                    check(lift != null && Math.abs(c.player.getY() - lift.getY() - .5) < .19,
                            "Client rider lost the moving deck: " + c.player.getY() + "/" + (lift == null ? "missing" : lift.getY()));
                    check(c.player.onGround() && Math.abs(c.player.getY()-lift.getY()-.5) < .02,
                            "Rider bounces between grounded and falling");
                    check(Math.abs(c.player.yo-lift.yo-.5) < .04,
                            "Previous-frame feet drift from the deck: " + (c.player.yo-lift.yo-.5));
                });
            }
            context.takeScreenshot("chain-lift-lower-landing");
            context.waitTicks(200);
            server.runOnServer(mc -> {
                check(ref.get().getY() <= 182, "Lift did not descend with its player");
                check(Math.abs(mc.getPlayerList().getPlayers().getFirst().getY() - ref.get().getY() - .5) < .19, "Rider fell through descending deck");
            });
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(20);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(10);
            context.runOnClient(c -> {
                var lift = c.level.getEntity(ref.get().getId());
                check(Math.abs(c.player.getZ() - lift.getZ()) > 2, "Player could not walk off the lift");
                check(c.player.getY() < lift.getY() + .2, "Lift kept supporting the player after stepping off");
            });
            Asterion.LOGGER.info("PASS: lift waits upstairs, live client strafes during descent, jumps and lands, stays synchronized, and walks off freely");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (BlockPos p : BlockPos.betweenClosed(34,179,10,58,179,30)) level.setBlock(p, Asterion.SHALE.defaultBlockState(),18);
                for (int i = 0; i < 2; i++) {
                    var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
                    var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
                    boss.saveWithoutId(out);
                    var tag = out.buildResult();
                    tag.putInt("asterion_behavior_phase", net.krodark.asterion.entity.MinotaurEntity.BehaviorPhase.BOSS.ordinal());
                    tag.putInt("asterion_boss_stage", 3);
                    tag.putInt("asterion_behavior_ticks", 200);
                    tag.putBoolean("hide_harvested", i == 1);
                    boss.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
                    boss.setPos(40 + i * 10, 180, 22); level.addFreshEntity(boss);
                }
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, 45, 186, 12, Set.of(), 0, 28, true);
                player.setNoGravity(true);
            });
            context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            context.waitTicks(20);
            context.takeScreenshot("minotaur-harvested-comparison");
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
