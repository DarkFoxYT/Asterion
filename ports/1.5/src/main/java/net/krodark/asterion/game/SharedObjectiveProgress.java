package net.krodark.asterion.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionWorldState;
import net.krodark.asterion.network.ObjectiveProgressPayload;
import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.krodark.asterion.worldgen.BossArenaEncounter;
import net.krodark.asterion.worldgen.CatacombLayout;
import net.krodark.asterion.worldgen.LabyrinthLevels;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

public final class SharedObjectiveProgress extends SavedData {
    private static final int CATACOMBS=1, BRAZIER_KEY=2, MOLD=4, FORGE=8, ORE=16,
            INGOTS=32, MINOTAUR_KEY=64, ARENA=128, OMEGA_KEY=256, FINISHED=512, QUEEN_MET=1024, QUEEN_HELPED=2048,
            OVERGROWTH=4096, MARSHLANDS=8192;
    public static final Codec<SharedObjectiveProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 16383).optionalFieldOf("milestones", 0).forGetter(state -> state.milestones)
    ).apply(instance, SharedObjectiveProgress::new));
    private static final net.krodark.asterion.port.compat.SavedDataCompat.Factory<SharedObjectiveProgress> FACTORY =
            net.krodark.asterion.port.compat.SavedDataCompat.factory(
                    CODEC, () -> new SharedObjectiveProgress(0));
    private static final Map<MinecraftServer, Map<UUID, Integer>> SENT = new WeakHashMap<>();
    private int milestones;

    private SharedObjectiveProgress(int milestones) { this.milestones = milestones; }
    public static SharedObjectiveProgress get(ServerLevel level) {
        return net.krodark.asterion.port.compat.SavedDataCompat.get(level.getDataStorage(), FACTORY, "asterion_shared_objectives");
    }
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return net.krodark.asterion.port.compat.SavedDataCompat.save(CODEC, this, tag, registries);
    }
    private boolean has(int flag) { return (milestones & flag) != 0; }
    public int stage() {
        if (has(FINISHED)) return 15;
        if (has(OMEGA_KEY)) return 11;
        if (has(ARENA)) return 10;
        if (has(MINOTAUR_KEY)) return 9;
        if (has(MOLD)) return !has(FORGE) ? 5 : has(INGOTS) ? 8 : has(ORE) ? 7 : 6;
        if (has(BRAZIER_KEY)) return 4;
        // A lower floor never substitutes for a missing key or boss reward.
        if (has(FORGE)) return 12;
        if (!has(QUEEN_HELPED)) {
            if (!has(QUEEN_MET)) return 0;
            if (!has(OVERGROWTH)) return 13;
            if (!has(MARSHLANDS)) return 14;
            return 1;
        }
        return has(CATACOMBS) ? 3 : 2;
    }
    public boolean queenRouteComplete() { return has(OVERGROWTH) && has(MARSHLANDS); }

    public net.minecraft.core.BlockPos navigationTarget(net.minecraft.world.phys.Vec3 position) {
        int stage = stage();
        if (stage == 0 || stage == 1) return null;
        if (stage == 4) return AuthoredCatacombs.BRAZIER_ROOM_ORIGINS.stream()
                .map(origin -> new net.minecraft.core.BlockPos(origin.getX(), AuthoredCatacombs.CONNECTOR_Y, origin.getZ() + 25))
                .min(java.util.Comparator.comparingDouble(pos -> position.distanceToSqr(
                        net.minecraft.world.phys.Vec3.atCenterOf(pos)))).orElse(null);
        if (stage == 9 || stage == 10)
            return MinotaurArenaEntrances.door(MinotaurArenaEntrances.PLAYER_ENTRANCE);
        if (stage == 2 || stage == 3 || stage == 5 || stage == 12)
            return new net.minecraft.core.BlockPos(CatacombLayout.ROOT_CENTER, AuthoredCatacombs.CONNECTOR_Y, CatacombLayout.ROOT_CENTER);
        return null;
    }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(SharedObjectiveProgress::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(SENT::remove);
    }
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) return;
        ServerLevel level = server.getLevel(Asterion.ASTERION_LEVEL);
        if (level == null) return;
        var progress = get(level);
        progress.observe(level);
        var sent = SENT.computeIfAbsent(server, ignored -> new HashMap<>());
        sent.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
        int stage = progress.stage();
        for (ServerPlayer player : level.players()) {
            if (sent.getOrDefault(player.getUUID(), -1) == stage && server.getTickCount() % 100 != 0) continue;
            if (ServerPlayNetworking.canSend(player, ObjectiveProgressPayload.TYPE)) {
                ServerPlayNetworking.send(player, new ObjectiveProgressPayload(stage));
                sent.put(player.getUUID(), stage);
            }
        }
    }
    public void observe(ServerLevel level) {
        int previous = milestones;
        var world = AsterionWorldState.get(level);
        if (world.cursedBrazierDefeated()) milestones |= MOLD;
        if (world.minotaurDefeated()) milestones |= OMEGA_KEY;
        if (world.omegaGateUnlocked()) milestones |= FINISHED;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            var pos = player.blockPosition();
            if (net.krodark.asterion.entity.QueenBeetleEntity.questIndex(player) > 0) milestones |= QUEEN_MET | QUEEN_HELPED;
            if (player.getTags().contains("asterion.queen_beetle_quest.active")) milestones |= QUEEN_MET;
            if (player.getY() >= LabyrinthLevels.MAZE_FLOOR_Y && player.getY() <= LabyrinthLevels.MAZE_FLOOR_Y + 96) {
                if (net.krodark.asterion.worldgen.WorldGenerator.isOvergrowthBiomeAt(player.getX(), player.getZ())) milestones |= OVERGROWTH;
                if (net.krodark.asterion.worldgen.WorldGenerator.isCrimsonMarshlandsAt(player.getX(), player.getZ())) milestones |= MARSHLANDS;
            }
            if (CatacombLayout.contains(pos)) milestones |= CATACOMBS;
            if (carries(player, GameplayContent.CURSED_BRAZIER_KEY)) milestones |= BRAZIER_KEY;
            if (carries(player, Asterion.MINOTAUR_KEY_CAST)) milestones |= MOLD;
            if (pos.getY() <= LabyrinthLevels.FORGE_ROOF_Y) milestones |= FORGE;
            if (carries(player, Asterion.SHALE_TARNISHED_GOLD_ORE.asItem(), Asterion.SHADED_SHALE_TARNISHED_GOLD_ORE.asItem(),
                    Asterion.SHALE_CELESTIAL_GOLD_ORE.asItem(), Asterion.SHADED_SHALE_CELESTIAL_GOLD_ORE.asItem())) milestones |= ORE;
            if (carries(player, Asterion.TARNISHED_GOLD_INGOT, Asterion.CELESTIAL_GOLD_INGOT,
                    Asterion.CELESTIAL_BRONZE_INGOT, Asterion.CELESTIAL_STEEL_INGOT, Asterion.BONESTEEL_INGOT)) milestones |= INGOTS;
            if (carries(player, Asterion.MINOTAUR_KEY)) milestones |= MINOTAUR_KEY;
            if (BossArenaEncounter.isParticipant(player) || has(MINOTAUR_KEY)
                    && player.position().distanceToSqr(MinotaurArenaEntrances.door(MinotaurArenaEntrances.PLAYER_ENTRANCE).getCenter()) <= 24 * 24)
                milestones |= ARENA;
            if (carries(player, Asterion.OMEGA_KEY)) milestones |= OMEGA_KEY;
        }
        if (milestones != previous) setDirty();
    }
    private static boolean carries(ServerPlayer player, Item... items) {
        for (Item item : items) if (player.getInventory().contains(new ItemStack(item))) return true;
        return false;
    }
//? if <1.20.5 {
/*    @Override public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) { return save(tag, null); }*/
//?}
}
