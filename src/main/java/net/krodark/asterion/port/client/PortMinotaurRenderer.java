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
//? if >=1.20.5 {
import software.bernie.geckolib.util.Color;
//?} else {
/*import software.bernie.geckolib.core.object.Color;*/
//?}

/** Restores the Minotaur's authored axe and paired swords on the 1.21.1 model. */
@SuppressWarnings("deprecation")
public final class PortMinotaurRenderer extends SimpleGeoEntityRenderer<MinotaurEntity> {
    public PortMinotaurRenderer(EntityRendererProvider.Context context) {
        super(context, Asterion.id("entity/minotaur"), Asterion.id("textures/entity/minotaur.png"),
                Asterion.id("entity/minotaur"), 1.9F, 1.0F);
        withEmissiveBones(PortMinotaurPose::eyeTint, boss -> !boss.isHarvested(), "glow");
        addRenderLayer(new Weapons(this));
        addRenderLayer(new PortMinotaurBodyLayer(this));
        addRenderLayer(new PortMinotaurChainLayer(this));
    }

    @Override
    public Color getRenderColor(MinotaurEntity boss, float partial, int light) {
        var base = super.getRenderColor(boss, partial, light);
        return PortMinotaurPose.attackCue(boss) ? new Color((base.getAlpha() << 24) | 0x4DFF59) : base;
    }

    @Override

//? if >=1.20.5 {
public void preRender(PoseStack poses, MinotaurEntity boss, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                          float partialTick, int light, int overlay, int colour) {
//?} else {
/*public void preRender(PoseStack poses, MinotaurEntity boss, BakedGeoModel model,
                          MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                          float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
 int colour = ((int)(alpha*255)<<24)|((int)(red*255)<<16)|((int)(green*255)<<8)|(int)(blue*255);*/
//?}

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

//? if >=1.20.5 {
super.preRender(poses, boss, model, buffers, buffer, rerender,
                partialTick, light, overlay, colour);
//?} else {
/*super.preRender(poses, boss, model, buffers, buffer, rerender,
                partialTick, light, overlay, ((colour >> 16) & 255) / 255F, ((colour >> 8) & 255) / 255F, (colour & 255) / 255F, ((colour >>> 24) & 255) / 255F);*/
//?}

    }

    @Override
    public boolean shouldRender(MinotaurEntity boss, Frustum frustum, double x, double y, double z) {
        if (boss.doorEntryTicks() > 0 && boss.doorEntryTicks() - 1
                < net.krodark.asterion.entity.MinotaurAnimationTiming.ENTRY_BREAK_TICK) return false;
        return boss.shouldRender(x,y,z) && frustum.isVisible(boss.animatedBodyBounds().inflate(1));
    }

    @Override

//? if >=1.20.5 {
public void renderRecursively(PoseStack poses, MinotaurEntity boss, GeoBone bone, RenderType type,
                                  MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                                  float partialTick, int light, int overlay, int colour) {
//?} else {
/*public void renderRecursively(PoseStack poses, MinotaurEntity boss, GeoBone bone, RenderType type,
                                  MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                                  float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
 int colour = ((int)(alpha*255)<<24)|((int)(red*255)<<16)|((int)(green*255)<<8)|(int)(blue*255);*/
//?}

        String name = bone.getName();
        boolean skeleton = name.startsWith("skeleton") || name.startsWith("skeliton");
        boolean retained = name.contains("armor") || name.equals("lefthand") || name.equals("righthand")
                || name.equals("thing for skirt ig") || name.endsWith("_player_grip") || name.startsWith("hand_item");
        MinotaurRemains region = region(bone);
        boolean removed = region.removed(boss.removedParts());
        if (!name.equals("glow"))
            bone.setHidden(removed || (skeleton ? !boss.isHarvested() : boss.isHarvested() && !retained));

//? if >=1.20.5 {
super.renderRecursively(poses, boss, bone, type, buffers, buffer, rerender,
                partialTick, light, overlay, colour);
//?} else {
/*super.renderRecursively(poses, boss, bone, type, buffers, buffer, rerender,
                partialTick, light, overlay, ((colour >> 16) & 255) / 255F, ((colour >> 8) & 255) / 255F, (colour & 255) / 255F, ((colour >>> 24) & 255) / 255F);*/
//?}

    }

    private static MinotaurRemains region(GeoBone bone) {
        for (GeoBone parent = bone; parent != null; parent = parent.getParent()) {
            MinotaurRemains region = MinotaurRemains.root(parent.getName());
            if (region != null) return region;
        }
        return MinotaurRemains.TORSO;
    }

    private static final class Weapons extends GeoRenderLayer<MinotaurEntity> {
        private static final double BACK_MOUNT_CENTER_Y = (129 - 6 * Math.sqrt(2)) / 32.0D;
        private static final WeaponObject AXE = new WeaponObject();
        private static final WeaponObject SWORD = new WeaponObject();
        private static final GeoObjectRenderer<WeaponObject> AXE_RENDERER = new WeaponRenderer(false);
        private static final GeoObjectRenderer<WeaponObject> SWORD_RENDERER = new WeaponRenderer(true);

        private Weapons(PortMinotaurRenderer renderer) { super(renderer); }

        @Override
        public void renderForBone(PoseStack poses, MinotaurEntity boss, GeoBone bone, RenderType renderType,
                                  MultiBufferSource buffers, VertexConsumer buffer, float partialTick,
                                  int packedLight, int packedOverlay) {
            if (boss.isDefeatedBoss()) return;
            poses.pushPose();
            // GeckoLib 5 per-bone tasks end at the pivot; GeckoLib 4 layers end
            // after translating away from it. Restore main's attachment basis.
            //? if >=1.20.5 {
            software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(poses, bone);
            //?} else {
            /*software.bernie.geckolib.util.RenderUtils.translateToPivotPoint(poses, bone);
            *///?}
            try {
            String name = bone.getName();
            int mode = boss.renderedWeaponMode();
            if (mode == 2 && (name.equals("hand_itemR") || name.equals("hand_itemL"))) {
                poses.pushPose();
                poses.translate(0, 6.0 / 16.0, 0);

//? if >=1.20.5 {
SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight, partialTick);
//?} else {
/*SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight);*/
//?}

                poses.popPose();
            }
            if (mode != 2 && name.equals("lowerbody")) {
                float age = boss.tickCount + partialTick;
                for (int sign = -1; sign <= 1; sign += 2) {
                    float breathe = (float)Math.sin(age * .075F + (sign < 0 ? 0F : .65F));
                    float settle = (float)Math.sin(age * .16F + (sign < 0 ? 0F : Math.PI));
                    poses.pushPose();
                    poses.translate(sign * 17.0D / 16.0D, 17.0D / 16.0D + breathe * .025D,
                            3.0D / 16.0D + settle * .018D);
                    poses.mulPose(Axis.XP.rotationDegrees(168 + breathe * 1.25F));
                    poses.mulPose(Axis.ZP.rotationDegrees(-sign * (6 + settle * 1.1F)));
                    poses.translate(0, 6.0 / 16.0, 0);

//? if >=1.20.5 {
SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight, partialTick);
//?} else {
/*SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight);*/
//?}

                    poses.popPose();
                }
            }
            if (mode == 1 && !boss.axeInWorld() && name.equals("hand_itemR")) {
                poses.pushPose();
                // hand_itemR is an authored locator.  Its rotation already puts the
                // weapon axis through the palm; another correction here compounds
                // the arm animation and makes the axe point into the floor.
                poses.translate(0, -MinotaurAxeEntity.GRIP_Y, 0);

//? if >=1.20.5 {
AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight, partialTick);
//?} else {
/*AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight);*/
//?}

                poses.popPose();
            } else if (mode != 1 && !boss.axeInWorld() && name.equals("body")) {
                poses.pushPose();
                // This offset was authored in the torso basis.  Attaching it to
                // lowerbody makes the hip and torso rotations affect it twice.
                poses.translate(0, .82D, 1.42D);
                poses.mulPose(Axis.ZP.rotationDegrees(45));
                poses.mulPose(Axis.YP.rotationDegrees(90));
                poses.translate(0, -BACK_MOUNT_CENTER_Y, 0);

//? if >=1.20.5 {
AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight, partialTick);
//?} else {
/*AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight);*/
//?}

                poses.popPose();
            }
            } finally { poses.popPose(); }
        }
    }

    /** Main's DebrisGeoRenderer disables the default block-centering offset. */
    static final class WeaponRenderer extends GeoObjectRenderer<WeaponObject> {
        WeaponRenderer(boolean sword) { super(new WeaponModel(sword)); }
        @Override
        //? if >=1.20.5 {
        public void preRender(PoseStack poses, WeaponObject object, BakedGeoModel model,
                MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                float partial, int light, int overlay, int color) {
            super.preRender(poses, object, model, buffers, buffer, rerender, partial, light, overlay, color);
        //?} else {
        /*public void preRender(PoseStack poses, WeaponObject object, BakedGeoModel model,
                MultiBufferSource buffers, VertexConsumer buffer, boolean rerender,
                float partial, int light, int overlay, float red, float green, float blue, float alpha) {
            super.preRender(poses, object, model, buffers, buffer, rerender, partial, light, overlay, red, green, blue, alpha);
        *///?}
            poses.translate(-.5, -.51, -.5);
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
