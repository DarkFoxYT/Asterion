package net.krodark.asterion.port.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
final class PortWeaponTrails {
 private PortWeaponTrails(){}
 private static final java.util.Map<Long,HeldTrail> HELD = new java.util.HashMap<>();
 private static net.minecraft.client.multiplayer.ClientLevel world;
 private static final class HeldTrail {
  final java.util.ArrayDeque<Vec3[]> samples = new java.util.ArrayDeque<>();
  double stamp = Double.NEGATIVE_INFINITY;
 }
 static void held(PoseStack poses,MultiBufferSource buffers,net.krodark.asterion.entity.MinotaurEntity boss,int side,float partial){
  var client=net.minecraft.client.Minecraft.getInstance();
  if(world!=client.level){HELD.clear();world=client.level;}
  long key=((long)boss.getId()<<1)^(side>0?1L:0L);
  float strength=boss.swordTrailStrength(partial);
  if(strength<=.005F){HELD.remove(key);return;}
  if(HELD.size()>=128&&!HELD.containsKey(key))HELD.clear();
  HeldTrail trail=HELD.computeIfAbsent(key,k->new HeldTrail());
  double stamp=boss.tickCount+partial;
  if(stamp<trail.stamp)trail.samples.clear();
  Vec3 camera=client.gameRenderer.getMainCamera().getPosition();
  var matrix=poses.last().pose();
  var root=matrix.transformPosition(new org.joml.Vector3f(0,19F/16F,0));
  var tip=matrix.transformPosition(new org.joml.Vector3f(0,84F/16F,0));
  Vec3[] sample={new Vec3(root.x,root.y,root.z).add(camera),new Vec3(tip.x,tip.y,tip.z).add(camera)};
  if(stamp>trail.stamp&&(trail.samples.isEmpty()||trail.samples.getLast()[1].distanceToSqr(sample[1])>.0004)){
   trail.samples.addLast(sample);trail.stamp=stamp;
   while(trail.samples.size()>7)trail.samples.removeFirst();
  }
  draw(new PoseStack(),buffers,trail.samples.toArray(new Vec3[0][]),camera,true,strength);
 }

 static void draw(PoseStack poses,MultiBufferSource buffers,Vec3[][] samples,Vec3 origin,boolean sword){
  draw(poses,buffers,samples,origin,sword,1F);
 }
 private static void draw(PoseStack poses,MultiBufferSource buffers,Vec3[][] samples,Vec3 origin,boolean sword,float strength){
  if(samples==null || samples.length<2)return;
  var out=buffers.getBuffer(RenderType.lightning());var pose=poses.last();
  for(int i=1;i<samples.length;i++){
   int alpha=(int)(135*strength*Math.pow(i/(double)(samples.length-1),2));
   for(int index:new int[]{0,1,2,3,3,2,1,0}){
    Vec3 point=switch(index){case 0->samples[i-1][0];case 1->samples[i-1][1];case 2->samples[i][1];default->samples[i][0];};
    Vec3 v=point.subtract(origin);int a=index==0||index==3?alpha/3:alpha;
    //? if >=1.20.5 {
    out.addVertex(pose,(float)v.x,(float)v.y,(float)v.z).setColor(255,sword?205:160,sword?135:70,a);
    //?} else {
    /*out.vertex(pose.pose(),(float)v.x,(float)v.y,(float)v.z).color(255,sword?205:160,sword?135:70,a).endVertex();*/
    //?}
   }
  }
 }
}
