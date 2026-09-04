package net.krodark.asterion.client.light;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.GreekBrazierBlock;
import net.krodark.asterion.block.LamenterBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Low-frequency nearby scan for luminous fixtures that do not render their own point light. */
public final class BrazierAmneticLights {
    private static int cooldown;
    private BrazierAmneticLights() { }

    public static void tick(Minecraft client) {
        if(client.level==null||client.player==null||--cooldown>0)return;
        cooldown=40;
        BlockPos center=client.player.blockPosition();
        BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
        for(int y=-8;y<=8;y++) for(int x=-16;x<=16;x++) for(int z=-16;z<=16;z++) {
            if(x*x+z*z>256)continue;
            cursor.set(center.getX()+x,center.getY()+y,center.getZ()+z);
            var state=client.level.getBlockState(cursor);
            if(state.is(Asterion.LAMENTER)&&state.getValue(LamenterBlock.CRYING)) {
                BlockPos key=cursor.immutable();
                LedAmneticLight.updateItemGlowLight(key,new Vec3(key.getX()+.5,key.getY()+.72,key.getZ()+.5),
                        .18F,.72F,1F,.72F,4.25F,false);
                continue;
            }
            if(!state.is(Asterion.GREEK_BRAZIER)||!GreekBrazierBlock.isRoot(state)
                    ||!state.getValue(BlockStateProperties.LIT))continue;
            BlockPos key=cursor.immutable();
            LedAmneticLight.updateItemGlowLight(key,new Vec3(key.getX()+.5,key.getY()+1.35,key.getZ()+.5),
                    .18F,1F,.30F,2.15F,9F,false);
        }
    }
}
