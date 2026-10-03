package net.krodark.asterion.physics;

import net.krodark.asterion.entity.PhysicsChainEntity;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Explicit grip selection. Vanilla entity interaction predicts the same selection on both sides. */
public final class ChainGrip {
    // Client and integrated-server players share ids, but must never share grip state.
    private static final ChainGripSelection<Level,Player,Entity> GRIPS = new ChainGripSelection<>();
    private static final java.util.Map<Player,Long> HELD_UNTIL=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private ChainGrip() { }
    public static boolean grab(Player player, Entity chain) {
        if (!eligible(player) || !chain.isAlive() || chain.level()!=player.level()
                || !(chain instanceof PhysicsChainEntity || chain instanceof MinotaurAxeEntity weapon && weapon.chainSide()!=0)) return false;
        var eye=player.getEyePosition();
        var closest=chain instanceof PhysicsChainEntity hanging?hanging.gripPoint(eye)
                :((MinotaurAxeEntity)chain).chainGripPoint(eye);
        double reach=player.entityInteractionRange()+.35;
        if (closest==null || eye.distanceToSqr(closest)>reach*reach) return false;
        player.setSprinting(false);
        if(player.getAbilities().flying){player.getAbilities().flying=false;player.onUpdateAbilities();}
        var held=target(player);
        if(held!=null && held!=chain)return true;
        if(held!=chain)
            GRIPS.select(player.level(),player.getUUID(),player,chain);
        HELD_UNTIL.put(player,player.level().getGameTime()+15);
        return true;
    }
    public static void setHeld(Player player,boolean held) {
        if(!held) { release(player); return; }
        if(GRIPS.target(player.level(),player.getUUID(),player)!=null)
            HELD_UNTIL.put(player,player.level().getGameTime()+15);
    }
    public static void release(Player player) {
        GRIPS.release(player.level(),player.getUUID());HELD_UNTIL.remove(player);
    }
    public static Entity target(Player player) {
        Entity chain=GRIPS.target(player.level(),player.getUUID(),player);
        if (chain==null) return null;
        if (!player.level().isClientSide() && player.level().getGameTime()>HELD_UNTIL.getOrDefault(player,0L)) {
            release(player);return null;
        }
        double reach=player.entityInteractionRange()+1;
        var body=player.getBoundingBox();
        if (!eligible(player) || player.getAbilities().flying || !chain.isAlive() || chain.level()!=player.level()
                || !chain.getBoundingBox().inflate(reach).intersects(body)
                || chain instanceof MinotaurAxeEntity weapon && weapon.chainSide()==0) {
            release(player); return null;
        }
        return chain;
    }
    public static boolean holds(Player player, Entity chain) { return target(player)==chain; }
    public static net.minecraft.world.phys.Vec3 center(Player player) {
        Entity selected=target(player);
        var body=player.position().add(0,.9,0);
        if(selected instanceof PhysicsChainEntity chain) {
            return chain.gripPoint(body);
        }
        if(selected instanceof MinotaurAxeEntity weapon)return weapon.chainGripPoint(body);
        return null;
    }
    private static boolean eligible(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !MinotaurEntity.isHeld(player);
    }
}
