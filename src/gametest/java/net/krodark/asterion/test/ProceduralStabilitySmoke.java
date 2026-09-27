package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/** Exercises GeckoLib's actual channel-reset lifecycle with alternating entities. */
public final class ProceduralStabilitySmoke {
    public static void run(ClientLevel level) {
        try { runChecked(level); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void runChecked(ClientLevel level) throws ReflectiveOperationException {
        var apply=Class.forName("net.krodark.asterion.port.client.PortMinotaurPose").getDeclaredMethod("apply",MinotaurEntity.class,java.util.Collection.class,float.class);
        apply.setAccessible(true);
        var model = new GeoModel<MinotaurEntity>() {
            public ResourceLocation getModelResource(MinotaurEntity b) { return Asterion.id("unused"); }
            public ResourceLocation getTextureResource(MinotaurEntity b) { return Asterion.id("unused"); }
            public ResourceLocation getAnimationResource(MinotaurEntity b) { return Asterion.id("unused"); }
        };
        var processor = model.getAnimationProcessor();
        var tickAnimation=java.util.Arrays.stream(processor.getClass().getMethods()).filter(m->m.getName().equals("tickAnimation")).findFirst().orElseThrow();
        var stateConstructor=tickAnimation.getParameterTypes()[4].getConstructors()[0];
        for (String name : new String[]{"body","neck","head","leftshoulder","rightshoulder","rightarm","lowerrightarm"})
            processor.registerGeoBone(new GeoBone(null,name,false,0D,false,false));
        var bosses = new MinotaurEntity[]{new MinotaurEntity(Asterion.MINOTAUR,level),new MinotaurEntity(Asterion.MINOTAUR,level)};
        for (var boss : bosses) boss.getAnimatableInstanceCache().getManagerForId(boss.getId()).getAnimationControllers().clear();
        for (int frame=0;frame<6000;frame++) {
            var boss=bosses[frame%2];
            double time=frame/12.0;
            boss.tickCount=(int)time;
            boss.yHeadRot=boss.yHeadRotO=(frame%2==0?1:-1)*65;
            boss.setXRot(30);boss.xRotO=30;
            float partial=(float)(time-Math.floor(time));
            var state=stateConstructor.newInstance(boss,0F,0F,partial,false);
            tickAnimation.invoke(processor,boss,model,boss.getAnimatableInstanceCache().getManagerForId(boss.getId()),time,state,true);
            apply.invoke(null,boss,processor.getRegisteredBones(),partial);
            for (var bone : processor.getRegisteredBones()) {
                if (!Float.isFinite(bone.getRotX()) || !Float.isFinite(bone.getRotY()) || !Float.isFinite(bone.getRotZ())
                        || Math.abs(bone.getRotX())>2 || Math.abs(bone.getRotY())>2 || Math.abs(bone.getRotZ())>2)
                    throw new AssertionError("Procedural rotation accumulated at frame "+frame+" on "+bone.getName());
                if (Math.abs(bone.getPosX())>2 || Math.abs(bone.getPosY())>2 || Math.abs(bone.getPosZ())>2
                        || bone.getScaleX()!=1 || bone.getScaleY()!=1 || bone.getScaleZ()!=1)
                    throw new AssertionError("Procedural translation/scale drift on "+bone.getName());
            }
        }
        var grappler = new MinotaurEntity(Asterion.MINOTAUR,level);
        PolishSmoke.data(grappler,"DATA_PHASE",PolishSmoke.ordinal("BehaviorPhase","BOSS"));
        PolishSmoke.data(grappler,"DATA_BOSS_ATTACK",PolishSmoke.ordinal("BossAttack","CHAIN_GRAPPLE"));
        PolishSmoke.data(grappler,"DATA_BOSS_ATTACK_TICKS",24);
        grappler.yHeadRot=grappler.yHeadRotO=65;grappler.setXRot(30);grappler.xRotO=30;
        if(!grappler.isChainGrappleActive())throw new AssertionError("Invalid grapple fixture");
        for(int frame=0;frame<120;frame++) {
            grappler.tickCount=frame;
            for(var bone:processor.getRegisteredBones()) { bone.setRotX(.25F);bone.setRotY(-.4F);bone.setRotZ(.1F); }
            apply.invoke(null,grappler,processor.getRegisteredBones(),.5F);
            for(var bone:processor.getRegisteredBones())
                if(bone.getRotX()!=.25F || bone.getRotY()!=-.4F || bone.getRotZ()!=.1F)
                    throw new AssertionError("Procedural offsets changed the authored grapple pose");
        }
        var live = level.getEntitiesOfClass(MinotaurEntity.class, net.minecraft.client.Minecraft.getInstance().player.getBoundingBox().inflate(30)).get(0);
        var controller = live.getAnimatableInstanceCache().getManagerForId(live.getId()).getAnimationControllers().get("movement");
        if (controller.getCurrentAnimation() == null) throw new AssertionError("No live animation for clock regression");
        var clock = (net.krodark.asterion.entity.MinotaurAnimationController) (Object) controller;
        var adjust = clock.getClass().getDeclaredMethod("adjustTick", double.class); adjust.setAccessible(true);
        clock.samplePose(-1, 20, false);
        double a = (double)adjust.invoke(clock, 1000D), b = (double)adjust.invoke(clock, 1000.25D);
        if (Math.abs((b-a)-.25) > .0001) throw new AssertionError("Natural animation clock froze instead of interpolating: " + a + ", " + b);
        Asterion.LOGGER.info("ASTERION_ANIMATION_CLOCK PASSED: natural clip advances at fractional render times");
        Asterion.LOGGER.info("ASTERION_GRAPPLE PASSED: authored grapple bone rotations preserved for 120 frames");
        Asterion.LOGGER.info("ASTERION_PROCEDURAL PASSED: 6000 GeckoLib frames, alternating Minotaurs, no rotation/translation/scale drift");
    }
}
