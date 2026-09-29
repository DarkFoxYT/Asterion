package net.krodark.asterion.update.underworld;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.LimboWanderers;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.gamerules.GameRules;

/** Persistent off-path populations, bounded per tick and restricted to loaded terrain. */
public final class SpiderPopulation {
    public static final int LOCAL_CAP=6;
    private static final int SPAWN_INTERVAL=200;
    private SpiderPopulation() { }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var level=server.getLevel(Asterion.LIMBO_LEVEL);
            if(level==null || level.getGameTime()%SPAWN_INTERVAL!=0 || level.getDifficulty()==Difficulty.PEACEFUL
                    || !level.getGameRules().get(GameRules.SPAWN_MOBS))return;
            int budget=1;
            for(var player:level.players()) {
                if(budget<=0)break;
                if(!player.isAlive() || player.isSpectator())continue;
                int nearby=level.getEntitiesOfClass(LimboSpiderEntity.class,player.getBoundingBox().inflate(80)).size();
                for(int attempt=0;attempt<16 && budget>0 && nearby<LOCAL_CAP;attempt++) {
                    double angle=player.getRandom().nextDouble()*Math.PI*2,range=10+player.getRandom().nextInt(39);
                    int x=(int)Math.floor(player.getX()+Math.cos(angle)*range),z=(int)Math.floor(player.getZ()+Math.sin(angle)*range);
                    if(UnderworldTerrain.isMainPath(x+.5,z+.5)
                            || !level.getChunkSource().hasChunk(x>>4,z>>4)
                            || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(x>>4,z>>4)))continue;
                    var feet=LimboWanderers.findFloor(level,x,z,player.getBlockY()+24,player.getBlockY()-32);
                    if(feet==null
                            || level.players().stream().anyMatch(p->p.distanceToSqr(x+.5,feet.getY(),z+.5)<8*8))continue;
                    var spider=UnderworldContent.SPIDER.create(level,EntitySpawnReason.NATURAL);
                    if(spider==null)break;
                    spider.setPos(x+.5,feet.getY(),z+.5);
                    if(!level.noCollision(spider) || !level.isUnobstructed(spider))continue;
                    spider.settleNaturally();
                    if(level.addFreshEntity(spider)) { budget--;nearby++; }
                }
            }
        });
    }
}
