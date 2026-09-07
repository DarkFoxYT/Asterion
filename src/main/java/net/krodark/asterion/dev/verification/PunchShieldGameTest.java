package net.krodark.asterion.dev.verification;

import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public final class PunchShieldGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var bossRef = new java.util.concurrent.atomic.AtomicReference<MinotaurEntity>();
            for (int scenario = 0; scenario < 3; scenario++) {
                final int test = scenario;
                InteractionHand hand = scenario == 1 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                world.getServer().runOnServer(server -> {
                    var level = server.overworld();
                    var player = server.getPlayerList().getPlayers().getFirst();
                    for (BlockPos p : BlockPos.betweenClosed(-8,120,-8,8,120,8)) level.setBlock(p, Asterion.ANCIENT_BRICKS.defaultBlockState(), 18);
                    if (bossRef.get() == null) {
                        var boss = Asterion.MINOTAUR.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        boss.setPos(.5,121,.5); boss.setNoAi(true); boss.setYHeadRot(0); boss.setYRot(0);
                        level.addFreshEntity(boss); bossRef.set(boss);
                    }
                    player.stopUsingItem();
                    player.setGameMode(GameType.SURVIVAL);
                    player.setInvulnerable(false);
                    player.setHealth(20); player.invulnerableTime = 0;
                    player.teleportTo(level,.5,121,3.5,Set.of(),test == 2 ? 0 : 180,0,true);
                    player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    var shield = new ItemStack(Items.SHIELD);
                    shield.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
                    player.setItemInHand(hand, shield);
                    player.startUsingItem(hand);
                });
                context.waitTicks(10);
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    if (!player.isBlocking()) throw new AssertionError("Shield did not raise");
                    try {
                    var strike = MinotaurEntity.class.getDeclaredMethod("performPunchStrike", net.minecraft.server.level.ServerLevel.class,
                            net.minecraft.server.level.ServerPlayer.class, int.class);
                    strike.setAccessible(true);
                    strike.invoke(bossRef.get(), server.overworld(), player, 0);
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                    float expected = test == 2 ? 14F : 15.2F;
                    if (Math.abs(player.getHealth() - expected) > .01F) throw new AssertionError("Wrong punch damage: " + player.getHealth() + " expected " + expected);
                    if (test < 2 && !player.getItemInHand(hand).isEmpty()) throw new AssertionError("Unbreaking shield survived punch in " + hand);
                    if (test == 2 && player.getItemInHand(hand).isEmpty()) throw new AssertionError("Rear hit destroyed a shield that did not block");
                });
            }
            Asterion.LOGGER.info("PASS: punches instantly destroy Unbreaking III shields in either hand, reduce damage by 20%, and respect facing");
        }
    }
}
