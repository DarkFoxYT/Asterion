package net.krodark.asterion.port.compat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
public final class GeometryCompat {
 public static AABB fullBlocks(BlockPos a,BlockPos b){return new AABB(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,Math.max(a.getY(),b.getY())+1,Math.max(a.getZ(),b.getZ())+1);}
 public static net.minecraft.world.level.levelgen.structure.BoundingBox inflate(net.minecraft.world.level.levelgen.structure.BoundingBox b,int x,int y,int z) {
 return new net.minecraft.world.level.levelgen.structure.BoundingBox(b.minX()-x,b.minY()-y,b.minZ()-z,b.maxX()+x,b.maxY()+y,b.maxZ()+z);
 }
 public static java.util.stream.Stream<net.minecraft.world.level.ChunkPos> chunks(net.minecraft.world.level.levelgen.structure.BoundingBox b) {
 return java.util.stream.IntStream.rangeClosed(b.minX()>>4,b.maxX()>>4).boxed().flatMap(x->java.util.stream.IntStream.rangeClosed(b.minZ()>>4,b.maxZ()>>4).mapToObj(z->new net.minecraft.world.level.ChunkPos(x,z)));
 }
}
