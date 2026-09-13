package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/** Renders the simulated weapon entity with its authored Gecko model and texture. */
@SuppressWarnings("deprecation")
public final class MinotaurAxePortRenderer extends EntityRenderer<MinotaurAxeEntity> {
    private static final PortMinotaurRenderer.WeaponObject AXE = new PortMinotaurRenderer.WeaponObject();
    private static final PortMinotaurRenderer.WeaponObject SWORD = new PortMinotaurRenderer.WeaponObject();
    private static final GeoObjectRenderer<PortMinotaurRenderer.WeaponObject> AXE_RENDERER =
            new GeoObjectRenderer<>(new PortMinotaurRenderer.WeaponModel(false));
    private static final GeoObjectRenderer<PortMinotaurRenderer.WeaponObject> SWORD_RENDERER =
            new GeoObjectRenderer<>(new PortMinotaurRenderer.WeaponModel(true));

    public MinotaurAxePortRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = .6F;
    }

    @Override
    public void render(MinotaurAxeEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.mulPose(entity.renderRotation(partialTick));
        poses.translate(0, -entity.modelCenterY(), 0);
        poses.scale(entity.modelScale(), entity.modelScale(), entity.modelScale());
        if (entity.isSword()) SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight, partialTick);
        else AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight, partialTick);
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override public ResourceLocation getTextureLocation(MinotaurAxeEntity entity) {
        return entity.isSword()
                ? net.krodark.asterion.Asterion.id("textures/physics/sword.png")
                : net.krodark.asterion.Asterion.id("textures/physics/axe.png");
    }
}
