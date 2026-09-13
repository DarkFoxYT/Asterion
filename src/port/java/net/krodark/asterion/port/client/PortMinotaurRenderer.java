package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Restores the Minotaur's authored axe and paired swords on the 1.21.1 model. */
@SuppressWarnings("deprecation")
public final class PortMinotaurRenderer extends SimpleGeoEntityRenderer<MinotaurEntity> {
    public PortMinotaurRenderer(EntityRendererProvider.Context context) {
        super(context, Asterion.id("entity/minotaur"), Asterion.id("textures/entity/minotaur.png"),
                Asterion.id("entity/minotaur"), 1.1F, 1.0F);
        addRenderLayer(new Weapons(this));
    }

    private static final class Weapons extends GeoRenderLayer<MinotaurEntity> {
        private static final WeaponObject AXE = new WeaponObject();
        private static final WeaponObject SWORD = new WeaponObject();
        private static final GeoObjectRenderer<WeaponObject> AXE_RENDERER = new GeoObjectRenderer<>(new WeaponModel(false));
        private static final GeoObjectRenderer<WeaponObject> SWORD_RENDERER = new GeoObjectRenderer<>(new WeaponModel(true));

        private Weapons(PortMinotaurRenderer renderer) { super(renderer); }

        @Override
        public void renderForBone(PoseStack poses, MinotaurEntity boss, GeoBone bone, RenderType renderType,
                                  MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                                  int packedLight, int packedOverlay) {
            if (boss.isDefeatedBoss()) return;
            String name = bone.getName();
            int mode = boss.renderedWeaponMode();
            if (mode == 2 && (name.equals("hand_itemR") || name.equals("hand_itemL"))) {
                poses.pushPose();
                poses.translate(0, 6.0D / 16.0D, 0);
                SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight, partialTick);
                poses.popPose();
            }
            if (mode != 2 && name.equals("lowerbody")) {
                for (int sign : new int[]{-1, 1}) {
                    poses.pushPose();
                    poses.translate(sign * 17.0D / 16.0D, 17.0D / 16.0D, 3.0D / 16.0D);
                    poses.mulPose(Axis.XP.rotationDegrees(168));
                    poses.mulPose(Axis.ZP.rotationDegrees(-sign * 6));
                    SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight, partialTick);
                    poses.popPose();
                }
            }
            if (mode == 1 && !boss.axeInWorld() && name.equals("hand_itemR")) {
                poses.pushPose();
                poses.translate(0, -MinotaurAxeEntity.GRIP_Y, 0);
                AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight, partialTick);
                poses.popPose();
            } else if (mode != 1 && !boss.axeInWorld() && name.equals("lowerbody")) {
                poses.pushPose();
                poses.translate(0, .82D, 1.42D);
                poses.mulPose(Axis.ZP.rotationDegrees(45));
                poses.mulPose(Axis.YP.rotationDegrees(90));
                poses.translate(0, -(129 - 6 * Math.sqrt(2)) / 32.0D, 0);
                AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight, partialTick);
                poses.popPose();
            }
        }
    }

    static final class WeaponObject implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object related) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? 0 : level.getGameTime();
        }
    }

    static final class WeaponModel extends GeoModel<WeaponObject> {
        private final boolean sword;
        WeaponModel(boolean sword) { this.sword = sword; }
        @Override public ResourceLocation getModelResource(WeaponObject object) {
            return Asterion.id("geo/physics/" + (sword ? "sword" : "axe") + ".geo.json");
        }
        @Override public ResourceLocation getTextureResource(WeaponObject object) {
            return Asterion.id("textures/physics/" + (sword ? "sword" : "axe") + ".png");
        }
        @Override public ResourceLocation getAnimationResource(WeaponObject object) { return null; }
    }
}
