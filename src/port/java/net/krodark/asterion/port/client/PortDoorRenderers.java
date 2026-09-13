package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.BarrelDoorBlock;
import net.krodark.asterion.block.BarrelDoorBlockEntity;
import net.krodark.asterion.block.CursedBrazierDoorBlock;
import net.krodark.asterion.block.CursedBrazierDoorBlockEntity;
import net.krodark.asterion.block.MinotaurDoorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/** 1.21.1 bone-driven renderers for the three animated multi-block doors. */
public final class PortDoorRenderers {
    private PortDoorRenderers() {}

    public static final class Barrel extends SimpleGeoBlockRenderer<BarrelDoorBlockEntity> {
        private float angle;

        public Barrel() {
            super(Asterion.id("block/barrel_door"), Asterion.id("textures/block/barrel_door.png"),
                    Asterion.id("block/barrel_door"));
        }

        @Override
        public void render(BarrelDoorBlockEntity door, float partialTick, PoseStack poses,
                           MultiBufferSource buffers, int light, int overlay) {
            angle = door.angle(partialTick);
            super.render(door, partialTick, poses, buffers, light, overlay);
        }

        @Override
        public void renderRecursively(PoseStack poses, BarrelDoorBlockEntity door, GeoBone bone,
                                      RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                      boolean rerender, float partialTick, int light, int overlay, int colour) {
            if (bone.getName().equals("door")) bone.setRotY(angle);
            super.renderRecursively(poses, door, bone, type, buffers, buffer, rerender,
                    partialTick, light, overlay, colour);
        }

        @Override public boolean shouldRenderOffScreen(BarrelDoorBlockEntity door) { return true; }
        @Override public int getViewDistance() { return 128; }
        @Override public boolean shouldRender(BarrelDoorBlockEntity door, Vec3 camera) {
            return BarrelDoorBlock.isRoot(door.getBlockState()) && super.shouldRender(door, camera);
        }
    }

    public static final class Minotaur extends SimpleGeoBlockRenderer<MinotaurDoorBlockEntity> {
        private float angle;

        public Minotaur() {
            super(Asterion.id("block/minotaur_door"), Asterion.id("textures/block/minotaur_door.png"),
                    Asterion.id("block/minotaur_door"));
        }

        @Override
        public void render(MinotaurDoorBlockEntity door, float partialTick, PoseStack poses,
                           MultiBufferSource buffers, int light, int overlay) {
            angle = door.angle(partialTick);
            super.render(door, partialTick, poses, buffers, light, overlay);
        }

        @Override
        public void renderRecursively(PoseStack poses, MinotaurDoorBlockEntity door, GeoBone bone,
                                      RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                      boolean rerender, float partialTick, int light, int overlay, int colour) {
            if (bone.getName().equals("rightdoor")) bone.setRotY(angle);
            else if (bone.getName().equals("leftdoor")) bone.setRotY(-angle);
            super.renderRecursively(poses, door, bone, type, buffers, buffer, rerender,
                    partialTick, light, overlay, colour);
        }

        @Override public boolean shouldRenderOffScreen(MinotaurDoorBlockEntity door) { return true; }
        @Override public int getViewDistance() { return 128; }
    }

    public static final class Cursed extends SimpleGeoBlockRenderer<CursedBrazierDoorBlockEntity> {
        private float lift;

        public Cursed() {
            super(Asterion.id("block/cursed_brazier_door"), Asterion.id("textures/block/cursed_brazier_door.png"),
                    Asterion.id("block/cursed_brazier_door"));
        }

        @Override
        public void render(CursedBrazierDoorBlockEntity door, float partialTick, PoseStack poses,
                           MultiBufferSource buffers, int light, int overlay) {
            lift = door.progress(partialTick);
            super.render(door, partialTick, poses, buffers, light, overlay);
        }

        @Override
        public void renderRecursively(PoseStack poses, CursedBrazierDoorBlockEntity door, GeoBone bone,
                                      RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                      boolean rerender, float partialTick, int light, int overlay, int colour) {
            if (bone.getName().equals("full")) bone.setPosY(lift * 72.0F);
            super.renderRecursively(poses, door, bone, type, buffers, buffer, rerender,
                    partialTick, light, overlay, colour);
        }

        @Override public boolean shouldRenderOffScreen(CursedBrazierDoorBlockEntity door) { return true; }
        @Override public int getViewDistance() { return 128; }
        @Override public boolean shouldRender(CursedBrazierDoorBlockEntity door, Vec3 camera) {
            return CursedBrazierDoorBlock.isRoot(door.getBlockState()) && super.shouldRender(door, camera);
        }
    }
}
