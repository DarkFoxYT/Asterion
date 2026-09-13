package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.CentipedeChain;
import net.krodark.asterion.entity.CentipedeFrame;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

/** Applies the server-authored articulated chain to the 1.21.1 GeckoLib bones. */
public final class PortScarletCentipedeRenderer extends SimpleGeoEntityRenderer<ScarletCentipedeEntity> {
    private ScarletCentipedeEntity current;
    private float partial;
    private Vec3 origin = Vec3.ZERO;

    public PortScarletCentipedeRenderer(EntityRendererProvider.Context context) {
        super(context, Asterion.id("entity/centipede"), Asterion.id("textures/entity/centipede.png"),
                Asterion.id("entity/centipede"), 1.44F, CentipedeFrame.MODEL_SCALE);
    }

    @Override
    public void render(ScarletCentipedeEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        current = entity;
        partial = partialTick;
        origin = entity.getPosition(partialTick);
        super.render(entity, yaw, partialTick, poses, buffers, light);
        current = null;
    }

    @Override
    protected void applyRotations(ScarletCentipedeEntity entity, PoseStack poses, float age,
                                  float yaw, float partialTick, float nativeScale) {
        // Each anchor already contains its full world-space surface orientation.
    }

    @Override
    public void renderRecursively(PoseStack poses, ScarletCentipedeEntity entity, GeoBone bone,
                                  RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                  boolean rerender, float partialTick, int light, int overlay, int colour) {
        String name = bone.getName();
        int anchor = name.equals("head_anchor") ? 0 : indexed(name, "segment_anchor_");
        if (anchor >= 0) applyAnchor(bone, anchor);
        else {
            int leg = trailingIndex(name);
            if (leg >= 0 && leg < entity.chainSegmentCount() && name.contains("leg_")) {
                float wave = Mth.sin(entity.segmentGait(leg, partial) * 1.55F - leg * 0.78F)
                        * 0.42F * entity.segmentSpeed(leg, partial);
                boolean positive = name.startsWith("leftfront") || name.startsWith("leftback")
                        || name.startsWith("rightmid");
                bone.setRotY(bone.getInitialSnapshot().getRotY() + (positive ? wave : -wave));
            }
        }
        super.renderRecursively(poses, entity, bone, type, buffers, buffer, rerender,
                partialTick, light, overlay, colour);
    }

    private void applyAnchor(GeoBone bone, int index) {
        boolean visible = current != null && index < current.chainSegmentCount();
        bone.setHidden(!visible);
        if (!visible) return;
        CentipedeChain.Pose pose = current.chainPose(index, partial);
        Vector3f position = CentipedeFrame.boneTranslation(pose.position().subtract(origin));
        Vector3f rotation = CentipedeFrame.boneAngles(CentipedeFrame.rotation(pose.normal(), pose.forward()));
        bone.updatePosition(position.x, position.y, position.z);
        bone.updateRotation(rotation.x, rotation.y, rotation.z);
    }

    private static int indexed(String name, String prefix) {
        if (!name.startsWith(prefix)) return -1;
        try { return Integer.parseInt(name.substring(prefix.length())); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static int trailingIndex(String name) {
        int underscore = name.lastIndexOf('_');
        if (underscore < 0) return -1;
        try { return Integer.parseInt(name.substring(underscore + 1)); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
