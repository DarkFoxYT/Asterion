package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.block.GreekBrazierBlock;
import net.krodark.asterion.entity.MinotaurAnimationTiming;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.worldgen.CatacombArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

final class MinotaurRageCheck {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void run(ServerLevel level, ServerPlayer player) {
        var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
        Map<BlockPos, BlockState> saved = new HashMap<>();
        try {
            for (var track : List.of(MinotaurAnimationTiming.ROAR, MinotaurAnimationTiming.ENTRY_ROAR, MinotaurAnimationTiming.FIRE_ROAR)) {
                int tick = track.roarSoundTick();
                check(track.seconds(tick - 1) < 2.5 && track.seconds(tick) >= 2.5, "Sound not on frame-60 crossing");
            }
            boss.setPos(0, 37, 0);
            call(boss, "beginBossIntercept", new Class[]{ServerPlayer.class}, player);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos pos = CatacombArena.brazier(side);
                for (int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) saved.put(pos.offset(x,0,z),level.getBlockState(pos.offset(x,0,z)));
                GreekBrazierBlock.relight(level, pos);
            }
            check(WorldGenerator.activeBossBraziers(level) == 4, "Missing arena braziers");
            call(boss,"setRage",new Class[]{int.class},12);
            GreekBrazierBlock.extinguish(level,CatacombArena.brazier(Direction.NORTH));
            GreekBrazierBlock.extinguish(level,CatacombArena.brazier(Direction.SOUTH));
            call(boss,"tickBrazierRage",new Class[]{ServerLevel.class},level);
            check(boss.rage() == 4,"Extinguished braziers did not weaken phase one");
            Map<BlockPos,Long> deadlines=(Map<BlockPos,Long>)field(boss,"brazierRelights");
            for(long deadline:deadlines.values()) check(deadline-level.getGameTime()>=600 && deadline-level.getGameTime()<=900,"Relight outside 30-45 seconds");
            deadlines.replaceAll((pos,time)->level.getGameTime());
            call(boss,"tickBrazierRage",new Class[]{ServerLevel.class},level);
            check(WorldGenerator.activeBossBraziers(level)==4 && boss.rage()==5,"Relighting did not gradually restore rage");
            call(boss,"syncBossBars",new Class[]{ServerLevel.class},level);
            var bar=(net.minecraft.server.level.ServerBossEvent)field(boss,"rageBossBar");
            check(!bar.isVisible() && bar.getPlayers().isEmpty(),"Phase-one rage bar leaked");
            var stage=MinotaurEntity.class.getDeclaredField("bossStage");stage.setAccessible(true);
            stage.set(boss,Enum.valueOf((Class)stage.getType(),"EXTREME"));
            call(boss,"setRage",new Class[]{int.class},0);
            check(boss.rage()==12,"Phase two rage was not locked at maximum");
            check((boolean)call(boss,"tryMaxRageRoar",new Class[]{ServerPlayer.class},player),"Max rage did not trigger roar");
            for(int tick=0;tick<150;tick++) {
                call(boss,"tickBossAttack",new Class[]{ServerLevel.class,ServerPlayer.class},level,player);
                if(tick==60) check(boss.animationState()==MinotaurEntity.AnimationState.ROAR_START
                        && boss.getDeltaMovement().horizontalDistanceSqr()==0,"Combat roar was not animated/stationary");
            }
            check(field(boss,"bossAttack").toString().equals("NONE"),"Roar failed to finish");
            check(!(boolean)call(boss,"tryMaxRageRoar",new Class[]{ServerPlayer.class},player),"Roar repeated at maximum rage");
            BlockPos wet = CatacombArena.brazier(Direction.NORTH);
            GreekBrazierBlock.extinguish(level,wet);
            level.setBlock(wet,level.getBlockState(wet).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true),2);
            check(!GreekBrazierBlock.relight(level,wet),"Relit a submerged brazier");
            var pendingField=net.krodark.asterion.worldgen.ArenaDebris.class.getDeclaredField("PENDING");pendingField.setAccessible(true);
            var pending=(Map<ServerLevel,List<net.krodark.asterion.network.ArenaDebrisPayload.Fragment>>)pendingField.get(null);
            int before=pending.getOrDefault(level,List.of()).size();
            WorldGenerator.collapseBossRoofRing(level,boss.position(),5);
            var fragments=pending.get(level).subList(before,pending.get(level).size());
            Set<Integer> quadrants=new HashSet<>();
            for(var fragment:fragments) quadrants.add((fragment.position().x>0?1:0)+(fragment.position().z>0?2:0));
            check(fragments.size()==32 && quadrants.size()==4,"Roof debris is not distributed across every quadrant");
            check(fragments.stream().allMatch(fragment -> fragment.scale() >= 1.25F),
                    "Roof collapse did not use heavy rubble pieces");
            Asterion.LOGGER.info("PASS: roar frame 60, once-only combat roar, hidden phase-one rage, brazier weakening/timed relight and maximum phase-two rage");
            Asterion.LOGGER.info("PASS: submerged braziers stay extinguished and bounded roof debris covers all quadrants");
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { saved.forEach((pos,state)->level.setBlock(pos,state,2));boss.discard(); }
    }
    private static Object call(Object target,String name,Class<?>[] types,Object... args) throws ReflectiveOperationException {
        var method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);
    }
    private static Object field(Object target,String name) throws ReflectiveOperationException {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
