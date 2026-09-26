package net.krodark.asterion.update.underworld;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionWorldState;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.krodark.asterion.update.underworld.entity.CharonEntity;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** Owns the one-time death transition and the persistent ferry at the river's threshold. */
public final class UnderworldPassage {
    private static int ferryCheck;
    private static final Map<UUID, Set<Integer>> CHAMBER_EVENTS = new HashMap<>();

    private UnderworldPassage() { }

    public static void initialize() {
        FerryRejoin.initialize();
        FerryCommands.register();
        net.krodark.asterion.network.FerryControlPayload.initialize();
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level) || !level.dimension().equals(Asterion.LIMBO_LEVEL)
                    || !state.is(Blocks.COBWEB)) return;
            for (LimboSpiderEntity spider : level.getEntitiesOfClass(LimboSpiderEntity.class,
                    new AABB(pos).inflate(36), LimboSpiderEntity::isAlive))
                spider.hunt(player);
        });
        // Registered after Asterion's recovery so death always leads into Limbo.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) enterAfterDeath(newPlayer);
        });
        ServerTickEvents.END_SERVER_TICK.register(UnderworldPassage::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CHAMBER_EVENTS.clear());
    }

    private static void enterAfterDeath(ServerPlayer player) {
        ServerLevel destination = player.level().getServer().getLevel(Asterion.LIMBO_LEVEL);
        if (destination == null) return;
        boolean firstPassage = AsterionWorldState.get(destination).beginUnderworldPassage(player.getUUID());
        var spawn = UnderworldTerrain.randomSpawn(player.getUUID());
        destination.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        player.stopRiding();
        player.teleportTo(destination, spawn.getX() + .5, spawn.getY(),
                spawn.getZ() + .5, Set.of(), 0F, 0F, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        // Charon always gives a newly dead soul exactly one fare.
        if (firstPassage && !player.getInventory().contains(new ItemStack(Items.GOLD_NUGGET)))
            player.getInventory().add(new ItemStack(Items.GOLD_NUGGET));
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Asterion.LIMBO_LEVEL);
        if (level == null || level.players().isEmpty()) return;

        for (ServerPlayer player : java.util.List.copyOf(level.players())) {
            if (FerryRejoin.recover(player)) continue;
            net.krodark.asterion.update.underworld.world.UnderworldWaterPhysics.alignSurface(
                    player, level.getGameTime());
            chamberEvent(level, player);
            // The Styx is crossed aboard the paid ferry, not by swimming or walking around it.
            if (player.isAlive() && !player.isSpectator() && !player.getAbilities().instabuild
                    && player.getZ() > 105
                    && CharonsFerryEntity.supporting(player) == null
                    && !(level.getEntity(CharonsFerryEntity.SHARED_ID) instanceof CharonsFerryEntity escort
                    && escort.hasPaid(player) && escort.distanceToSqr(player) < 144)) {
                level.getChunk(UnderworldTerrain.SPAWN_X >> 4, UnderworldTerrain.SPAWN_Z >> 4);
                player.teleportTo(level, UnderworldTerrain.SPAWN_X + .5,
                        UnderworldTerrain.SPAWN_Y, UnderworldTerrain.SPAWN_Z + .5,
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

        // Nearby dropped items ride the same displaced surface as players and
        // the ferry. Bound the query to active players and every other tick.
        if ((level.getGameTime() & 1L) == 0L) {
            java.util.Set<Integer> seenItems = new java.util.HashSet<>();
            for (ServerPlayer player : level.players()) {
                int itemBudget = 64;
                for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        player.getBoundingBox().inflate(32, 8, 32))) {
                    if (itemBudget-- <= 0) break;
                    if (seenItems.add(item.getId()))
                        net.krodark.asterion.update.underworld.world.UnderworldWaterPhysics.alignItem(
                                item, level.getGameTime());
                }
            }
        }

        if (FerryRejoin.hasPending() || ++ferryCheck < 80) return;
        ferryCheck = 0;
        if (level.getEntity(CharonsFerryEntity.SHARED_ID) instanceof CharonsFerryEntity ferry) {
            ensureCharon(level, ferry);
            return;
        }
        // getEntity only exposes accessible sections; hidden entities still own their UUID.
        if (((net.krodark.asterion.mixin.ServerEntityManagerAccessor)level).asterion$entityManager().isLoaded(CharonsFerryEntity.SHARED_ID)) return;
        var journey=FerryJourneyState.get(level);
        int boatChunkX=(int)Math.floor(journey.boatX)>>4,boatChunkZ=(int)Math.floor(journey.boatZ)>>4;
        level.getChunk(boatChunkX,boatChunkZ);
        if(!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(boatChunkX,boatChunkZ)))return;
        if (((net.krodark.asterion.mixin.ServerEntityManagerAccessor)level).asterion$entityManager().isLoaded(CharonsFerryEntity.SHARED_ID)) return;
        AABB route = new AABB(-96, UnderworldTerrain.WATER_Y - 8, UnderworldTerrain.START_Z,
                96, UnderworldTerrain.WATER_Y + 16, UnderworldTerrain.END_Z);
        if (!level.getEntitiesOfClass(CharonsFerryEntity.class, route).isEmpty()) return;
        level.getChunk(0, UnderworldTerrain.FERRY_Z >> 4);
        CharonsFerryEntity ferry = UnderworldContent.CHARONS_FERRY.create(level, EntitySpawnReason.EVENT);
        if (ferry == null) return;
        ferry.setUUID(CharonsFerryEntity.SHARED_ID);
        ferry.berth();
        if(level.addFreshEntity(ferry))ensureCharon(level, ferry);
    }

    private static void chamberEvent(ServerLevel level, ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.getZ() < UnderworldTerrain.SPAWN_Z + 320
                || !UnderworldTerrain.inChamber(player.blockPosition())) return;
        int slot = Math.floorDiv(player.getBlockZ() - UnderworldTerrain.SPAWN_Z, 80);
        if (!CHAMBER_EVENTS.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(slot)) return;
        var center = UnderworldTerrain.chamberCenter(slot);
        level.playSound(null, center, SoundEvents.HUSK_AMBIENT, SoundSource.AMBIENT, .65F, .55F);
        if (UnderworldTerrain.nestSlot(slot)) {
            if (level.getEntitiesOfClass(LimboSpiderEntity.class,new AABB(center).inflate(48)).isEmpty()) {
                LimboSpiderEntity spider = UnderworldContent.SPIDER.create(level,EntitySpawnReason.EVENT);
                if (spider != null) {
                    spider.setNest(center);
                    level.addFreshEntity(spider);
                    // Motionless bodies are caught high in the web canopy around the perch.
                    if (level.getEntitiesOfClass(net.minecraft.world.entity.monster.skeleton.Skeleton.class,
                            new AABB(center).inflate(22)).isEmpty()) {
                        for (int i = 0; i < 3; i++) {
                            var victim = EntityType.SKELETON.create(level,EntitySpawnReason.EVENT);
                            if (victim == null) break;
                            victim.setPos(center.getX() + (i - 1) * 5.5,
                                    center.getY() + 14 + (i & 1) * 2,
                                    center.getZ() + (i == 1 ? -5 : 4));
                            victim.setNoAi(true);
                            victim.setNoGravity(true);
                            victim.setSilent(true);
                            victim.setInvulnerable(true);
                            level.addFreshEntity(victim);
                        }
                    }
                }
            }
            return;
        }
        // Encounter budget is per chamber/player; revisiting never piles up mobs.
        if ((slot & 1) == 0 && level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                new AABB(center).inflate(15), mob -> mob.getType() == EntityType.CAVE_SPIDER).size() < 3) {
            for (int i = 0; i < 2; i++) {
                var spider = EntityType.CAVE_SPIDER.create(level, EntitySpawnReason.EVENT);
                if (spider == null) break;
                spider.setPos(center.getX() + (i == 0 ? -3.5 : 3.5), center.getY(), center.getZ() + 2.5);
                level.addFreshEntity(spider);
            }
        }
    }

    private static void ensureCharon(ServerLevel level, CharonsFerryEntity ferry) {
        if (level.getEntity(CharonEntity.SHARED_ID) instanceof CharonEntity existing) {
            if (existing.getVehicle() != ferry) existing.startRiding(ferry);
            return;
        }
        if (((net.krodark.asterion.mixin.ServerEntityManagerAccessor)level).asterion$entityManager().isLoaded(CharonEntity.SHARED_ID)
                || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(ferry.getBlockX()>>4,ferry.getBlockZ()>>4)))return;
        CharonEntity charon = UnderworldContent.CHARON.create(level, EntitySpawnReason.EVENT);
        if (charon == null) return;
        charon.setUUID(CharonEntity.SHARED_ID);
        charon.setPos(ferry.getX(), ferry.deckY(), ferry.getZ());
        if(level.addFreshEntity(charon))charon.startRiding(ferry);
    }
}
