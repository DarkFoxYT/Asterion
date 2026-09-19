package net.krodark.asterion.port.legacy.gecko;
import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
public class GeoBlockRenderer<T extends BlockEntity & GeoAnimatable> extends software.bernie.geckolib.renderer.GeoBlockRenderer<T> {
 public GeoBlockRenderer(GeoModel<T> model) { super(model); }
 @Override public void defaultRender(PoseStack poses,T entity,MultiBufferSource buffers,RenderType type,VertexConsumer buffer,float yaw,float partialTick,int light) {
  renderTyped(entity,partialTick,poses,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
 }
 public void renderTyped(T entity,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
  super.defaultRender(poses,entity,buffers,null,null,0,partialTick,light);
 }
}
