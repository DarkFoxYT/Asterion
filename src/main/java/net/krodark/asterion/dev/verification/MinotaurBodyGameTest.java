package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.MinotaurBodyPicking;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MinotaurBodyGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            var id = new AtomicInteger();
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                player.teleportTo(level, 0, 204, -16, Set.of(), 0, 5, true);
                player.setNoGravity(true);
                player.setInvulnerable(true);
                for (var pos : BlockPos.betweenClosed(-16, 199, -20, 16, 199, 16))
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), 18);
                var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
                boss.setPos(0, 200, 0);
                boss.setNoGravity(true);
                boss.setNoAi(true);
                boss.setYRot(180); boss.yBodyRot = 180; boss.yHeadRot = 180;
                level.addFreshEntity(boss);
                id.set(boss.getId());
            });
            context.waitTicks(35);
            var exposed = new AtomicReference<Vec3>();
            for (int mode : new int[]{0, 1, 2}) {
                server.runOnServer(mc -> {
                    var boss = (MinotaurEntity)mc.overworld().getEntity(id.get());
                    try {
                        var field = MinotaurEntity.class.getDeclaredField("DATA_WEAPON");
                        field.setAccessible(true);
                        @SuppressWarnings("unchecked") var key = (EntityDataAccessor<Integer>)field.get(null);
                        boss.getEntityData().set(key, mode);
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                });
                context.waitTicks(12);
                context.runOnClient(client -> {
                    var boss = (MinotaurEntity)client.level.getEntity(id.get());
                    check(boss.renderedWeaponMode() == mode, "Weapon mode did not synchronize");
                    var body = MinotaurBodyPicking.body(boss);
                    check(body != null && body.partCount() >= 15, "Animated body regions were not captured");
                    int outside = 0;
                    for (double x = -8; x <= 8; x += .25) for (double y = 200; y <= 212; y += .25) {
                        Vec3 from = new Vec3(x, y, -12), to = new Vec3(x, y, 12);
                        Vec3 hit = body.clip(from, to);
                        if (hit != null && !boss.getBoundingBox().contains(hit)) {
                            outside++;
                            if (hit.y > 203) exposed.set(hit);
                        }
                    }
                    check(outside > 10, "Targeting still only covers the navigation box");
                });
                context.takeScreenshot("minotaur-weapons-" + mode);
            }
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                var boss = (MinotaurEntity)mc.overworld().getEntity(id.get());
                Vec3 hit = exposed.get();
                check(hit != null, "No exposed upper-body attack region");
                boss.beginDebug(player);
                boss.setDebugRunning(false);
                player.teleportTo(mc.overworld(), hit.x, hit.y - player.getEyeHeight(), hit.z - 2, Set.of(), 0, 0, true);
                float before = boss.getHealth();
                net.krodark.asterion.network.MinotaurBodyPayload.handle(player,
                        new net.krodark.asterion.network.MinotaurBodyPayload(id.get(), hit.add(0, 0, 20), true));
                check(boss.getHealth() == before, "Out-of-reach damage was accepted");
                net.krodark.asterion.network.MinotaurBodyPayload.handle(player,
                        new net.krodark.asterion.network.MinotaurBodyPayload(id.get(), hit, true));
                check(boss.getHealth() < before, "Animated limb attack did not deal server-side damage");
                player.teleportTo(mc.overworld(), 0, 204, -16, Set.of(), 0, 5, true);
            });
            server.runOnServer(mc -> {
                var boss = (MinotaurEntity)mc.overworld().getEntity(id.get());
                var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, mc.overworld().registryAccess());
                boss.saveWithoutId(saved);
                var tag = saved.buildResult();
                tag.putInt("asterion_behavior_phase", MinotaurEntity.BehaviorPhase.BOSS.ordinal());
                tag.putInt("asterion_boss_stage", 3);
                tag.putInt("asterion_boss_animation_ticks", 85);
                boss.load(net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING, mc.overworld().registryAccess(), tag));
                var axe = new net.krodark.asterion.entity.MinotaurAxeEntity(Asterion.MINOTAUR_AXE, mc.overworld());
                axe.drop(new Vec3(5, 203, -3), Vec3.ZERO, 0, false, 1);
                mc.overworld().addFreshEntity(axe);
            });
            context.waitTicks(85);
            context.takeScreenshot("minotaur-fallen-body-and-axe");
            var surface = new AtomicReference<Vec3>();
            context.runOnClient(client -> {
                var boss = (MinotaurEntity)client.level.getEntity(id.get());
                var body = MinotaurBodyPicking.body(boss);
                check(body != null && body.partCount() > 10, "Fallen body lost its targeting regions");
                for (double x = -8; x < 8 && surface.get() == null; x += .25)
                    for (double z = -8; z < 8; z += .25) {
                        Vec3 hit = body.clip(new Vec3(x, 212, z), new Vec3(x, 200, z));
                        if (hit != null && !boss.getBoundingBox().contains(hit)) { surface.set(hit); break; }
                    }
                check(surface.get() != null, "Fallen limbs remain untargetable outside the standing box");
            });
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                var boss = (MinotaurEntity)mc.overworld().getEntity(id.get());
                var hit = surface.get();
                player.teleportTo(mc.overworld(), hit.x, hit.y + 1, hit.z, Set.of(), 0, 90, true);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                net.krodark.asterion.network.MinotaurBodyPayload.handle(player,
                        new net.krodark.asterion.network.MinotaurBodyPayload(id.get(), new Vec3(Double.NaN, 0, 0), false));
                check(!boss.isHarvested(), "Nonfinite interaction was accepted");
                var obstruction = BlockPos.containing(hit.add(0, 1, 0));
                mc.overworld().setBlock(obstruction, Blocks.STONE.defaultBlockState(), 18);
                net.krodark.asterion.network.MinotaurBodyPayload.handle(player,
                        new net.krodark.asterion.network.MinotaurBodyPayload(id.get(), hit, false));
                check(!boss.isHarvested(), "Interaction went through a wall");
                mc.overworld().setBlock(obstruction, Blocks.AIR.defaultBlockState(), 18);
            });
            context.waitTicks(5);
            context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new net.krodark.asterion.network.MinotaurBodyPayload(id.get(), surface.get(), false)));
            context.waitTicks(5);
            server.runOnServer(mc -> check(((MinotaurEntity)mc.overworld().getEntity(id.get())).isHarvested(),
                    "Empty-hand network harvest of a fallen limb failed"));
            Asterion.LOGGER.info("PASS: visible weapon modes, animated and fallen body targeting, blocked invalid interactions, empty-hand network harvest");
        }
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
