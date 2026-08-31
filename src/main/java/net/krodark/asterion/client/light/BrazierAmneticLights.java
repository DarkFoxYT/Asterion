package net.krodark.asterion.client.light;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.GreekBrazierBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Low-frequency nearby scan for static braziers, which intentionally have no block entity. */
public final class BrazierAmneticLights {
    private static int cooldown;
    private BrazierAmneticLights() { }

    public static void tick(Minecraft client) {
        if(client.level==null||client.player==null||--cooldown>0)return;
        cooldown=10;
        BlockPos center=client.player.blockPosition();
        BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
        for(int y=-12;y<=12;y++) for(int x=-28;x<=28;x++) for(int z=-28;z<=28;z++) {
            if(x*x+z*z>784)continue;
            cursor.set(center.getX()+x,center.getY()+y,center.getZ()+z);
            var state=client.level.getBlockState(cursor);
            if(!state.is(Asterion.GREEK_BRAZIER)||!GreekBrazierBlock.isRoot(state)
                    ||!state.getValue(BlockStateProperties.LIT))continue;
            BlockPos key=cursor.immutable();
            LedAmneticLight.updateItemGlowLight(key,new Vec3(key.getX()+.5,key.getY()+1.35,key.getZ()+.5),
                    .18F,1F,.30F,2.15F,9F,false);
        }
    }
}
