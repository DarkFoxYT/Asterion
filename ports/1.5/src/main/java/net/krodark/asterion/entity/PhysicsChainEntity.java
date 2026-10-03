package net.krodark.asterion.entity;

import net.krodark.asterion.physics.SegmentedChain;
import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;

/** A hanging metal chain; both sides simulate the same bounded climbing envelope. */
public final class PhysicsChainEntity extends Entity {
    private static final EntityDataAccessor<Integer> LENGTH = SynchedEntityData.defineId(PhysicsChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> SUPPORT = SynchedEntityData.defineId(PhysicsChainEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> PARENT = SynchedEntityData.defineId(PhysicsChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SPAN = SynchedEntityData.defineId(PhysicsChainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> END_SUPPORT = SynchedEntityData.defineId(PhysicsChainEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<org.joml.Vector3f> END_OFFSET = SynchedEntityData.defineId(PhysicsChainEntity.class, EntityDataSerializers.VECTOR3);
    private java.util.UUID parentUUID;
    private boolean attached=true;
    private SegmentedChain chain;
    private int simulatedLength;
    private int settledTicks;
    public PhysicsChainEntity(EntityType<? extends PhysicsChainEntity> type, Level level) {
        super(type, level); setNoGravity(true);
    }
    //? if >=1.20.5 {
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(LENGTH, 8); data.define(SUPPORT, BlockPos.ZERO);data.define(PARENT,-1);
        data.define(SPAN,false);data.define(END_SUPPORT,BlockPos.ZERO);data.define(END_OFFSET,new org.joml.Vector3f());
    }
    //?} else {
    /*    @Override protected void defineSynchedData() {
        entityData.define(LENGTH, 8); entityData.define(SUPPORT, BlockPos.ZERO);entityData.define(PARENT,-1);
        entityData.define(SPAN,false);entityData.define(END_SUPPORT,BlockPos.ZERO);entityData.define(END_OFFSET,new org.joml.Vector3f());
    }*/
    //?}

    public void configure(BlockPos support, Vec3 anchor, int length) {
        configure(support,anchor,length,true);
    }
    public void configure(BlockPos support,Vec3 anchor,int length,boolean attached) {
        this.attached=attached;entityData.set(SUPPORT,support);entityData.set(LENGTH,net.krodark.asterion.port.compat.MathCompat.clamp(length,1,64));setPos(anchor);
    }
    public boolean extend(Player player,net.minecraft.world.item.ItemStack stack) {
        if(isSpan())return false;
        var endpoint=endPoint();
        double reach=3.75;
        if(player.getEyePosition().distanceToSqr(endpoint)>reach*reach
                || !player.mayUseItemAt(BlockPos.containing(endpoint),net.minecraft.core.Direction.DOWN,stack))return false;
        var child=childChain();if(child!=null)return child.extend(player,stack);
        var below=BlockPos.containing(endpoint.add(0,-.5,0));
        if(!level().getChunkSource().hasChunk(below.getX()>>4,below.getZ()>>4))return false;
        if(!level().getBlockState(below).getCollisionShape(level(),below).isEmpty())return false;
        if(!level().isClientSide()) {
            if(length()<64)entityData.set(LENGTH,length()+1);
            else {
                var next=new PhysicsChainEntity(ChainLiftContent.PHYSICS_CHAIN,level());
                next.configure(entityData.get(SUPPORT),endpoint,1,false);next.connectTo(this);
                if(!level().addFreshEntity(next))return false;
            }
            settledTicks=0;if(!player.isCreative())stack.shrink(1);
            level().playSound(null,below,net.minecraft.sounds.SoundEvents.CHAIN_PLACE,net.minecraft.sounds.SoundSource.BLOCKS,1,.9F);
        }
        return true;
    }
    public int length() { return entityData.get(LENGTH); }
    public boolean isSpan(){return entityData.get(SPAN);}
    private Vec3 endpoint(){var offset=entityData.get(END_OFFSET);return isSpan()?position().add(offset.x(),offset.y(),offset.z()):position().add(0,-length(),0);}
    public void configureSpan(BlockPos support,Vec3 start,BlockPos endSupport,Vec3 end,double slack) {
        configure(support,start,(int)Math.ceil(start.distanceTo(end)+slack),true);
        entityData.set(SPAN,true);entityData.set(END_SUPPORT,endSupport);
        Vec3 delta=end.subtract(start);entityData.set(END_OFFSET,new org.joml.Vector3f((float)delta.x,(float)delta.y,(float)delta.z));
        setBoundingBox(makeBoundingBox());
    }
    public Vec3 endPoint() {return chain==null?endpoint():chain.endPoint();}
    public boolean connectTo(PhysicsChainEntity parent) {
        if(parent==this || parent.level()!=level() || parent.getY()<=getY()+.25)return false;
        parentUUID=parent.getUUID();entityData.set(PARENT,parent.getId());attached=false;settledTicks=0;
        setPos(parent.endPoint());return true;
    }
    public PhysicsChainEntity parentChain() {
        var entity=level().getEntity(entityData.get(PARENT));
        if(entity instanceof PhysicsChainEntity parent && parent.isAlive())return parent;
        if(parentUUID!=null && level() instanceof ServerLevel server && server.getEntity(parentUUID) instanceof PhysicsChainEntity parent && parent.isAlive()) {
            entityData.set(PARENT,parent.getId());return parent;
        }
        return null;
    }
    public PhysicsChainEntity childChain() {
        for(var candidate:level().getEntitiesOfClass(PhysicsChainEntity.class,new AABB(endPoint(),endPoint()).inflate(1.5)))
            if(candidate!=this && candidate.isAlive() && (candidate.entityData.get(PARENT)==getId() || getUUID().equals(candidate.parentUUID)))return candidate;
        return null;
    }
    @Override protected AABB makeBoundingBox() {
        Vec3 position = position();
        if(isSpan()) {
            Vec3 end=endpoint(),delta=end.subtract(position);double rest=length()*length();
            double x=.5*(Math.sqrt(Math.max(0,rest-delta.y*delta.y-delta.z*delta.z))-Math.abs(delta.x))+.6;
            double y=.5*(Math.sqrt(Math.max(0,rest-delta.x*delta.x-delta.z*delta.z))-Math.abs(delta.y))+.6;
            double z=.5*(Math.sqrt(Math.max(0,rest-delta.x*delta.x-delta.y*delta.y))-Math.abs(delta.z))+.6;
            return new AABB(position,end).inflate(Math.max(.6,x),Math.max(.6,y),Math.max(.6,z));
        }
        return new AABB(position.x - 1, position.y - length() - .5, position.z - 1,
                position.x + 1, position.y + .2, position.z + 1);
    }
    @Override public void tick() {
        super.tick();
        var parent=parentChain();
        if(parent!=null) {
            Vec3 anchor=parent.endPoint();
            if(position().distanceToSqr(anchor)>1e-8){setPos(anchor);settledTicks=0;}
        } else if(!attached && parentUUID==null && level() instanceof ServerLevel && tickCount%20==0) {
            // Repair adjoining sections in existing saves as well as newly authored chains.
            for(var candidate:level().getEntitiesOfClass(PhysicsChainEntity.class,new AABB(position(),position()).inflate(1.25)))
                if(candidate!=this && candidate.getY()>getY()+.25 && candidate.endPoint().distanceToSqr(position())<.75*.75
                        && candidate.childChain()==null){connectTo(candidate);break;}
        }
        setBoundingBox(makeBoundingBox());
        if (attached && level() instanceof ServerLevel server && tickCount % 20 == 0
                && level().getChunkSource().hasChunk(entityData.get(SUPPORT).getX() >> 4, entityData.get(SUPPORT).getZ() >> 4)
                && (level().getBlockState(entityData.get(SUPPORT)).getCollisionShape(level(), entityData.get(SUPPORT)).isEmpty()
                || isSpan() && level().getChunkSource().hasChunk(entityData.get(END_SUPPORT).getX()>>4,entityData.get(END_SUPPORT).getZ()>>4)
                && level().getBlockState(entityData.get(END_SUPPORT)).getCollisionShape(level(),entityData.get(END_SUPPORT)).isEmpty())) {
            if (server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOENTITYDROPS))
                spawnAtLocation(new net.minecraft.world.item.ItemStack(ChainLiftContent.PHYSICS_CHAIN_ITEM,length()));
            discard(); return;
        }
        if(!level().isClientSide()) {
            boolean nearby=false;
            AABB awakeBox=getBoundingBox().inflate(4);
            for(Player player:level().players())if(!player.isSpectator() && player.getBoundingBox().intersects(awakeBox)) {nearby=true;break;}
            // Server motion is needed only for nearby grips. Distant cosmetic chains need no
            // node allocations, block ray casts or constraint sweeps; never load chunks for it.
            if(!nearby){if(chain!=null)chain.rest();return;}
        }
        if (chain == null || simulatedLength != length()) {
            simulatedLength = length();
            chain = chain == null ? new SegmentedChain(position(), endpoint(), Math.min(96,length() * 4), length())
                    : chain.extended(Math.min(96,length()*4),length());
            if(isSpan())chain.sag(position(),endpoint());
        }
        java.util.List<Vec3> bodies = java.util.List.of();
        AABB interactionBox=getBoundingBox().inflate(.5);
        for (Player player : level().players()) {
            if(player.isSpectator() || !player.getBoundingBox().intersects(interactionBox)
                    || net.krodark.asterion.physics.ChainGrip.holds(player,this))continue;
            if(bodies.isEmpty())bodies=new java.util.ArrayList<>();
            bodies.add(player.position().add(0, .45, 0));
            bodies.add(player.position().add(0, 1.25, 0));
        }
        if(bodies.isEmpty() && chain.isSettled())settledTicks++;else settledTicks=0;
        if(settledTicks>20 && tickCount%20!=0) {chain.rest();return;}
        chain.step(position(), endpoint(), isSpan(), (from, to) -> {
            // Prevent load-bearing links from swinging outside the server climbing envelope.
            Vec3 horizontal = to.subtract(position()).multiply(1, 0, 1);
            if (!isSpan() && horizontal.lengthSqr() > .36) to = new Vec3(position().x, to.y, position().z).add(horizontal.normalize().scale(.6));
            var fromBlock = BlockPos.containing(from);
            var toBlock=BlockPos.containing(to);
            if(!level().getChunkSource().hasChunk(fromBlock.getX()>>4,fromBlock.getZ()>>4)
                    || !level().getChunkSource().hasChunk(toBlock.getX()>>4,toBlock.getZ()>>4))return from;
            if (fromBlock.equals(BlockPos.containing(to)) && level().getBlockState(fromBlock).isAir()) return to;
            var hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.035));
        }, bodies);
    }
    public Vec3[] points(float partial) {
        Vec3[] points=chain==null?new Vec3[]{position(),endpoint()}:chain.rendered(partial);
        var parent=parentChain();
        if(parent!=null) {var upstream=parent.chain==null?new Vec3[]{parent.position(),parent.endPoint()}:parent.chain.rendered(partial);points[0]=upstream[upstream.length-1];}
        return points;
    }
    public Vec3 parentTangent(float partial) {
        var parent=parentChain();if(parent==null)return null;
        Vec3[] points=parent.points(partial);return points[points.length-1].subtract(points[points.length-2]).normalize();
    }
    public Vec3 gripPoint(Vec3 body) {
        if(chain!=null)return chain.closestPoint(body);
        Vec3 delta=endpoint().subtract(position());
        return position().add(delta.scale(net.krodark.asterion.port.compat.MathCompat.clamp(body.subtract(position()).dot(delta)/Math.max(1e-9,delta.lengthSqr()),0,1)));
    }
    public Vec3 gripTangent(Vec3 body) {
        Vec3 direction=chain==null?endpoint().subtract(position()).normalize():chain.tangentAt(body);
        Vec3 delta=endpoint().subtract(position());
        boolean ascending=isSpan()?Math.abs(delta.y)>delta.horizontalDistance() && delta.y<0:direction.y<0;
        return ascending?direction.scale(-1):direction;
    }
    public static boolean canClimb(LivingEntity body) {
        if (!(body instanceof Player player)) return false;
        var center=net.krodark.asterion.physics.ChainGrip.center(player);
        return center!=null && center.distanceToSqr(body.position().add(0,.9,0))<1.2*1.2;
    }
    @Override public net.minecraft.world.InteractionResult interactAt(Player player, Vec3 hit, net.minecraft.world.InteractionHand hand) {
        if(player.getItemInHand(hand).is(ChainLiftContent.PHYSICS_CHAIN_ITEM)
                && player.getEyePosition().distanceToSqr(endPoint())<Math.pow(3.75,2))
            return extend(player,player.getItemInHand(hand))?net.minecraft.world.InteractionResult.SUCCESS:net.minecraft.world.InteractionResult.FAIL;
        return net.krodark.asterion.physics.ChainGrip.grab(player,this)
                ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
    }
    @Override public boolean hurt( DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof Player player && player.isShiftKeyDown()) {
            if (!player.isCreative()) spawnAtLocation(new net.minecraft.world.item.ItemStack(ChainLiftContent.PHYSICS_CHAIN_ITEM,length()));
            discard(); return true;
        }
        return false;
    }
    @Override public boolean isPickable() { return true; }
    @Override protected void addAdditionalSaveData(CompoundTag out) {
        out.putInt("Length", length()); out.putLong("Support", entityData.get(SUPPORT).asLong());
        out.putBoolean("Attached",attached);
        out.putBoolean("Span",isSpan());out.putLong("EndSupport",entityData.get(END_SUPPORT).asLong());
        var end=entityData.get(END_OFFSET);out.putFloat("EndX",end.x());out.putFloat("EndY",end.y());out.putFloat("EndZ",end.z());
        if(parentUUID!=null)out.putString("ParentChain",parentUUID.toString());
    }
    @Override protected void readAdditionalSaveData(CompoundTag in) {
        entityData.set(LENGTH, net.krodark.asterion.port.compat.MathCompat.clamp(net.krodark.asterion.port.compat.NbtCompat.getInt(in, "Length", 8), 1, 64));
        attached=net.krodark.asterion.port.compat.NbtCompat.getBoolean(in, "Attached",true);
        entityData.set(SPAN,net.krodark.asterion.port.compat.NbtCompat.getBoolean(in, "Span",false));entityData.set(END_SUPPORT,BlockPos.of(net.krodark.asterion.port.compat.NbtCompat.getLong(in, "EndSupport",0)));
        entityData.set(END_OFFSET,new org.joml.Vector3f(net.krodark.asterion.port.compat.NbtCompat.getFloat(in, "EndX",0),net.krodark.asterion.port.compat.NbtCompat.getFloat(in, "EndY",0),net.krodark.asterion.port.compat.NbtCompat.getFloat(in, "EndZ",0)));
        try { parentUUID=java.util.UUID.fromString(in.getString("ParentChain")); } catch(IllegalArgumentException ignored){parentUUID=null;}
        entityData.set(SUPPORT, BlockPos.of(net.krodark.asterion.port.compat.NbtCompat.getLong(in, "Support", 0)));
    }
}
