package net.krodark.asterion.game;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.effect.GreekFireBurn;
import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Arena-only rescue: cancel death before vanilla drops inventory, reset the fight and refund paid keys. */
public final class ArenaDeathRecovery {
    private ArenaDeathRecovery() { }

    public static void initialize() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,amount)-> {
            if(!(entity instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !level.dimension().equals(Asterion.ASTERION_LEVEL))return true;
            BlockPos deathPosition=player.blockPosition().immutable();
            boolean minotaurArena=WorldGenerator.isInsideBossArena(player.position());
            boolean cursedArena=AuthoredCatacombs.insideCursedBrazierRoom(deathPosition);
            if(!minotaurArena&&!cursedArena)return true;

            // ALLOW_DEATH runs before loot/inventory drops. Restoring positive health and
            // cancelling here gives arena-local keep inventory without changing the gamerule.
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.clearFire();
            player.removeEffect(GreekFireBurn.TYPE);
            player.invulnerableTime=60;

            if(minotaurArena)
                EncounterKeyRecovery.restoreConsumed(player,Asterion.MINOTAUR_KEY);
            if(cursedArena)
                EncounterKeyRecovery.restoreConsumed(player,GameplayContent.CURSED_BRAZIER_KEY);

            level.getServer().execute(()-> {
                if(player.isRemoved())return;
                if(cursedArena)AuthoredCatacombs.resetCursedBrazierAfterDeath(level,deathPosition);
                if(minotaurArena)WorldGenerator.resetBossEncounterAfterDeath(player);
                WorldGenerator.respawnAtRune(player,deathPosition);
                player.setHealth(player.getMaxHealth());
                player.invulnerableTime=60;
                player.sendSystemMessage(Component.translatable("message.asterion.arena_revived"));
            });
            return false;
        });
    }
}
