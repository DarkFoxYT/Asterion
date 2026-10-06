import java.util.*;
import java.nio.file.*;
import net.krodark.asterion.update.underworld.world.*;
import net.krodark.asterion.update.underworld.client.LimboCascadeMesh;
import net.minecraft.world.phys.Vec3;

public final class LimboCascadeSmoke {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // This isolated sampler has no server datapack reload to bind fluid tags.
        var bindTags=net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindTags",java.util.Collection.class);
        bindTags.setAccessible(true);
        for(var fluid:new net.minecraft.world.level.material.Fluid[]{net.minecraft.world.level.material.Fluids.WATER,net.minecraft.world.level.material.Fluids.FLOWING_WATER})
            bindTags.invoke(fluid.builtInRegistryHolder(),java.util.List.of(net.minecraft.tags.FluidTags.WATER));
        int checks=0;
        for (int boundary=1;boundary<=4;boundary++) for (int angle=0;angle<12;angle++) {
            double theta=angle*Math.PI/6,radius=LimboSeaRegions.SHORE_RADIUS-boundary*3200;
            double x=radius*Math.sin(theta),z=LimboSeaRegions.CENTER_Z-radius*Math.cos(theta);
            Vec3 normal=LimboCascades.downstream(x,z);
            int upper=LimboCascades.waterY(x-normal.x*2,z-normal.z*2),lower=LimboCascades.waterY(x+normal.x*2,z+normal.z*2);
            check(upper-lower==LimboCascades.drop(boundary),"Waterfall drop must increase toward centre");
            check(upper==LimboCascades.waterYForTier(boundary-1) && lower==LimboCascades.waterYForTier(boundary),"Sea ordering changed");
            Vec3 flow=LimboCascades.current(x-normal.x*2,z-normal.z*2);
            check(flow.dot(LimboCascades.downstream(x,z))>0 && flow.length()<=.065001,"Current must go over the lip and remain bounded");
            for (int seed=0;seed<8;seed++) {
                int floor=UnderworldTerrain.seaFloor(seed,(int)Math.floor(x+normal.x*8),(int)Math.floor(z+normal.z*8));
                check(floor>=UnderworldTerrain.MIN_Y+2 && floor<lower-10,"Lower sea loses depth or exceeds dimension floor");
                checks++;
            }
            var falling=FerryHeave.advance(upper+.65,0,false,lower+.65);
            check(falling.airborne() && falling.height()>lower+17,"Ferry must start falling, not snap down");
            boolean splashed=false;
            for (int tick=0;tick<120;tick++) {
                falling=FerryHeave.advance(falling.height(),falling.speed(),falling.airborne(),lower+.65);
                splashed|=falling.impact()>.5;
                check(falling.height()>=lower+.45,"Hull penetrates the landing surface");
            }
            check(splashed && !falling.airborne() && Math.abs(falling.height()-lower-.65)<.01,"Ferry does not splash and settle");
        }
        check(LimboCascades.waterY(UnderworldTerrain.SPAWN_X,UnderworldTerrain.SPAWN_Z)==47,"Entrance/dock sea moved");
        check(LimboCascades.current(0,200).equals(Vec3.ZERO),"Current leaks far from falls");
        var water=new net.minecraft.world.level.BlockGetter() {
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos){return null;}
            public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) {
                int top=LimboCascades.waterY(pos.getX(),pos.getZ());
                return (pos.getY()<=top && pos.getY()>top-30?net.minecraft.world.level.block.Blocks.WATER:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState();
            }
            public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos pos){return getBlockState(pos).getFluidState();}
            public int getHeight(){return 320;}
            public int getMinY(){return -64;}
        };
        for(int boundary=1;boundary<=4;boundary++)for(int side=-9;side<=9;side++) {
            int x=0,z=18+boundary*3200+side,y=LimboCascades.waterY(x,z);
            int[] depths=new int[18*18];
            for(int dz=0;dz<18;dz++)for(int dx=0;dx<18;dx++)depths[dz*18+dx]=LimboCascades.waterY(x+dx-9,z+dz-9)==y?6:0;
            check(Math.abs(WaterShoreline.sample(water,x,y,z)-WaterShoreline.attenuation(depths,18,9,9))<.00001,
                    "Boat shoreline attenuation diverges from the rendered tier at "+z);
        }
        int[] grid=new int[34*34];
        for(int z=0;z<34;z++)for(int x=0;x<34;x++)grid[z*34+x]=x<17?47:29;
        var faces=LimboCascadeMesh.build(0,0,grid,34,9);
        check(faces.size()==16,"Straight lip must create exactly one face per block");
        for(var f:faces) check(f.upper()==47 && f.lower()==29 && f.nx()==1 && f.nz()==0,"Wrong sheet orientation/height");
        Arrays.fill(grid,29);
        check(LimboCascadeMesh.build(0,0,grid,34,9).isEmpty(),"Flat sea has waterfall geometry");
        Arrays.fill(grid,Integer.MIN_VALUE);
        check(LimboCascadeMesh.build(0,0,grid,34,9).isEmpty(),"Unloaded chunk has invented geometry");
        for(int z=0;z<34;z++)for(int x=0;x<34;x++)grid[z*34+x]=z<17?11:-7;
        var rotated=LimboCascadeMesh.build(0,0,grid,34,9);
        check(rotated.size()==16 && rotated.getFirst().nz()==1,"North/south contour incorrect");
        for(int boundary=1;boundary<=4;boundary++)for(int angle=0;angle<12;angle++) {
            double theta=angle*Math.PI/6,radius=16000-boundary*3200;
            double x=radius*Math.sin(theta),z=16018-radius*Math.cos(theta);
            int baseX=((int)Math.floor(x)>>4)<<4,baseZ=((int)Math.floor(z)>>4)<<4,curtains=0;
            // At a chunk corner the high-side owner can be in any neighboring tile.
            for(int ox=-1;ox<=1;ox++)for(int oz=-1;oz<=1;oz++) {
            int tx=baseX+ox*16,tz=baseZ+oz*16;
            for(int dz=0;dz<34;dz++)for(int dx=0;dx<34;dx++)grid[dz*34+dx]=LimboCascades.waterY(tx+dx-9,tz+dz-9);
            var rim=LimboCascadeMesh.build(tx,tz,grid,34,9);
            curtains+=rim.size();
            for(var face:rim) {
                check(Math.abs(Math.hypot(face.x0(),face.z0()-16018)-radius)<.001,"Waterfall body lost the circular contour");
                check(Math.abs(face.x0()+face.lipX0()-Math.rint(face.x0()+face.lipX0()))<.00001
                        && Math.abs(face.z0()+face.lipZ0()-Math.rint(face.z0()+face.lipZ0()))<.00001,
                        "Smoothed curtain lip no longer joins the block surface");
            }
            }
            check(curtains>0,"Generated rim has no curtain");
        }
        check(LimboSeaRegions.caves(UnderworldTerrain.SPAWN_X,UnderworldTerrain.SPAWN_Z),"Spawn left the outer cave ring");
        check(LimboSeaRegions.sea(0,LimboSeaRegions.CENTER_Z)==LimboSeaRegions.Sea.COCYTUS,"Centre must be Cocytus");
        for(int x=-73;x<=72;x++)for(int z=-16;z<=17;z++)
            check(UnderworldTerrain.seaFloor(42,x,(int)LimboSeaRegions.CENTER_Z+z)==LimboCascades.waterYForTier(4)+1,
                    "Centre gate footprint must have a flat, dry foundation");
        for(int angle=0;angle<24;angle++)for(int sea=0;sea<5;sea++) {
            double theta=angle*Math.PI/12,r=16000-(sea+.5)*3200;
            double x=r*Math.sin(theta),z=LimboSeaRegions.CENTER_Z-r*Math.cos(theta);
            check(LimboSeaRegions.sea(x,z).ordinal()==sea,"Ring order/width varies with direction");
        }
        System.out.println("PASS concentric layout, four cascades, 48 contour crossings, "+checks+" terrain samples, 76 per-tier wave/boat shoreline checks, ferry free-fall/landing, cached mesh and unloaded edges.");
    }
}
