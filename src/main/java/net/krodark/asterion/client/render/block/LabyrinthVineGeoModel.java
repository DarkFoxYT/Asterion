package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.minecraft.resources.Identifier;

public final class LabyrinthVineGeoModel extends GeoModel<LabyrinthVineBlockEntity> {
    public static final DataTicket<Boolean> END = DataTickets.create("asterion_labyrinth_vine_end", Boolean.class);
    private static final Identifier MIDDLE_MODEL = Asterion.id("block/labyrinth_vine_middle");
    private static final Identifier END_MODEL = Asterion.id("block/labyrinth_vine_end");
    private static final Identifier TEXTURE = Asterion.id("textures/block/labyrinth_vine.png");
    private static final Identifier ANIMATION = Asterion.id("block/labyrinth_vine");

    @Override public Identifier getModelResource(GeoRenderState state) {
        return state.getOrDefaultGeckolibData(END, true) ? END_MODEL : MIDDLE_MODEL;
    }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(LabyrinthVineBlockEntity animatable) { return ANIMATION; }
}
