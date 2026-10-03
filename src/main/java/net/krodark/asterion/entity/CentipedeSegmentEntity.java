package net.krodark.asterion.entity;

import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Lightweight damage/interaction proxy: no AI, physics, independent health, or saved state. */
public final class CentipedeSegmentEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(CentipedeSegmentEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEGMENT=SynchedEntityData.defineId(CentipedeSegmentEntity.class,EntityDataSerializers.INT);
    public CentipedeSegmentEntity(EntityType<? extends CentipedeSegmentEntity> type,Level level){super(type,level);setNoGravity(true);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(OWNER,-1);builder.define(SEGMENT,0);}
    public void configure(ScarletCentipedeEntity owner,int segment){entityData.set(OWNER,owner.getId());entityData.set(SEGMENT,segment);follow(owner);}
    public ScarletCentipedeEntity owner(){return level().getEntity(entityData.get(OWNER)) instanceof ScarletCentipedeEntity mob?mob:null;}
    public void follow(ScarletCentipedeEntity owner){var pose=owner.chainPose(entityData.get(SEGMENT),1);setPos(pose.position());setBoundingBox(CentipedeInteraction.bounds(entityData.get(SEGMENT),pose));}
    @Override public void tick(){tickCount++;var owner=owner();if(owner==null){if(!level().isClientSide() || tickCount>60)discard();return;}
        if(!owner.isAlive() || entityData.get(SEGMENT)>=owner.chainSegmentCount()){discard();return;}if(level().isClientSide())follow(owner);}
    @Override public boolean isPickable(){return owner()!=null && owner().isAlive();}
    @Override public boolean canBeHitByProjectile(){return isPickable();}
    @Override public boolean hurtServer(ServerLevel level,net.minecraft.world.damagesource.DamageSource source,float amount){var owner=owner();return owner!=null && owner.hurtServer(level,source,amount);}
    @Override public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,Vec3 hit){
        var owner=owner();if(owner==null)return net.minecraft.world.InteractionResult.PASS;
        return level().isClientSide() || owner.mountSegment(player,entityData.get(SEGMENT))?net.minecraft.world.InteractionResult.SUCCESS:net.minecraft.world.InteractionResult.PASS;
    }
    @Override protected void addAdditionalSaveData(ValueOutput output){}
    @Override protected void readAdditionalSaveData(ValueInput input){}
}
