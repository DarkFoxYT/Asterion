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
        // Share only the disposable client's packet sink; combat and cooldown state are separate.
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
            check(AfterblowItem.storedAt(sword, now) == 11, "Light hit did not arm the counter: "
                    + AfterblowItem.storedAt(sword, now) + ", PvP=" + level.isPvpAllowed());
            check(!player.isUsingItem(), "Guard continued after blocking a hit");
            check(item.use(level, player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "Cooldown allowed another guard");
            check(!opponent.getCooldowns().isOnCooldown(otherSword), "Cooldown leaked between players");
            check(player.getCooldowns().isOnCooldown(otherSword), "Switching swords bypassed cooldown");
            for (int tick = 0; tick < 39; tick++) player.getCooldowns().tick();
            check(player.getCooldowns().isOnCooldown(sword), "Cooldown ended before two seconds");
            player.getCooldowns().tick();
            check(!player.getCooldowns().isOnCooldown(sword), "Cooldown exceeded two seconds");
            item.use(level, player, InteractionHand.MAIN_HAND);
            player.hurtServer(level, level.damageSources().playerAttack(opponent), 20);
            check(AfterblowItem.storedAt(sword, now) == 2, "Heavy hit stacked charge or failed to weaken it");
            check(sword.getDamageValue() == 22, "Heavy block did not cost more durability");

            opponent.setHealth(20);
            opponent.invulnerableTime = 0;
            check(opponent.hurtServer(level, level.damageSources().playerAttack(player), 9), "Counterattack was rejected");
            check(Math.abs(opponent.getHealth() - 18) < .001F, "Counter added normal damage or became an immune second hit");
            check(AfterblowItem.storedAt(sword, now) == 0, "Counterattack did not consume charge");
            opponent.invulnerableTime = 0;
            opponent.hurtServer(level, level.damageSources().playerAttack(player), 3);
            check(Math.abs(opponent.getHealth() - 15) < .001F, "Charge was used twice");

            item.use(level, opponent, InteractionHand.MAIN_HAND);
            opponent.releaseUsingItem();
            check(opponent.getCooldowns().isOnCooldown(otherSword), "Releasing guard skipped cooldown");
            for (int tick = 0; tick < 40; tick++) opponent.getCooldowns().tick();
            item.finishUsingItem(otherSword, level, opponent);
            check(opponent.getCooldowns().isOnCooldown(otherSword), "Guard expiry skipped cooldown");
            Asterion.LOGGER.info("PASS: Afterblow PvP guard, separate player state, single replacement counter, inverse damage scaling and 40-tick cooldown");
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
