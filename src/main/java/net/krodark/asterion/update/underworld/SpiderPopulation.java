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
    public static final int LOCAL_CAP=8;
    private SpiderPopulation() { }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var level=server.getLevel(Asterion.LIMBO_LEVEL);
            if(level==null || level.getGameTime()%60!=0 || level.getDifficulty()==Difficulty.PEACEFUL
                    || !level.getGameRules().get(GameRules.SPAWN_MOBS))return;
            int budget=2;
            for(var player:level.players()) {
                if(budget<=0)break;
                if(!player.isAlive() || player.isSpectator() || player.getZ()>-40
                        || level.getEntitiesOfClass(LimboSpiderEntity.class,player.getBoundingBox().inflate(80)).size()>=LOCAL_CAP)continue;
                int slot=Math.floorDiv(player.getBlockZ()-UnderworldTerrain.SPAWN_Z,80);
                var chamber=UnderworldTerrain.chamberCenter(slot);
                if(player.distanceToSqr(chamber.getX()+.5,chamber.getY(),chamber.getZ()+.5)>88*88
                        || level.getEntitiesOfClass(LimboSpiderEntity.class,new net.minecraft.world.phys.AABB(chamber).inflate(28)).size()>=5)continue;
                for(int attempt=0;attempt<24;attempt++) {
                    double angle=player.getRandom().nextDouble()*Math.PI*2,range=4+player.getRandom().nextInt(13);
                    int x=(int)Math.floor(chamber.getX()+Math.cos(angle)*range),z=(int)Math.floor(chamber.getZ()+Math.sin(angle)*range);
                    if(z>-32 || z<UnderworldTerrain.START_Z+24 || !UnderworldTerrain.inChamber(new net.minecraft.core.BlockPos(x,chamber.getY(),z))
                            || !level.getChunkSource().hasChunk(x>>4,z>>4)
                            || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(x>>4,z>>4)))continue;
                    var feet=LimboWanderers.findFloor(level,x,z,chamber.getY()+20,chamber.getY()-24);
                    if(feet==null || !UnderworldTerrain.inChamber(feet)
                            || level.players().stream().anyMatch(p->p.distanceToSqr(x+.5,feet.getY(),z+.5)<8*8))continue;
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
