package net.krodark.asterion.test;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
public final class PolishClientSmoke {
    int title, ticks;
    boolean started;
    final long deadline=System.nanoTime()+480_000_000_000L;
    public void onInitializeClient() {
        if (Boolean.getBoolean("asterion.polishSmoke")) ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void data(MinotaurEntity b,String field,Object value) {
        try {var f=MinotaurEntity.class.getDeclaredField(field);f.setAccessible(true);b.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor)f.get(null),value);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static int ordinal(String type,String name) { for(var k:MinotaurEntity.class.getDeclaredClasses()) if(k.getSimpleName().equals(type)) for(var v:k.getEnumConstants()) if(((Enum<?>)v).name().equals(name)) return ((Enum<?>)v).ordinal(); throw new AssertionError(name); }
    private void tick(Minecraft c) {
        if(System.nanoTime()>deadline)throw new AssertionError("26.1.2 smoke timed out");
        if (!started && c.screen instanceof TitleScreen && c.getOverlay()==null && ++title>20) {
            started=true;c.options.pauseOnLostFocus=false;c.options.renderDistance().set(4);
            net.krodark.asterion.AsterionConfig.INSTANCE.cinematicsEnabled=false;
            var settings=new net.minecraft.world.level.LevelSettings("Procedural smoke",net.minecraft.world.level.GameType.CREATIVE,net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT,true,net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
            c.createWorldOpenFlows().createFreshLevel("procedural-"+System.currentTimeMillis(),settings,new net.minecraft.world.level.levelgen.WorldOptions(321L,false,false),net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions,new TitleScreen());
        }
        if(c.level==null || c.player==null || c.getSingleplayerServer()==null)return;
        ticks++;
        if(ticks==40)c.getSingleplayerServer().execute(()->{
            var s=c.getSingleplayerServer();var source=s.createCommandSourceStack();
            for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++)s.overworld().getChunk(x,z);
            s.getCommands().performPrefixedCommand(source,"tp @a 0 180 -5 0 0");
            s.getCommands().performPrefixedCommand(source,"fill -8 179 -8 8 179 8 minecraft:stone");
            s.getCommands().performPrefixedCommand(source,"summon asterion:minotaur 0 180 3 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180.0f,0.0f]}");
        });
        if(ticks>60) {
            c.player.setPos(.5,180,-5);c.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            c.player.setYRot(0);c.player.yRotO=0;c.player.setXRot(-15);c.player.xRotO=-15;
            var boss=c.level.getEntitiesOfClass(MinotaurEntity.class,c.player.getBoundingBox().inflate(30)).stream().findFirst().orElse(null);
            if(boss==null) { ticks--;return; }
            data(boss,"DATA_PHASE",ordinal("BehaviorPhase","BOSS"));
            boss.yHeadRot=boss.yHeadRotO=boss.yBodyRot+(float)Math.sin(ticks*.06)*65;
            if(ticks>100 && ticks<240) {
                data(boss,"DATA_BOSS_ATTACK",ordinal("BossAttack","GRAB"));data(boss,"DATA_BOSS_ATTACK_TICKS",(ticks-100)%62);
                data(boss,"DATA_HELD_PLAYER",(ticks-100)%62>20 && (ticks-100)%62<46?c.player.getId():-1);data(boss,"DATA_REACH_ARM",1);data(boss,"DATA_GRAB_TARGET_ID",c.player.getId());
            }
        }
        if(ticks==270) {
            Asterion.LOGGER.info("ASTERION_26_POLISH PASSED: world load, Minotaur renderer, changing procedural look and attack timelines");
            c.stop();
        }
    }
}



