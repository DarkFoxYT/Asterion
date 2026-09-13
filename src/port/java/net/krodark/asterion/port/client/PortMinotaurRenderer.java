package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.entity.MinotaurRemains;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
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
                Asterion.id("entity/minotaur"), 1.9F, 1.0F);
        withEmissiveBones(ignored -> 0xFFFFFFFF, boss -> !boss.isHarvested(), "glow");
        addRenderLayer(new Weapons(this));
    }

    @Override
    public void preRender(PoseStack poses, MinotaurEntity boss, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                          float partialTick, int light, int overlay, int colour) {
        float scale = .47F * AsterionConfig.INSTANCE.minotaurScale;
        double entryTime = PortCinematics.bossVisualTime(boss, partialTick);
        boss.setEntryVisualTime(entryTime);
        if (Double.isFinite(entryTime)) {
            net.minecraft.world.phys.Vec3 authored = net.krodark.asterion.entity.MinotaurEntranceMotion
                    .point(entryTime, boss.getBbWidth());
            double renderedX = Mth.lerp(partialTick, boss.xo, boss.getX());
            double renderedY = Mth.lerp(partialTick, boss.yo, boss.getY());
            double renderedZ = Mth.lerp(partialTick, boss.zo, boss.getZ());
            poses.translate(authored.x - renderedX, authored.y - renderedY, authored.z - renderedZ);
        }
        scaleWidth = scale;
        scaleHeight = scale;
        super.preRender(poses, boss, model, buffers, buffer, rerender,
                partialTick, light, overlay, colour);
    }

    @Override
    public boolean shouldRender(MinotaurEntity boss, Frustum frustum, double x, double y, double z) {
        if (boss.doorEntryTicks() > 0 && boss.doorEntryTicks() - 1
                < net.krodark.asterion.entity.MinotaurAnimationTiming.ENTRY_BREAK_TICK) return false;
        return true;
    }

    @Override
    public void renderRecursively(PoseStack poses, MinotaurEntity boss, GeoBone bone, RenderType type,
                                  MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                                  float partialTick, int light, int overlay, int colour) {
        String name = bone.getName();
        boolean skeleton = name.startsWith("skeleton") || name.startsWith("skeliton");
        boolean retained = name.contains("armor") || name.equals("lefthand") || name.equals("righthand")
                || name.equals("thing for skirt ig") || name.endsWith("_player_grip") || name.startsWith("hand_item");
        MinotaurRemains region = region(bone);
        boolean removed = region.removed(boss.removedParts());
        if (!name.equals("glow"))
            bone.setHidden(removed || (skeleton ? !boss.isHarvested() : boss.isHarvested() && !retained));
        super.renderRecursively(poses, boss, bone, type, buffers, buffer, rerender,
                partialTick, light, overlay, colour);
    }

    private static MinotaurRemains region(GeoBone bone) {
        for (GeoBone parent = bone; parent != null; parent = parent.getParent()) {
            MinotaurRemains region = MinotaurRemains.root(parent.getName());
            if (region != null) return region;
        }
        return MinotaurRemains.TORSO;
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
                SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight, partialTick);
                poses.popPose();
            }
            if (mode != 2 && name.equals("lowerbody")) {
                for (int sign : new int[]{-1, 1}) {
                    float age = boss.tickCount + partialTick;
                    float breathe = (float)Math.sin(age * .075F + (sign < 0 ? 0F : .65F));
                    float settle = (float)Math.sin(age * .16F + (sign < 0 ? 0F : Math.PI));
                    poses.pushPose();
                    poses.translate(sign * 17.0D / 16.0D, 17.0D / 16.0D + breathe * .025D,
                            3.0D / 16.0D + settle * .018D);
                    poses.mulPose(Axis.XP.rotationDegrees(168 + breathe * 1.25F));
                    poses.mulPose(Axis.ZP.rotationDegrees(-sign * (6 + settle * 1.1F)));
                    SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight, partialTick);
                    poses.popPose();
                }
            }
            if (mode == 1 && !boss.axeInWorld() && name.equals("hand_itemR")) {
                poses.pushPose();
                poses.translate(0, -MinotaurAxeEntity.GRIP_Y, 0);
                AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight, partialTick);
                poses.popPose();
            } else if (mode != 1 && !boss.axeInWorld() && name.equals("body")) {
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
