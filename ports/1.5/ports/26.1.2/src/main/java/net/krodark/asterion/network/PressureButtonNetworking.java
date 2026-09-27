package net.krodark.asterion.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LamenterBlock;
import net.krodark.asterion.block.PressureButtonBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PressureButtonNetworking {
    private static final int HOLD_TICKS=40;
    private static final Map<UUID,Hold> HOLDS=new HashMap<>();
    private PressureButtonNetworking() { }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(PressureButtonHoldPayload.TYPE,(payload,context)->
                context.server().execute(()->setHolding(context.player(),payload.pos(),payload.held())));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var iterator=HOLDS.entrySet().iterator();
            while(iterator.hasNext()) {
                var entry=iterator.next();
                ServerPlayer player=server.getPlayerList().getPlayer(entry.getKey());
                Hold hold=entry.getValue();
                ServerLevel level=server.getLevel(hold.dimension);
                if(player==null||level==null||player.level()!=level||!validHeld(player,hold.pos)) {
                    iterator.remove();
                    if(level!=null) release(level,hold);
                    continue;
                }
                Hold advanced=advance(level,hold);
                entry.setValue(advanced);
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            Hold hold=HOLDS.remove(handler.getPlayer().getUUID());
            if(hold!=null) {
                ServerLevel level=server.getLevel(hold.dimension);
                if(level!=null) release(level,hold);
            }
        });
    }

    private static void setHolding(ServerPlayer player,BlockPos pos,boolean held) {
        Hold old=HOLDS.get(player.getUUID());
        if(!held) {
            if(old!=null&&old.pos.equals(pos)) {
                HOLDS.remove(player.getUUID());
                ServerLevel oldLevel=player.level().getServer().getLevel(old.dimension);
                if(oldLevel!=null) release(oldLevel,old);
            }
            return;
        }
        if(!validTarget(player,pos)) return;
        ServerLevel level=player.level();
        var state=level.getBlockState(pos);
        boolean button=state.is(Asterion.PRESSURE_BUTTON),lamenter=state.is(Asterion.LAMENTER);
        if(!button&&!lamenter) return;
        if(old!=null&&!old.pos.equals(pos)) {
            HOLDS.remove(player.getUUID());
            ServerLevel oldLevel=level.getServer().getLevel(old.dimension);
            if(oldLevel!=null) release(oldLevel,old);
        }
        if(old!=null&&old.pos.equals(pos)) return;
        BlockPos activeTarget=lamenter?pos.immutable():null;
        if(lamenter&&!state.getValue(LamenterBlock.ACTIVE))
            level.setBlock(pos,state.setValue(LamenterBlock.ACTIVE,true),Block.UPDATE_ALL);
        if(button&&!state.getValue(PressureButtonBlock.POWERED))
            level.setBlock(pos,state.setValue(PressureButtonBlock.POWERED,true),Block.UPDATE_ALL);
        HOLDS.put(player.getUUID(),new Hold(level.dimension(),pos.immutable(),0,activeTarget));
    }

    private static boolean validTarget(ServerPlayer player,BlockPos pos) {
        if(player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>6.0D*6.0D) return false;
        var hit=player.pick(6.0D,0,false);
        return hit instanceof net.minecraft.world.phys.BlockHitResult blockHit&&blockHit.getBlockPos().equals(pos);
    }

    private static boolean validHeld(ServerPlayer player,BlockPos pos) {
        if(player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>6.0D*6.0D) return false;
        var state=player.level().getBlockState(pos);
        return state.is(Asterion.PRESSURE_BUTTON)||state.is(Asterion.LAMENTER);
    }

    private static Hold advance(ServerLevel level,Hold hold) {
        var state=level.getBlockState(hold.pos);
        if(!state.is(Asterion.PRESSURE_BUTTON)) return hold;
        int progress=Math.min(HOLD_TICKS,hold.progress+1);
        BlockPos activeTarget=hold.activeTarget;
        if(progress>=HOLD_TICKS&&activeTarget==null) {
            activeTarget=nearestLamenter(level,hold.pos,48);
            if(activeTarget!=null) {
                var targetState=level.getBlockState(activeTarget);
                level.setBlock(activeTarget,targetState.setValue(LamenterBlock.ACTIVE,true),Block.UPDATE_ALL);
                level.playSound(null,activeTarget,net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN,
                    net.minecraft.sounds.SoundSource.BLOCKS,.9F,.72F);
            }
        }
        return new Hold(hold.dimension,hold.pos,progress,activeTarget);
    }

    private static void release(ServerLevel level,Hold hold) {
        var state=level.getBlockState(hold.pos);
        if(state.is(Asterion.PRESSURE_BUTTON)&&state.getValue(PressureButtonBlock.POWERED))
            level.setBlock(hold.pos,state.setValue(PressureButtonBlock.POWERED,false),Block.UPDATE_ALL);
        if(hold.activeTarget!=null) {
            var target=level.getBlockState(hold.activeTarget);
            boolean stillHeld=HOLDS.values().stream().anyMatch(other->hold.activeTarget.equals(other.activeTarget));
            if(!stillHeld&&target.is(Asterion.LAMENTER)&&target.getValue(LamenterBlock.ACTIVE))
                level.setBlock(hold.activeTarget,target.setValue(LamenterBlock.ACTIVE,false),Block.UPDATE_ALL);
        }
    }
    private static BlockPos nearestLamenter(ServerLevel level,BlockPos origin,int radius) {
        BlockPos nearest=null; double best=Double.MAX_VALUE;
        int minY=Math.max(level.getMinY(),origin.getY()-20),maxY=Math.min(level.getMaxY(),origin.getY()+20);
        for(BlockPos candidate:BlockPos.betweenClosed(origin.getX()-radius,minY,origin.getZ()-radius,
                origin.getX()+radius,maxY,origin.getZ()+radius)) {
            double distance=candidate.distSqr(origin);
            if(distance>=best||!level.getBlockState(candidate).is(Asterion.LAMENTER)) continue;
            nearest=candidate.immutable(); best=distance;
        }
        return nearest;
    }
    private record Hold(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                        BlockPos pos,int progress,BlockPos activeTarget) { }
}
