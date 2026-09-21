package net.krodark.asterion.update.underworld;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class FerryRejoin {
    private static final Map<UUID,Integer> pending=new HashMap<>();
    private FerryRejoin() { }
    public static boolean hasPending() { return !pending.isEmpty(); }
    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)-> { save(handler.player);pending.remove(handler.player.getUUID()); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->server.getPlayerList().getPlayers().forEach(FerryRejoin::save));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->pending.clear());
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)-> {
            var p=handler.player;
            pending.remove(p.getUUID());
            if(p.level().dimension().equals(Asterion.LIMBO_LEVEL)
                    && FerryJourneyState.get(p.level()).seats.containsKey(p.getUUID().toString())) pending.put(p.getUUID(),0);
        });
    }
    private static void save(ServerPlayer player) {
        if(!player.level().dimension().equals(Asterion.LIMBO_LEVEL))return;
        var state=FerryJourneyState.get(player.level());
        var boat=CharonsFerryEntity.supporting(player);
        if(boat!=null && player.isAlive()) {
            Vec3 local=boat.deckLocal(player.getX(),player.getZ());
            state.seats.put(player.getUUID().toString(),new FerryJourneyState.Seat(local.x,local.z,
                    player.getYRot()-boat.getYRot(),boat.hasPaid(player)));
            state.boatX=boat.getX();state.boatZ=boat.getZ();state.setDirty();
        } else if(!pending.containsKey(player.getUUID()) && state.seats.remove(player.getUUID().toString())!=null)state.setDirty();
    }
    /** Called before anti-swimming enforcement; pending recovery is never mistaken for an escape. */
    public static boolean recover(ServerPlayer player) {
        Integer attempts=pending.get(player.getUUID());
        if(attempts==null)return false;
        var state=FerryJourneyState.get(player.level());
        var seat=state.seats.get(player.getUUID().toString());
        if(seat==null || !player.isAlive()) { pending.remove(player.getUUID());return false; }
        player.level().getChunk((int)Math.floor(state.boatX)>>4,(int)Math.floor(state.boatZ)>>4);
        if(player.level().getEntity(CharonsFerryEntity.SHARED_ID) instanceof CharonsFerryEntity boat) {
            Vec3 position=boat.deckPoint(seat.x(),seat.z());
            player.teleportTo(player.level(),position.x,position.y,position.z,Set.of(),boat.getYRot()+seat.yaw(),player.getXRot(),true);
            if(seat.paid())boat.acceptFare(player);
            player.setDeltaMovement(Vec3.ZERO);player.setOnGround(true);player.resetFallDistance();
            state.seats.remove(player.getUUID().toString());state.setDirty();pending.remove(player.getUUID());
        } else if(attempts>=100) {
            // A removed ferry returns the player to the dry mainland entrance.
            player.level().getChunk(UnderworldTerrain.SPAWN_X >> 4, UnderworldTerrain.SPAWN_Z >> 4);
            player.teleportTo(player.level(),UnderworldTerrain.SPAWN_X+.5,UnderworldTerrain.SPAWN_Y,
                    UnderworldTerrain.SPAWN_Z+.5,Set.of(),0,0,true);
            player.setDeltaMovement(Vec3.ZERO);player.resetFallDistance();
            state.seats.remove(player.getUUID().toString());state.setDirty();pending.remove(player.getUUID());
        } else {
            pending.put(player.getUUID(),attempts+1);
            player.setDeltaMovement(Vec3.ZERO);player.resetFallDistance();
        }
        return true;
    }
}
