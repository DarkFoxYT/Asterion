package net.krodark.labyrinth.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.labyrinth.entity.MinotaurEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

public final class MinotaurPlaceholderRenderer extends
        MobRenderer<MinotaurEntity, ZombieRenderState, ZombieModel<ZombieRenderState>> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");
    private static final Identifier EYES = Identifier.fromNamespaceAndPath("labyrinth", "textures/entity/minotaur_eyes.png");

    public MinotaurPlaceholderRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.9F);
        addLayer(new MinotaurEyesLayer(this));
    }

    @Override
    public ZombieRenderState createRenderState() {
        return new ZombieRenderState();
    }

    @Override
    public void extractRenderState(MinotaurEntity entity, ZombieRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.isAggressive = entity.isAggressive();
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void scale(ZombieRenderState state, PoseStack poseStack) {
        poseStack.scale(1.42F, 1.38F, 1.42F);
    }

    /** Full-bright eyes remain barely readable through the Eclipse darkness without outlining the body. */
    private static final class MinotaurEyesLayer extends EyesLayer<ZombieRenderState, ZombieModel<ZombieRenderState>> {
        private MinotaurEyesLayer(RenderLayerParent<ZombieRenderState, ZombieModel<ZombieRenderState>> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return RenderTypes.eyes(EYES);
        }
    }
}
