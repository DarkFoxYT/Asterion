package net.krodark.asterion.game;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

 
public final class EncounterKeyRecovery {
    private static final long RECOVERY_DELAY=20L*60L;
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static final String MINOTAUR_KEY_SPENT="asterion_spent_minotaur_key";
    private static final String CURSED_KEY_SPENT="asterion_spent_cursed_brazier_key";
    private EncounterKeyRecovery() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(EncounterKeyRecovery::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->PENDING.clear());
    }

     
    public static void markConsumed(ServerPlayer player,Item key) {
        String marker=spentMarker(key);
        if(marker!=null)player.addTag(marker);
    }

     
    public static boolean restoreConsumed(ServerPlayer player,Item key) {
        String marker=spentMarker(key);
        if(marker==null||!player.removeTag(marker))return false;
        ItemStack stack=new ItemStack(key);
        if(!player.getInventory().contains(stack))player.getInventory().placeItemBackInInventory(stack);
        return true;
    }

    private static String spentMarker(Item key) {
        if(key==net.krodark.asterion.Asterion.MINOTAUR_KEY)return MINOTAUR_KEY_SPENT;
        if(key==GameplayContent.CURSED_BRAZIER_KEY)return CURSED_KEY_SPENT;
        return null;
    }

    public static void track(ServerLevel level,ItemEntity key,ServerPlayer intendedPlayer) {
        if(key==null)return;
        key.setGlowingTag(true);
        PENDING.put(key.getUUID(),new Pending(level.dimension(),key.getUUID(),
                intendedPlayer==null?null:intendedPlayer.getUUID(),level.getGameTime()+RECOVERY_DELAY));
    }

    private static final class Refunds extends net.minecraft.world.level.saveddata.SavedData {
        final Map<String, Integer> pending;
        Refunds(Map<String, Integer> pending) { this.pending = new HashMap<>(pending); }
        static final com.mojang.serialization.Codec<Refunds> CODEC =
                com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,
                        com.mojang.serialization.Codec.intRange(1, 3)).xmap(Refunds::new, refunds -> refunds.pending);
        static final net.minecraft.world.level.saveddata.SavedDataType<Refunds> TYPE =
                new net.minecraft.world.level.saveddata.SavedDataType<>(
                        net.krodark.asterion.Asterion.id("encounter_key_refunds"), () -> new Refunds(Map.of()), CODEC, null);
    }

    public static void refundAttemptKey(ServerLevel level, UUID id, Item key) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (player != null) { restoreConsumed(player, key); return; }
        Refunds refunds = level.getDataStorage().computeIfAbsent(Refunds.TYPE);
        int flag = key == net.krodark.asterion.Asterion.MINOTAUR_KEY ? 1 : 2;
        refunds.pending.merge(id.toString(), flag, (a, b) -> a | b);
        refunds.setDirty();
    }

    private static void deliverRefunds(MinecraftServer server) {
        ServerLevel maze = server.getLevel(net.krodark.asterion.Asterion.ASTERION_LEVEL);
        if (maze == null) return;
        Refunds refunds = maze.getDataStorage().computeIfAbsent(Refunds.TYPE);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer flags = refunds.pending.remove(player.getUUID().toString());
            if (flags == null) continue;
            if ((flags & 1) != 0) restoreConsumed(player, net.krodark.asterion.Asterion.MINOTAUR_KEY);
            if ((flags & 2) != 0) restoreConsumed(player, GameplayContent.CURSED_BRAZIER_KEY);
            refunds.setDirty();
        }
    }

    private static void tick(MinecraftServer server) {
        if(server.getTickCount()%20!=0)return;
        deliverRefunds(server);
        if(PENDING.isEmpty())return;
        Iterator<Pending> iterator=PENDING.values().iterator();
        while(iterator.hasNext()) {
            Pending pending=iterator.next();
            ServerLevel level=server.getLevel(pending.dimension);
            if(level==null)continue;
            var entity=level.getEntity(pending.itemId);
            if(!(entity instanceof ItemEntity item)||!item.isAlive()) {iterator.remove();continue;}
            item.setGlowingTag(true);
            if(level.getGameTime()<pending.deadline)continue;
            ServerPlayer player=pending.playerId==null?null:server.getPlayerList().getPlayer(pending.playerId);
            if(player==null||player.level()!=level||!player.isAlive()||player.isSpectator()) {
                player=level.players().stream().filter(candidate->candidate.isAlive()&&!candidate.isSpectator())
                        .min(java.util.Comparator.comparingDouble(candidate->candidate.distanceToSqr(item))).orElse(null);
            }
            if(player==null)continue;
            var stack=item.getItem().copy();
            if(!player.getInventory().add(stack))continue;
            item.discard();
            iterator.remove();
        }
    }

    private record Pending(ResourceKey<Level> dimension,UUID itemId,UUID playerId,long deadline) { }
}
