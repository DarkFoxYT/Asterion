package net.krodark.asterion.port.client;
import com.mojang.blaze3d.vertex.*;
import net.krodark.asterion.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import java.util.*;
final class PortMinotaurBodyLayer extends GeoRenderLayer<MinotaurEntity> {
    private static final Map<GeoBone,List<AABB>> SHAPES = new WeakHashMap<>();
    private PortMinotaurBodyPicking.Body body;
    PortMinotaurBodyLayer(PortMinotaurRenderer renderer) { super(renderer); }
    @Override public void preRender(PoseStack p,MinotaurEntity boss,BakedGeoModel model,RenderType type,MultiBufferSource buffers,VertexConsumer out,float partial,int light,int overlay) {
        body = PortMinotaurBodyPicking.begin(boss);
    }
    @Override public void renderForBone(PoseStack p,MinotaurEntity boss,GeoBone bone,RenderType type,MultiBufferSource buffers,VertexConsumer out,float partial,int light,int overlay) {
        if (body == null || bone.isHidden() || bone.getCubes().isEmpty()) return;
        MinotaurRemains region = MinotaurRemains.TORSO;
        for (GeoBone parent = bone; parent != null; parent = parent.getParent()) {
            var found = MinotaurRemains.root(parent.getName());
            if (found != null) { region = found; break; }
        }
        body.add(new Matrix4f(p.last().pose()).invert(), Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(),
                SHAPES.computeIfAbsent(bone,PortMinotaurBodyLayer::shapes), region.ordinal());
    }
    @Override public void render(PoseStack p,MinotaurEntity boss,BakedGeoModel model,RenderType type,MultiBufferSource buffers,VertexConsumer out,float partial,int light,int overlay) {
        PortMinotaurBodyPicking.publish(body);
    }
    private static List<AABB> shapes(GeoBone bone) {
        List<AABB> shapes = new ArrayList<>(); PoseStack p = new PoseStack(); Vector3f point = new Vector3f();
        for (GeoCube cube : bone.getCubes()) {
            p.pushPose();
            //? if >=1.20.5 {
            software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(p,cube);
            software.bernie.geckolib.util.RenderUtil.rotateMatrixAroundCube(p,cube);
            software.bernie.geckolib.util.RenderUtil.translateAwayFromPivotPoint(p,cube);
            //?} else {
            /*software.bernie.geckolib.util.RenderUtils.translateToPivotPoint(p,cube);
            software.bernie.geckolib.util.RenderUtils.rotateMatrixAroundCube(p,cube);
            software.bernie.geckolib.util.RenderUtils.translateAwayFromPivotPoint(p,cube);
            *///?}
            double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
            for (GeoQuad quad : cube.quads()) if (quad != null) for (GeoVertex v : quad.vertices()) {
                p.last().pose().transformPosition(v.position(),point);
                minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);
                maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);
            }
            p.popPose();
            if(Double.isFinite(minX))shapes.add(new AABB(minX,minY,minZ,maxX,maxY,maxZ).inflate(.035));
        }
        return List.copyOf(shapes);
    }
}
