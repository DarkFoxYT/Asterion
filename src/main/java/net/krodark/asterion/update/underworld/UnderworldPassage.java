package net.krodark.asterion.update.underworld;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionWorldState;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.krodark.asterion.update.underworld.entity.CharonEntity;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Owns the one-time death transition and the persistent ferry at the river's threshold. */
public final class UnderworldPassage {
    private static int ferryCheck;

    private UnderworldPassage() { }

    public static void initialize() {
        FerryRejoin.initialize();
        // Registered after Asterion's existing respawn recovery, so the one-time story passage wins cleanly.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> enterAfterFirstDeath(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(UnderworldPassage::tick);
    }

    private static void enterAfterFirstDeath(ServerPlayer player) {
        ServerLevel destination = player.level().getServer().getLevel(Asterion.LIMBO_LEVEL);
        if (destination == null || !AsterionWorldState.get(destination).beginUnderworldPassage(player.getUUID())) return;
        destination.getChunk(UnderworldTerrain.SPAWN_X >> 4, UnderworldTerrain.SPAWN_Z >> 4);
        player.stopRiding();
        player.teleportTo(destination, UnderworldTerrain.SPAWN_X + .5, UnderworldTerrain.SPAWN_Y,
                UnderworldTerrain.SPAWN_Z + .5, Set.of(), 0F, 0F, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        // Charon always gives a newly dead soul exactly one fare.
        if (!player.getInventory().contains(new ItemStack(Items.GOLD_NUGGET)))
            player.getInventory().add(new ItemStack(Items.GOLD_NUGGET));
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Asterion.LIMBO_LEVEL);
        if (level == null || level.players().isEmpty()) return;

        for (ServerPlayer player : java.util.List.copyOf(level.players())) {
            if (FerryRejoin.recover(player)) continue;
            // The Styx is crossed aboard the paid ferry, not by swimming or walking around it.
            if (player.isAlive() && !player.isSpectator() && !player.getAbilities().instabuild
                    && player.getZ() > 105
                    && CharonsFerryEntity.supporting(player) == null
                    && !(level.getEntity(CharonsFerryEntity.SHARED_ID) instanceof CharonsFerryEntity escort
                    && escort.hasPaid(player) && escort.distanceToSqr(player) < 144)) {
                player.teleportTo(level, UnderworldTerrain.riverCenter(UnderworldTerrain.FERRY_Z) - 4,
                        UnderworldTerrain.WATER_Y + 2, UnderworldTerrain.FERRY_Z,
                        Set.of(), 0, 0, true);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
            }
            if (player.isAlive() && !player.isSpectator() && player.getX() > -42
                    && player.getZ() > 20 && player.getZ() < 105
                    && level.getEntity(CharonsFerryEntity.SHARED_ID) instanceof CharonsFerryEntity ferry)
                ferry.summon();
            if (player.isAlive() && !player.isSpectator()
                    && player.getZ() >= UnderworldTerrain.END_Z - 72
                    && level.getEntity(CharonsFerryEntity.SHARED_ID) instanceof CharonsFerryEntity arrival
                    && arrival.hasPaid(player) && arrival.supports(player)
                    && player.getY() >= UnderworldTerrain.WATER_Y - 3)
                net.krodark.asterion.worldgen.WorldGenerator.beginLimboExit(player);
        }

        if (FerryRejoin.hasPending() || ++ferryCheck < 80) return;
        ferryCheck = 0;
        if (level.getEntity(CharonsFerryEntity.SHARED_ID) instanceof CharonsFerryEntity ferry) {
            ensureCharon(level, ferry);
            return;
        }
        AABB route = new AABB(-96, UnderworldTerrain.WATER_Y - 8, UnderworldTerrain.START_Z,
                96, UnderworldTerrain.WATER_Y + 16, UnderworldTerrain.END_Z);
        if (!level.getEntitiesOfClass(CharonsFerryEntity.class, route).isEmpty()) return;
        level.getChunk(0, UnderworldTerrain.FERRY_Z >> 4);
        CharonsFerryEntity ferry = UnderworldContent.CHARONS_FERRY.create(level, EntitySpawnReason.EVENT);
        if (ferry == null) return;
        ferry.setUUID(CharonsFerryEntity.SHARED_ID);
        ferry.berth();
        level.addFreshEntity(ferry);
        ensureCharon(level, ferry);
    }

    private static void ensureCharon(ServerLevel level, CharonsFerryEntity ferry) {
        if (level.getEntity(CharonEntity.SHARED_ID) instanceof CharonEntity existing) {
            if (existing.getVehicle() != ferry) existing.startRiding(ferry);
            return;
        }
        CharonEntity charon = UnderworldContent.CHARON.create(level, EntitySpawnReason.EVENT);
        if (charon == null) return;
        charon.setUUID(CharonEntity.SHARED_ID);
        charon.setPos(ferry.getX(), ferry.deckY(), ferry.getZ());
        level.addFreshEntity(charon);
        charon.startRiding(ferry);
    }
}
