package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlock;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.entity.*;
import net.krodark.asterion.game.ChainLiftContent;
import net.krodark.asterion.item.AfterblowItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.phys.AABB;
import java.util.Set;

public final class LiftForgeFixesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> {
            org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle());
            com.meekdev.amnetic.client.bloom.Bloom.settings().enabled(true).all(false).occlude(true).threshold(0);
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                AfterblowCombatCheck.run(server);
                try {
                    var field = net.krodark.asterion.worldgen.AuthoredForge.class.getDeclaredField("CRUCIBLE_PART_DATA");
                    field.setAccessible(true);
                    var processor = (StructureProcessor)field.get(null);
                    var template = server.getStructureManager().get(Asterion.id("forge/forge")).orElseThrow();
                    for (Rotation rotation : Rotation.values()) {
                        var origin = new BlockPos(150 + rotation.ordinal() * 160, 170, 150);
                        var settings = new StructurePlaceSettings().setRotation(rotation).addProcessor(processor);
                        check(template.placeInWorld(level, origin, origin, settings, level.getRandom(), 18), "Forge template failed to place");
                        var roots = template.filterBlocks(origin, settings, Asterion.CRUCIBLE).stream()
                                .filter(info -> CrucibleBlock.isRoot(info.state())).toList();
                        check(roots.size() == 1, "Generated Forge has wrong root count");
                        var root = roots.getFirst().pos();
                        var forge = (CrucibleBlockEntity)level.getBlockEntity(root);
                        check(forge != null && forge.materialUnits() == 0, "Generated Forge retained saved ingredients");
                        for (BlockPos part : BlockPos.betweenClosed(root.offset(-2, 0, -2), root.offset(2, 3, 2))) {
                            var state = level.getBlockState(part);
                            check(state.is(Asterion.CRUCIBLE) && CrucibleBlock.root(part, state).equals(root), "Rotated Forge part lost its root");
                            check(state.hasBlockEntity() == part.equals(root), "Forge allocated a block entity for a non-root part");
                        }
                        for (int i = 0; i < 100; i++) CrucibleBlockEntity.tick(level, root, level.getBlockState(root), forge);
                        check(forge.temperature() > 0 && forge.fuelTicks() > 0, "Generated Forge did not heat over its magma block");
                    }
                    var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
                    var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                    boss.saveWithoutId(out);
                    var tag = out.buildResult();
                    tag.putInt("asterion_behavior_phase", MinotaurEntity.BehaviorPhase.BOSS.ordinal());
                    tag.putInt("asterion_boss_stage", 2);
                    boss.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                    boss.setPos(30, 200, 30);
                    var sword = new ItemStack(Asterion.AFTERBLOW);
                    player.setItemInHand(InteractionHand.MAIN_HAND, sword);
                    player.startUsingItem(InteractionHand.MAIN_HAND);
                    check(AfterblowItem.tryBlock(player, level.damageSources().mobAttack(boss), 4), "Could not charge Afterblow");
                    float health = boss.getHealth();
                    check(boss.hurtServer(level, level.damageSources().playerAttack(player), 3), "Boss rejected counter");
                    check(boss.getHealth() < health && AfterblowItem.storedAt(sword, level.getGameTime()) == 0, "Boss counter failed to discharge");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                BlockPos anchor = new BlockPos(0, 200, 0);
                for (var floor : BlockPos.betweenClosed(-6, 199, -6, 6, 199, 6)) level.setBlock(floor, Asterion.SHALE_BRICKS.defaultBlockState(), 18);
                level.setBlock(anchor, ChainLiftContent.ANCHOR.defaultBlockState(), 18);
                level.setBlock(anchor.above(10), Asterion.SHALE_BRICKS.defaultBlockState(), 18);
                var lift = ChainLiftContent.LIFT.create(level, EntitySpawnReason.EVENT);
                lift.setUUID(java.util.UUID.nameUUIDFromBytes((level.dimension().identifier() + ":chain_lift:" + anchor.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                lift.configure(anchor, 210); level.addFreshEntity(lift);
                player.teleportTo(level, .5, 200, -4, Set.of(), 0, -12, true);
                player.setNoGravity(true);
                lift.tick();
                var runes = level.getEntitiesOfClass(LiftCallRuneEntity.class, new AABB(-4, 200, -4, 4, 212, 4));
                check(runes.size() == 2, "Lift did not create both call runes");
                var lower = runes.stream().min(java.util.Comparator.comparingDouble(e -> e.getY())).orElseThrow();
                lower.interact(player, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
                lift.tick();
                check(lift.moving(), "Right-click did not call lift down");
                Asterion.LOGGER.info("PASS: all four Forge rotations retain 100 valid parts and heat, clean template contents, PvP and boss Afterblow discharge, both lift call runes");
            });
            context.waitTicks(180);
            world.getServer().runOnServer(server -> {
                var lift = server.overworld().getEntitiesOfClass(ChainLiftEntity.class, new AABB(-4,199,-4,4,213,4)).getFirst();
                check(Math.abs(lift.getY() - lift.bottomY()) < .01, "Called lift did not reach lower landing");
                var player = server.getPlayerList().getPlayers().getFirst();
                var rune = server.overworld().getEntitiesOfClass(LiftCallRuneEntity.class, new AABB(-4,200,-4,4,204,4)).getFirst();
                player.teleportTo(server.overworld(), rune.getX(), rune.getY() - 1.25, rune.getZ() - 3, Set.of(), 0, 0, true);
                player.setNoGravity(true);
            });
            context.waitTicks(20);
            context.takeScreenshot("lift-call-rune");
            context.runOnClient(c -> check(net.krodark.asterion.client.light.AmneticBoneEmission.submissions() > 0, "Rune did not reach Amnetic emission"));
            world.getServer().runOnServer(server -> {
                var lift = server.overworld().getEntitiesOfClass(ChainLiftEntity.class, new AABB(-4,199,-4,4,213,4)).getFirst();
                lift.callTo(true);
            });
            context.waitTicks(180);
            world.getServer().runOnServer(server -> {
                var lift = server.overworld().getEntitiesOfClass(ChainLiftEntity.class, new AABB(-4,199,-4,4,213,4)).getFirst();
                check(Math.abs(lift.getY() - lift.topY()) < .01, "Called lift did not reach upper landing");
                Asterion.LOGGER.info("PASS: lift responds to calls at both stops and rune renders through Amnetic");
            });
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

