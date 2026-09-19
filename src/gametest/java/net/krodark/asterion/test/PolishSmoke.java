package net.krodark.asterion.test;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.phys.Vec3;
final class PolishSmoke {
    private static float diamond;
    private static Vec3 hand;
    private static net.minecraft.client.gui.components.LerpingBossEvent bar;
    static void register(java.util.function.IntSupplier ticks) {
        if (!Boolean.getBoolean("asterion.polishSmoke")) return;
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((g,t) -> {
            if (ticks.getAsInt()<180) return;
            if (bar==null) bar=new net.minecraft.client.gui.components.LerpingBossEvent(java.util.UUID.randomUUID(),
                    net.minecraft.network.chat.Component.literal("THE MINOTAUR"),.65F,
                    net.minecraft.world.BossEvent.BossBarColor.RED,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS,false,false,false);
            net.krodark.asterion.port.client.PortMinotaurBossBar.render(g,g.guiWidth()/2-91,16,bar);
        });
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void data(MinotaurEntity boss,String field,Object value) {
        try {var f=MinotaurEntity.class.getDeclaredField(field);f.setAccessible(true);boss.getEntityData().set((EntityDataAccessor)f.get(null),value);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static int ordinal(String type,String name) {
        for(var c:MinotaurEntity.class.getDeclaredClasses()) if(c.getSimpleName().equals(type))
            for(var v:c.getEnumConstants())if(((Enum<?>)v).name().equals(name))return ((Enum<?>)v).ordinal();
        throw new AssertionError(name);
    }
    static void tick(Minecraft client,int tick) {
        var boss=client.level.getEntitiesOfClass(MinotaurEntity.class,client.player.getBoundingBox().inflate(30)).stream().findFirst().orElse(null);
        if(boss==null)throw new AssertionError("Missing Minotaur fixture");
        data(boss,"DATA_PHASE",ordinal("BehaviorPhase","BOSS"));
        client.player.setXRot(-20);client.player.xRotO=-20;
        if(tick>=185 && tick<250) {
            data(boss,"DATA_BOSS_ATTACK",ordinal("BossAttack","GRAB"));
            data(boss,"DATA_BOSS_ATTACK_TICKS",Math.min(46,tick-180));
            data(boss,"DATA_GRAB_TARGET_ID",client.player.getId());data(boss,"DATA_REACH_ARM",1);
            data(boss,"DATA_HELD_PLAYER",client.player.getId());
            boss.yHeadRot=boss.yBodyRot+35;boss.yHeadRotO=boss.yHeadRot;
        }
        if(tick==160) GameplaySmoke.server(client,p->{
            for(var slot:java.util.List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))p.setItemSlot(slot,ItemStack.EMPTY);
        });
        if(tick==175) GameplaySmoke.server(client,p->{
            if(MinotaurEntity.gearPressure(p)>.01F)throw new AssertionError("Unarmored pressure");
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
            p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
        });
        if(tick==200) {
            GameplaySmoke.server(client,p->{
                diamond=MinotaurEntity.gearPressure(p);if(diamond<.8F)throw new AssertionError("Diamond pressure missing");
                p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.NETHERITE_HELMET));p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.NETHERITE_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.NETHERITE_LEGGINGS));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.NETHERITE_BOOTS));
            });
            checkMask(client);
            ProceduralStabilitySmoke.run(client.level);
        }
        if(tick==220) {
            hand=net.krodark.asterion.port.client.ragdoll.MinotaurHandAttachment.feet(client.player);
            if(hand==null)throw new AssertionError("Held player lacks an animated hand anchor");
            if(net.krodark.asterion.port.client.PortMinotaurBodyPicking.body(boss)==null)throw new AssertionError("No animated body hitboxes");
            snapshot(client,"grab-green-blood");
        }
        if(tick==350) {
            GameplaySmoke.server(client,p->{
                if(MinotaurEntity.gearPressure(p)<=diamond)throw new AssertionError("Netherite must increase pressure");
                net.krodark.asterion.network.ragdoll.RagdollServerNetworking.markRagdolled(p,30);
                var result=p.gameMode.useItem(p,p.serverLevel(),new ItemStack(Asterion.AFTERBLOW),net.minecraft.world.InteractionHand.MAIN_HAND);
                if(result!=net.minecraft.world.InteractionResult.FAIL)throw new AssertionError("Server allowed item use while ragdolled");
                net.krodark.asterion.network.ragdoll.RagdollServerNetworking.finishRagdoll(p);
            });
        }
        if(tick==250) {data(boss,"DATA_HELD_PLAYER",-1);data(boss,"DATA_BOSS_ATTACK",0);data(boss,"DATA_BOSS_ATTACK_TICKS",0);}
        if(tick==255 && net.krodark.asterion.port.client.ragdoll.MinotaurHandAttachment.feet(client.player)!=null)throw new AssertionError("Stale hand after release");
        if(tick>=265) {data(boss,"DATA_BOSS_STAGE",ordinal("BossStage","DEFEATED"));data(boss,"DATA_BOSS_ATTACK_TICKS",200);}
        if(tick==310)snapshot(client,"corpse");
        if(tick==325)data(boss,"DATA_HARVESTED",true);
        if(tick==345)snapshot(client,"corpse-skeleton");
        if(tick==365) {
            ClientSmokeTest.verifyGraphicsAndTaa();
            Asterion.LOGGER.info("ASTERION_POLISH PASSED: GUI alpha masks, worn gear scaling, held/released player anchors, animated body picking, ragdoll item-use rejection, corpse rendering");
            client.stop();
        }
    }
    private static void snapshot(Minecraft c,String name){net.minecraft.client.Screenshot.grab(c.gameDirectory,"asterion-polish-"+name+".png",c.getMainRenderTarget(),m->{});}
    private static void checkMask(Minecraft client) {
        try {
            var cls=Class.forName("net.krodark.asterion.port.client.PortGuiMask");var get=cls.getDeclaredMethod("get",net.minecraft.resources.ResourceLocation.class);get.setAccessible(true);
            var id=(net.minecraft.resources.ResourceLocation)get.invoke(null,Asterion.id("textures/gui/bossbar/minotaur_filling.png"));
            var image=((net.minecraft.client.renderer.texture.DynamicTexture)client.getTextureManager().getTexture(id)).getPixels();
            boolean visible=false;
            for(int y=53;y<59;y++)for(int x=46;x<210;x++){int pixel=image.getPixelRGBA(x,y);if((pixel>>>24)>0){visible=true;if((pixel&0xFFFFFF)!=0xFFFFFF)throw new AssertionError("Blood mask remains dark");}}
            if(!visible)throw new AssertionError("Empty blood mask");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
}
