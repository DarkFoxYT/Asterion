package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.MinotaurBodyPicking;
import net.krodark.asterion.entity.*;
import net.krodark.asterion.game.AncientContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MinotaurRemainsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            var id = new AtomicInteger();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                for (var p : BlockPos.betweenClosed(-14, 199, -16, 14, 199, 14)) level.setBlock(p, Asterion.SHALE_BRICKS.defaultBlockState(), 18);
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, 0, 204, -9, Set.of(), 0, 25, true);
                player.setNoGravity(true); player.setInvulnerable(true);
                var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
                boss.setPos(0, 200, 0); boss.setNoAi(true); boss.setNoGravity(true);
                boss.setYRot(180); boss.yBodyRot = 180; boss.yHeadRot = 180;
                var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
                boss.saveWithoutId(output);
                var tag = output.buildResult();
                tag.putInt("asterion_behavior_phase", MinotaurEntity.BehaviorPhase.BOSS.ordinal());
                tag.putInt("asterion_boss_stage", 3); tag.putInt("asterion_boss_animation_ticks", 85);
                tag.putBoolean("death_weapons_dropped", true);
                boss.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
                level.addFreshEntity(boss); id.set(boss.getId());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            });
            context.runOnClient(c -> { c.player.setNoGravity(true); c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); });
            context.waitTicks(45);
            aim(context, server, id.get(), MinotaurRemains.HEAD);
            context.getInput().pressKey(o -> o.keyUse);
            context.waitTicks(6);
            server.runOnServer(mc -> {
                var boss = (MinotaurEntity)mc.overworld().getEntity(id.get());
                check(!boss.isHarvested(), "A stick skinned the corpse without shears");
                mc.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            });
            context.waitTicks(3);
            context.getInput().pressKey(o -> o.keyUse);
            context.waitTicks(6);
            server.runOnServer(mc -> {
                var boss = (MinotaurEntity)mc.overworld().getEntity(id.get());
                check(boss.isHarvested(), "Right-click with shears did not skin the corpse");
                boss.dismember(mc.getPlayerList().getPlayers().getFirst(), InteractionHand.MAIN_HAND, MinotaurRemains.LEFT_ARM);
                check(boss.removedParts() == 0, "Shears removed a limb");
                mc.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Asterion.CELESTIAL_BRONZE_SWORD));
                boss.dismember(mc.getPlayerList().getPlayers().getFirst(), InteractionHand.MAIN_HAND, MinotaurRemains.HEAD);
                check(boss.removedParts() == 0, "Skull could be taken before limbs");
            });
            overview(context, server, "minotaur-skinned-bones");
            int mask = 0;
            for (var part : new MinotaurRemains[]{MinotaurRemains.LEFT_LEG, MinotaurRemains.LEFT_ARM,
                    MinotaurRemains.RIGHT_ARM, MinotaurRemains.RIGHT_LEG, MinotaurRemains.TORSO, MinotaurRemains.HEAD}) {
                aim(context, server, id.get(), part);
                context.getInput().pressKey(o -> o.keyAttack);
                context.waitTicks(5);
                mask |= part.bit();
                final int expected = mask;
                server.runOnServer(mc -> {
                    var boss = (MinotaurEntity)mc.overworld().getEntity(id.get());
                    if (part == MinotaurRemains.HEAD) { check(boss == null || boss.isRemoved(), "Collected skull left a corpse: " + (boss == null ? "null" : boss.removedParts())); return; }
                    check(boss != null && boss.removedParts() == expected, "Attack did not remove " + part);
                    var area = new AABB(-12, 199, -12, 12, 212, 12);
                    int drops = mc.overworld().getEntitiesOfClass(ItemEntity.class, area).size();
                    boss.dismember(mc.getPlayerList().getPlayers().getFirst(), InteractionHand.MAIN_HAND, part);
                    check(mc.overworld().getEntitiesOfClass(ItemEntity.class, area).size() == drops, "Duplicate limb reward");
                    var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, mc.overworld().registryAccess());
                    boss.saveWithoutId(saved);
                    boss.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, mc.overworld().registryAccess(), saved.buildResult()));
                    check(boss.removedParts() == expected, "Removed limbs returned after reload");
                    if (part == MinotaurRemains.LEFT_LEG)
                        mc.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
                });
                if (part == MinotaurRemains.LEFT_ARM) overview(context, server, "minotaur-arm-and-leg-removed");
                if (part == MinotaurRemains.TORSO) {
                    context.runOnClient(c -> {
                        var boss = (MinotaurEntity)c.level.getEntity(id.get());
                        check(MinotaurBodyPicking.body(boss).partCount() == 1, "Body geometry remained after removing the torso");
                    });
                    overview(context, server, "minotaur-skull-only");
                }
                context.waitTicks(24);
            }
            server.runOnServer(mc -> {
                var level = mc.overworld(); var player = mc.getPlayerList().getPlayers().getFirst();
                var trophies = level.getEntitiesOfClass(ItemEntity.class, new AABB(-12, 199, -12, 12, 212, 12),
                        item -> item.getItem().is(AncientContent.MINOTAUR_TROPHY_ITEM));
                check(trophies.size() == 1 && trophies.getFirst().getItem().getCount() == 1, "Final skull did not yield exactly one trophy");
                ItemStack trophy = trophies.getFirst().getItem().copy(); trophies.getFirst().discard();
                var pos = new BlockPos(0, 201, -6);
                level.setBlock(pos.below(), Asterion.SHALE_BRICKS.defaultBlockState(), 18);
                player.teleportTo(level, 3, 202, -12, Set.of(), 21, 15, true);
                var placement = new net.minecraft.world.item.context.BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, trophy,
                        new BlockHitResult(pos.getBottomCenter(), net.minecraft.core.Direction.UP, pos.below(), false));
                ((BlockItem)AncientContent.MINOTAUR_TROPHY_ITEM).place(placement);
                check(level.getBlockState(pos).is(AncientContent.MINOTAUR_TROPHY), "Earned trophy could not be placed");
                player.teleportTo(level, 3, 202, -12, Set.of(), 21, 15, true);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
            context.runOnClient(c -> c.options.hideGui = true);
            context.waitTicks(10);
            context.takeScreenshot("minotaur-skull-trophy-placed");
            context.runOnClient(c -> c.options.hideGui = false);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                var pos = new BlockPos(0, 201, -6);
                check(player.gameMode.destroyBlock(pos), "Placed trophy could not be broken");
                var drops = mc.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1),
                        item -> item.getItem().is(AncientContent.MINOTAUR_TROPHY_ITEM));
                check(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1, "Broken trophy did not drop itself exactly once");
            });
            Asterion.LOGGER.info("PASS: actual skin/use and sword/axe attacks, targeted limbs in either order, saved removal, skull-last trophy placement");
        }
    }

    private static void aim(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, int id, MinotaurRemains part) {
        var point = new AtomicReference<Vec3>();
        context.runOnClient(c -> {
            var body = MinotaurBodyPicking.body((MinotaurEntity)c.level.getEntity(id));
            var targets = new java.util.ArrayList<Vec3>();
            for (double x = -9; x < 9; x += .4) for (double z = -9; z < 9; z += .4) {
                var hit = body.pick(new Vec3(x, 212, z), new Vec3(x, 199, z));
                if (hit != null && hit.part() == part.ordinal()) targets.add(hit.point());
            }
            check(!targets.isEmpty(), "No visible hit region for " + part);
            point.set(targets.get(targets.size() / 2));
        });
        server.runOnServer(mc -> {
            var p = point.get();
            mc.getPlayerList().getPlayers().getFirst().teleportTo(mc.overworld(), p.x, p.y + .9, p.z, Set.of(), 0, 90, true);
        });
        context.waitTicks(5);
        context.runOnClient(c -> {
            Asterion.LOGGER.info("REMAINS aim {} eye={} point={} pitch={} hit={}", part, c.player.getEyePosition(), point.get(), c.player.getXRot(), c.hitResult);
            check(c.hitResult instanceof EntityHitResult hit && hit.getEntity().getId() == id, "Crosshair did not target the corpse's " + part);
        });
    }

    private static void overview(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String name) {
        server.runOnServer(mc -> {
            boolean skull = name.endsWith("skull-only");
            mc.getPlayerList().getPlayers().getFirst().teleportTo(mc.overworld(), 0, skull ? 202 : 205, skull ? -9.5 : -12,
                    Set.of(), 0, skull ? 35 : 33, true);
        });
        context.runOnClient(c -> c.options.hideGui = true);
        context.waitTicks(8);
        context.takeScreenshot(name);
        context.runOnClient(c -> c.options.hideGui = false);
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
