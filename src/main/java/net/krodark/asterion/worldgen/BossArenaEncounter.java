package net.krodark.asterion.worldgen;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.MinotaurDoorBlock;
import net.krodark.asterion.block.MinotaurDoorBlockEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.network.BossEntrancePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Owns arena sealing and player safety separately from the boss's combat AI. */
public final class BossArenaEncounter {
    /** Ten-and-a-half second reveal: enough room for the breach, roar and a gentle camera return. */
    public static final int INTRO_TICKS = 210;
    private static Encounter active;
    private BossArenaEncounter() { }

    public static void initialize(ServerLevel level) {
        clear();
        // Authored gates and doors are restored by their FULL chunk callback. Doing
        // world reads here can wait on chunk futures while SERVER_STARTED still owns
        // the server thread, freezing initial world load.
        if(!AuthoredCatacombs.enabled()) {
            MinotaurArenaEntrances.setGates(level,0,null);
            restoreDoors(level);
        }
    }

    public static void begin(ServerLevel level, ServerPlayer trigger, MinotaurEntity boss, Direction entry) {
        if (entry != MinotaurArenaEntrances.PLAYER_ENTRANCE) return;
        if (active != null) { admit(level, trigger, entry); return; }
        // The authored portcullises must be completely raised before the camera takes
        // control. This also repairs partially closed gates left by an interrupted intro.
        MinotaurArenaEntrances.setGates(level, 0, null);
        active = new Encounter(level, boss.getUUID(), entry.getOpposite(), level.getGameTime());
        MinotaurArenaEntrances.setOmegaLockVisible(level, false);
        admit(level, trigger, entry);
        // Include the nearby party before anything closes; never pull players from elsewhere in the maze.
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player != trigger && eligible(player) && player.position().horizontalDistance() < 60
                    && player.getY() >= AuthoredCatacombs.ARENA_FLOOR_Y
                    && player.getY() < LabyrinthLevels.MAZE_FLOOR_Y + 1) admit(level, player, entry);
        }
        for (Direction facing : MinotaurArenaEntrances.DOORS) if (facing != active.bossDoor) {
            moveFromClosure(level, facing);
            if (level.getBlockEntity(MinotaurArenaEntrances.door(facing)) instanceof MinotaurDoorBlockEntity door)
                door.closeForEncounter();
        }
    }

    private static boolean eligible(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && !player.isCreative(); }

    private static void admit(ServerLevel level, ServerPlayer player, Direction entry) {
        if (active == null || !active.participants.add(player.getUUID())) return;
        Vec3 safe = safePosition(level, player, entry, active.participants.size() - 1);
        Vec3 focus = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(active.bossDoor)).add(0, 2.8, 0);
        Vec3 delta = focus.subtract(safe.add(0, player.getEyeHeight(), 0));
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        player.stopRiding();
        player.teleportTo(level, safe.x, safe.y, safe.z, Set.of(), yaw, 0, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        int elapsed = (int)(level.getGameTime() - active.start);
        if (elapsed < INTRO_TICKS) {
            active.locks.put(player.getUUID(), new Lock(player, safe, yaw, player.isInvulnerable(), player.isNoGravity()));
            player.setInvulnerable(true);
            player.setNoGravity(true);
            if (ServerPlayNetworking.canSend(player, BossEntrancePayload.TYPE))
                ServerPlayNetworking.send(player, new BossEntrancePayload(active.bossDoor, elapsed, INTRO_TICKS));
        }
    }

    private static Vec3 safePosition(ServerLevel level, ServerPlayer player, Direction entry, int slot) {
        Vec3 gate = Vec3.atBottomCenterOf(MinotaurArenaEntrances.gate(entry));
        Vec3 inward = entry.getOpposite().getUnitVec3();
        Vec3 across = entry.getClockWise().getUnitVec3();
        for (int attempt = 0; attempt < 60; attempt++) {
            int index = slot + attempt;
            int side = index % 5 - 2;
            double depth = 7.5D + (index / 5 % 12) * 1.15D;
            Vec3 candidate = gate.add(inward.scale(depth)).add(across.scale(side * 1.25D));
            var bounds = player.getBoundingBox().move(candidate.subtract(player.position()));
            if (level.noCollision(player, bounds)
                    && !level.getBlockState(BlockPos.containing(candidate).below()).getCollisionShape(level, BlockPos.containing(candidate).below()).isEmpty())
                return candidate;
        }
        return gate.add(inward.scale(10.0D));
    }

    public static void tick(ServerLevel level) {
        if (active == null || active.level != level) return;
        for (UUID id : List.copyOf(active.participants)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && !player.isAlive() && player.level() == level) {
                net.krodark.asterion.WorldGenerator.resetBossEncounterAfterDeath(player);
                return;
            }
        }
        var entity = level.getEntity(active.boss);
        if (!(entity instanceof MinotaurEntity boss) || !boss.isAlive()) { finish(level); return; }
        int elapsed = (int)(level.getGameTime() - active.start);
        if (elapsed >= INTRO_TICKS && !active.omegaLockRestored) {
            MinotaurArenaEntrances.setOmegaLockVisible(level, true);
            active.omegaLockRestored = true;
        }
        tickArenaCreatures(level, elapsed);
        // A late party member in an entrance corridor is admitted safely, never trapped behind a gate.
        for (ServerPlayer player : List.copyOf(level.players())) if (eligible(player) && !active.participants.contains(player.getUUID())) {
            Direction entrance = MinotaurArenaEntrances.corridorAt(player.position());
            if (entrance != null) admit(level, player, entrance);
        }
        Iterator<Lock> locks = active.locks.values().iterator();
        while (locks.hasNext()) {
            Lock lock = locks.next();
            ServerPlayer player = lock.player;
            if (elapsed >= INTRO_TICKS || player.isRemoved() || !player.isAlive() || player.level() != level) {
                release(lock); locks.remove(); continue;
            }
            player.setPos(lock.position);
            player.setYRot(lock.yaw); player.setXRot(0);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }
        active.participants.removeIf(id -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            return player == null || player.level() != level;
        });
        int height = MinotaurArenaEntrances.gateHeight();
        // Keep the authored grates fully raised for the reveal; seal the room once
        // control returns to the party.
        int closedRows = Math.clamp((elapsed - INTRO_TICKS) / 2, 0, height);
        List<Direction> encounterGates = AuthoredCatacombs.enabled()
                ? List.of(MinotaurArenaEntrances.PLAYER_ENTRANCE, MinotaurArenaEntrances.BOSS_ENTRANCE)
                : MinotaurArenaEntrances.DOORS;
        for (Direction facing : encounterGates) {
            int rows = closedRows;
            if (facing == active.bossDoor && (boss.doorEntryTicks() > 0
                    || boss.getBoundingBox().intersects(MinotaurArenaEntrances.gateBounds(facing).inflate(.2)))) continue;
            if (rows == active.closedRows.getOrDefault(facing, 0)) continue;
            moveFromClosure(level, facing);
            MinotaurArenaEntrances.setGate(level, facing, rows);
            active.closedRows.put(facing, rows);
            level.playSound(null, MinotaurArenaEntrances.gate(facing),
                    rows == height ? SoundEvents.ANVIL_LAND : SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 1.4F, .6F);
        }
        if (elapsed >= INTRO_TICKS) {
            // The north door is sacrificial. Never recreate it during combat: doing so
            // can wall the Minotaur into his staging room after his breach animation.
            BlockPos bossDoor = MinotaurArenaEntrances.door(active.bossDoor);
            MinotaurDoorBlock.removeDoor(level, bossDoor, active.bossDoor);
        }
        // Logging out, changing dimension, or being the only creative player must not
        // delete a live encounter. The entity is persistent and waits in the sealed arena;
        // an actual participant death is handled above by the explicit arena reset path.
    }

    public static boolean blocksCentipedeSpawn(ServerLevel level, Vec3 position) {
        return isSealed(level) && position.horizontalDistanceSqr() < 46 * 46
                && position.y >= AuthoredCatacombs.ARENA_FLOOR_Y
                && position.y <= LabyrinthLevels.MAZE_FLOOR_Y + 1;
    }

    private static void tickArenaCreatures(ServerLevel level, int elapsed) {
        if (elapsed % 20 != 0) return;
        var arena = new net.minecraft.world.phys.AABB(-44, AuthoredCatacombs.ARENA_FLOOR_Y, -44,
                44, LabyrinthLevels.MAZE_FLOOR_Y + 1, 44);
        // Leave ridden/named centipedes alone; suppress wild arena interference, not player mounts.
        for (var centipede : level.getEntitiesOfClass(net.krodark.asterion.entity.ScarletCentipedeEntity.class, arena))
            if (!centipede.isVehicle() && !centipede.hasCustomName()) centipede.discard();
        int firstWave = ((INTRO_TICKS + 40 + 19) / 20) * 20;
        if (elapsed < firstWave || (elapsed - firstWave) % 300 != 0) return;
        int existing = level.getEntitiesOfClass(net.krodark.asterion.entity.BombadierBeetleEntity.class, arena).size();
        int remaining = Math.min(2, 4 - existing);
        for (int attempt = 0; attempt < 24 && remaining > 0; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            Vec3 pos = new Vec3(Math.cos(angle) * 20 + .5, AuthoredCatacombs.ARENA_FLOOR_Y + 1, Math.sin(angle) * 20 + .5);
            if (level.players().stream().anyMatch(player -> player.position().distanceToSqr(pos) < 8 * 8)) continue;
            var beetle = Asterion.BOMBARDIER_BEETLE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (beetle == null) break;
            beetle.setPos(pos);
            BlockPos floor = BlockPos.containing(pos).below();
            if (!level.noCollision(beetle) || level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) continue;
            beetle.addTag("asterion_arena_beetle");
            if (level.addFreshEntity(beetle)) {
                active.beetles.add(beetle.getUUID()); remaining--;
                level.sendParticles(Asterion.DOOR_DUST, pos.x, pos.y + .2, pos.z, 12, .5, .15, .5, .025);
            }
        }
    }

    private static void moveFromClosure(ServerLevel level, Direction facing) {
        var gate = MinotaurArenaEntrances.gateBounds(facing).inflate(.3);
        var door = MinotaurArenaEntrances.doorBounds(facing).inflate(.3);
        for (ServerPlayer player : List.copyOf(level.players())) if (!player.isSpectator()
                && (player.getBoundingBox().intersects(gate) || player.getBoundingBox().intersects(door))) {
            Vec3 safe = safePosition(level, player, facing, 0);
            player.stopRiding();
            player.teleportTo(level, safe.x, safe.y, safe.z, Set.of(), player.getYRot(), player.getXRot(), true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }
    }

    public static boolean isMovementLocked(ServerPlayer player) {
        return active != null && player.level() == active.level && active.locks.containsKey(player.getUUID());
    }

    public static boolean isSealed(ServerLevel level) { return active != null && active.level == level; }

    public static boolean isIntroCinematic(ServerLevel level) {
        return active != null && active.level == level
                && level.getGameTime() - active.start < INTRO_TICKS;
    }

    public static boolean sealsDoor(net.minecraft.world.level.Level level, BlockPos root, Direction facing) {
        return level instanceof ServerLevel server && isSealed(server) && root.equals(MinotaurArenaEntrances.door(facing));
    }

    private static void restoreDoors(ServerLevel level) {
        var doors = AuthoredCatacombs.enabled()
                ? List.of(MinotaurArenaEntrances.PLAYER_ENTRANCE, MinotaurArenaEntrances.BOSS_ENTRANCE)
                : MinotaurArenaEntrances.DOORS;
        for (Direction facing : doors) {
            BlockPos root = MinotaurArenaEntrances.door(facing);
            if (level.getBlockEntity(root) instanceof MinotaurDoorBlockEntity) continue;
            moveFromClosure(level, facing);
            MinotaurDoorBlock.place(level, root, facing);
            level.sendParticles(Asterion.DOOR_DUST, root.getX() + .5, root.getY() + 1, root.getZ() + .5, 48, 2.8, 1, .25, .03);
            level.playSound(null, root, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.4F, .6F);
        }
    }

    public static void finish(ServerLevel level) {
        ArenaDebris.clear(level);
        clear();
        net.krodark.asterion.WorldGenerator.clearBossEntryTracking();
        MinotaurArenaEntrances.setGates(level, 0, null);
        restoreDoors(level);
    }

    /** Victory cleanup keeps the authored broken doorway and seals its surviving portcullis. */
    public static void finishDefeated(ServerLevel level) {
        ArenaDebris.clear(level);
        clear();
        net.krodark.asterion.WorldGenerator.clearBossEntryTracking();
        MinotaurArenaEntrances.setGates(level, MinotaurArenaEntrances.gateHeight(), null);
    }

    public static void clear() {
        if (active != null) {
            if (!active.omegaLockRestored)
                MinotaurArenaEntrances.setOmegaLockVisible(active.level, true);
            for (Lock lock : active.locks.values()) release(lock);
            for (UUID id : active.beetles) {
                var beetle = active.level.getEntity(id);
                if (beetle != null) beetle.discard();
            }
        }
        active = null;
    }

    public static void releasePlayer(ServerPlayer player) {
        if (active == null) return;
        Lock lock = active.locks.remove(player.getUUID());
        if (lock != null) release(lock);
        active.participants.remove(player.getUUID());
    }

    private static void release(Lock lock) {
        lock.player.setInvulnerable(lock.wasInvulnerable);
        lock.player.setNoGravity(lock.hadNoGravity);
        lock.player.setDeltaMovement(Vec3.ZERO);
        if (!lock.player.isRemoved() && ServerPlayNetworking.canSend(lock.player, BossEntrancePayload.TYPE))
            ServerPlayNetworking.send(lock.player, new BossEntrancePayload(Direction.NORTH, 0, 0));
    }

    private record Lock(ServerPlayer player, Vec3 position, float yaw, boolean wasInvulnerable, boolean hadNoGravity) { }
    private static final class Encounter {
        final ServerLevel level;
        final UUID boss;
        final Direction bossDoor;
        final long start;
        final Set<UUID> beetles = new HashSet<>();
        final Set<UUID> participants = new LinkedHashSet<>();
        final Map<UUID, Lock> locks = new HashMap<>();
        final Map<Direction, Integer> closedRows = new EnumMap<>(Direction.class);
        boolean omegaLockRestored;
        Encounter(ServerLevel level, UUID boss, Direction door, long start) {
            this.level = level; this.boss = boss; this.bossDoor = door; this.start = start;
        }
    }
}
