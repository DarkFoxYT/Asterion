package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.game.GasClouds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Room miniboss: telegraphed fire control, a wounded phase and water as counterplay. */
public final class CursedBrazierEntity extends PathfinderMob implements GeoEntity {
    public enum Attack { NONE, VOLLEY, FAN, ERUPTION }
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(UUID.randomUUID(),
            net.minecraft.network.chat.Component.translatable("entity.asterion.cursed_brazier"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    private int cooldown = 50, attackTicks, attacks;
    private Attack attack = Attack.NONE;
    private Vec3 aim = Vec3.ZERO, eruption = Vec3.ZERO;

    public CursedBrazierEntity(EntityType<? extends CursedBrazierEntity> type, Level level) {
        super(type, level); xpReward = 35;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.ARMOR, 4).add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 20);
    }
    public Attack attack() { return attack; }
    @Override protected void registerGoals() { }
    private boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && player.level() == level()
                && distanceToSqr(player) <= 20 * 20 && hasLineOfSight(player)
                && !WorldGenerator.isNearSafeRune((ServerLevel)level(), player.blockPosition());
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        bossBar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
        bossBar.setName(getDisplayName());
        for (var viewer : java.util.List.copyOf(bossBar.getPlayers()))
            if (!isAlive() || !eligible(viewer)) bossBar.removePlayer(viewer);
        if (!isAlive() || isNoAi()) return;
        if (isInWater()) {
            attack = Attack.NONE; cooldown = 80; GasClouds.clearOwner(level, getUUID());
            if (tickCount % 10 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    getX(), getY() + 1, getZ(), 6, .5, .15, .5, .025);
            return;
        }
        if (tickCount % 4 == 0) level.sendParticles(Asterion.BRAZIER_FIRE, getX(), getY() + .9, getZ(), 3, .4, .1, .4, .01);
        var target = level.players().stream().filter(this::eligible)
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (target == null) { attack = Attack.NONE; cooldown = Math.max(cooldown, 30); return; }
        for (var viewer : level.players()) if (eligible(viewer)) bossBar.addPlayer(viewer);
        if (attack == Attack.NONE) {
            if (--cooldown > 0) return;
            attack = Attack.values()[1 + attacks++ % 3]; attackTicks = 0;
            eruption = target.position();
            aim = target.getBoundingBox().getCenter().subtract(mouth()).normalize();
            setYRot((float)Math.toDegrees(Math.atan2(-aim.x, aim.z))); yBodyRot = getYRot(); yHeadRot = getYRot();
            playSound(net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, 1.2F, .65F);
        }
        int tick = ++attackTicks;
        if (tick < 30) {
            if (tick % 3 == 0) {
                Vec3 warning = attack == Attack.ERUPTION ? eruption.add(0, .15, 0) : mouth().add(aim.scale(1.3));
                level.sendParticles(Asterion.GREEK_FIRE_SOOT, warning.x, warning.y, warning.z,
                        attack == Attack.ERUPTION ? 14 : 5, attack == Attack.ERUPTION ? 1.3 : .2, .15, .4, .025);
            }
            return;
        }
        if (tick == 30) playSound(net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 1.3F, .65F);
        if (attack == Attack.VOLLEY && tick <= 44 && tick % 2 == 0) flame(level, mouth(), aim.scale(.7));
        if (attack == Attack.FAN && (tick == 30 || tick == 40))
            for (int side = -2; side <= 2; side++) flame(level, mouth(), aim.yRot(side * .19F).scale(.5));
        if (attack == Attack.ERUPTION && tick == 30) {
            // Only create the marked patch on visible floor; never burn through a room wall.
            var hit = level.clip(new net.minecraft.world.level.ClipContext(mouth(), eruption.add(0, .3, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    flame(level, eruption.add(Math.cos(angle) * .8, .25, Math.sin(angle) * .8), new Vec3(0, .035, 0));
                }
        }
        if (tick >= 50) { attack = Attack.NONE; cooldown = (getHealth() < getMaxHealth() * .5 ? 45 : 70) + random.nextInt(21); }
    }
    private Vec3 mouth() { return position().add(0, 1.1, 0); }
    private void flame(ServerLevel level, Vec3 from, Vec3 velocity) {
        GasClouds.emit(level, from, velocity, getUUID()); GasClouds.ignite(level, from, getUUID());
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }
    @Override public void die(DamageSource source) { clearFireControl(); super.die(source); }
    @Override public void remove(RemovalReason reason) { clearFireControl(); super.remove(reason); }
    private void clearFireControl() {
        bossBar.removeAllPlayers(); attack = Attack.NONE;
        if (level() instanceof ServerLevel level) GasClouds.clearOwner(level, getUUID());
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        spawnAtLocation(level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 3));
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
