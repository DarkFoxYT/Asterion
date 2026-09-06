package net.krodark.asterion.dev.verification;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.item.AfterblowItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

final class AfterblowCombatCheck {
    static void run(MinecraftServer server) {
        var player = server.getPlayerList().getPlayers().getFirst();
        var level = player.level();
        var oldHand = player.getMainHandItem().copy();
        var oldMode = player.gameMode.getGameModeForPlayer();
        boolean wasInvulnerable = player.isInvulnerable();
        var opponent = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "AfterblowTest"),
                ClientInformation.createDefault());
         
        opponent.connection = player.connection;
        var sword = new ItemStack(Asterion.AFTERBLOW);
        var otherSword = new ItemStack(Asterion.AFTERBLOW);
        var item = (AfterblowItem)Asterion.AFTERBLOW;
        long now = level.getGameTime();
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setInvulnerable(false);
            player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            opponent.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            opponent.setItemInHand(InteractionHand.MAIN_HAND, otherSword);
            item.use(level, player, InteractionHand.MAIN_HAND);
            float health = player.getHealth();
            player.hurtServer(level, level.damageSources().playerAttack(opponent), 2);
            check(player.getHealth() == health, "Guard did not cancel incoming PvP damage");
            check(AfterblowItem.storedAt(sword, now) == 2, "Blocked damage was not stored exactly: "
                    + AfterblowItem.storedAt(sword, now) + ", PvP=" + level.isPvpAllowed());
            check(sword.getDamageValue() == 2, "Blocked damage was not applied as durability");
            check(!player.isUsingItem(), "Guard continued after blocking a hit");
            check(item.use(level, player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "Cooldown allowed another guard");
            check(!opponent.getCooldowns().isOnCooldown(otherSword), "Cooldown leaked between players");
            check(player.getCooldowns().isOnCooldown(otherSword), "Switching swords bypassed cooldown");
            for (int tick = 0; tick < 39; tick++) player.getCooldowns().tick();
            check(player.getCooldowns().isOnCooldown(sword), "Cooldown ended before two seconds");
            player.getCooldowns().tick();
            check(!player.getCooldowns().isOnCooldown(sword), "Cooldown exceeded two seconds");
            check(item.use(level, player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
                    "Charged sword was allowed to block again");

            opponent.setHealth(20);
            opponent.invulnerableTime = 0;
            opponent.hurtServer(level, level.damageSources().generic(), 9);
            opponent.setHealth(20);
            check(opponent.hurtServer(level, level.damageSources().playerAttack(player), 1), "Rapid counterattack was rejected during hurt immunity");
            check(Math.abs(opponent.getHealth() - 17) < .001F, "Rapid counter did not add all stored damage to one hit");
            check(AfterblowItem.storedAt(sword, now) == 0, "Counterattack did not consume charge");
            opponent.invulnerableTime = 0;
            opponent.hurtServer(level, level.damageSources().playerAttack(player), 3);
            check(Math.abs(opponent.getHealth() - 14) < .001F, "Charge was used twice");

            check(item.use(level, player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                    "Sword could not block again after discharge");
            player.hurtServer(level, level.damageSources().playerAttack(opponent), 8);
            check(AfterblowItem.storedAt(sword, now) == 8, "Second cycle did not store exact blocked damage");
            check(sword.getDamageValue() == 10, "Second block used the wrong durability amount");

            item.use(level, opponent, InteractionHand.MAIN_HAND);
            opponent.releaseUsingItem();
            check(opponent.getCooldowns().isOnCooldown(otherSword), "Releasing guard skipped cooldown");
            for (int tick = 0; tick < 40; tick++) opponent.getCooldowns().tick();
            item.finishUsingItem(otherSword, level, opponent);
            check(opponent.getCooldowns().isOnCooldown(otherSword), "Guard expiry skipped cooldown");
            Asterion.LOGGER.info("PASS: Afterblow exact stored damage, charged guard lockout, five-second expiry, durability cost and single-hit discharge");
        } finally {
            player.stopUsingItem();
            for (int tick = 0; tick < 40; tick++) player.getCooldowns().tick();
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
            player.setGameMode(oldMode);
            player.setInvulnerable(wasInvulnerable);
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
