package net.krodark.asterion.update.underworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.krodark.asterion.update.underworld.world.FerryWakeField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class FerryWakeTexture {
    public static final Identifier ID=Asterion.id("dynamic/ferry_wake");
    private static final FerryWakeField FIELD=new FerryWakeField();
    private static DynamicTexture texture;
    private static NativeImage pixels;
    private static ClientLevel world;
    private static long uploaded=Long.MIN_VALUE;
    private static double centerX,centerZ;
    private FerryWakeTexture() { }
    private static void ensureTexture() {
        if(texture!=null)return;
        // Client entrypoints run before the graphics device exists. Allocate only
        // from level rendering, and reuse the texture across frames and worlds.
        pixels=new NativeImage(128,129,true);
        texture=new DynamicTexture(() -> "Persistent ferry water displacement",pixels);
        Minecraft.getInstance().getTextureManager().register(ID,texture);
        clear();
    }
    public static void clear() { FIELD.clear(); world=null; uploaded=Long.MIN_VALUE; }
    public static void prepare(ClientLevel level, CharonsFerryEntity boat) {
        ensureTexture();
        if(world!=level) { clear(); world=level; }
        long time=level.getGameTime();
        if(boat!=null) {
            centerX=boat.getX();centerZ=boat.getZ();
            double yaw=Math.toRadians(boat.getYRot());
            // Stern positions are deposited in the world, not attached to the moving entity.
            FIELD.record(centerX+Math.sin(yaw)*3.2,centerZ-Math.cos(yaw)*3.2,time,1);
        }
        if(uploaded==time || time%2!=0 && uploaded!=Long.MIN_VALUE)return;
        uploaded=time;
        FIELD.rasterize(centerX,centerZ,time);
        for(int z=0;z<128;z++)for(int x=0;x<128;x++)pixels.setPixel(x,z,FIELD.pixel(x,z));
        int x=(FIELD.originX+2048)*16,z=(FIELD.originZ+2048)*16;
        pixels.setPixel(0,128,((z&255)<<24)|((x>>8&255)<<16)|((x&255)<<8)|(z>>8&255));
        texture.upload();
    }
}
