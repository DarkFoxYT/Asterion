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

/** Keeps encounter progression keys visible and recovers ignored drops without duplication. */
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

    /** Records the exact player who paid an arena entry cost. */
    public static void markConsumed(ServerPlayer player,Item key) {
        String marker=spentMarker(key);
        if(marker!=null)player.addTag(marker);
    }

    /** Restores a paid key once, without duplicating one the player already recovered elsewhere. */
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

    private static void tick(MinecraftServer server) {
        if(server.getTickCount()%20!=0||PENDING.isEmpty())return;
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
