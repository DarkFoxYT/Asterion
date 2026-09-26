package net.krodark.asterion.update.underworld;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.LimboWanderers;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.gamerules.GameRules;

/** Persistent cave populations: bounded local density, no forced chunk loading. */
public final class SpiderPopulation {
    public static final int LOCAL_CAP=12;
    private SpiderPopulation() { }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var level=server.getLevel(Asterion.LIMBO_LEVEL);
            if(level==null || level.getGameTime()%80!=0 || level.getDifficulty()==Difficulty.PEACEFUL
                    || !level.getGameRules().get(GameRules.SPAWN_MOBS))return;
            int budget=3;
            for(var player:level.players()) {
                if(budget<=0)break;
                if(!player.isAlive() || player.isSpectator() || player.getZ()>-40
                        || level.getEntitiesOfClass(LimboSpiderEntity.class,player.getBoundingBox().inflate(80)).size()>=LOCAL_CAP)continue;
                for(int attempt=0;attempt<20;attempt++) {
                    double angle=player.getRandom().nextDouble()*Math.PI*2,range=24+player.getRandom().nextInt(37);
                    int x=(int)Math.floor(player.getX()+Math.cos(angle)*range),z=(int)Math.floor(player.getZ()+Math.sin(angle)*range);
                    if(z>-32 || z<UnderworldTerrain.START_Z+24 || !level.getChunkSource().hasChunk(x>>4,z>>4)
                            || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(x>>4,z>>4)))continue;
                    var feet=LimboWanderers.findFloor(level,x,z,player.getBlockY()+20,player.getBlockY()-28);
                    if(feet==null || level.players().stream().anyMatch(p->p.distanceToSqr(x+.5,feet.getY(),z+.5)<20*20))continue;
                    if(level.getEntitiesOfClass(LimboSpiderEntity.class,new net.minecraft.world.phys.AABB(feet).inflate(12)).size()>=3)continue;
                    var spider=UnderworldContent.SPIDER.create(level,EntitySpawnReason.NATURAL);
                    if(spider==null)break;
                    spider.setPos(x+.5,feet.getY(),z+.5);
                    if(!level.noCollision(spider) || !level.isUnobstructed(spider))continue;
                    spider.settleNaturally();
                    if(level.addFreshEntity(spider))budget--;
                    break;
                }
            }
        });
    }
}
