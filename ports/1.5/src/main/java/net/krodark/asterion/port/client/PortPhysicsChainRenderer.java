package net.krodark.asterion.port.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.PhysicsChainEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
public final class PortPhysicsChainRenderer extends EntityRenderer<PhysicsChainEntity>{
 public PortPhysicsChainRenderer(EntityRendererProvider.Context context){super(context);}
 @Override public void render(PhysicsChainEntity chain,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
  PortChainGeometry.drawHanging(poses.last(),buffers.getBuffer(RenderType.entityCutout(getTextureLocation(chain))),chain.points(partial),chain.getPosition(partial).scale(-1),Vec3.ZERO,light);
 }
 @Override public ResourceLocation getTextureLocation(PhysicsChainEntity chain){return Asterion.id("textures/block/mazesteel_chain.png");}
}
