package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.RuneBlockEntity;
import net.minecraft.resources.Identifier;

public final class RuneGeoModel extends GeoModel<RuneBlockEntity> {
    public static final DataTicket<Integer> RUNE_INDEX = DataTickets.create("asterion_rune_index", Integer.class);
    private static final Identifier MODEL = Asterion.id("block/rune");
    private static final Identifier ANIMATION = Asterion.id("block/rune");

    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) {
        int index = state.getOrDefaultGeckolibData(RUNE_INDEX, 0);
        return Asterion.id("textures/block/runes/" + (index + 1) + ".png");
    }
    @Override public Identifier getAnimationResource(RuneBlockEntity animatable) { return ANIMATION; }
}
