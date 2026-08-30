package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LabyrinthVineBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;

public final class LabyrinthVineGeoModel extends GeoModel<LabyrinthVineBlockEntity> {
    public static final DataTicket<Boolean> END = DataTickets.create("asterion_labyrinth_vine_end", Boolean.class);
    public static final DataTicket<Direction> FACING = DataTickets.create(
            "asterion_labyrinth_vine_facing", Direction.class);
    private static final Identifier MODEL = Asterion.id("block/labyrinth_vine");
    private static final Identifier TEXTURE = Asterion.id("textures/block/labyrinth_vine.png");
    private static final Identifier ANIMATION = Asterion.id("block/labyrinth_vine");

    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(LabyrinthVineBlockEntity animatable) { return ANIMATION; }
}
