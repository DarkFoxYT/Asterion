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
        int checks=0;
        for (int boundary=1;boundary<=4;boundary++) for (int x : new int[]{-1600,-400,0,600,1400}) {
            double low=100,high=18000;
            for (int i=0;i<60;i++) {
                double mid=(low+high)*.5;
                if (LimboSeaRegions.distance(x,mid)<boundary*3200) low=mid; else high=mid;
            }
            double z=(low+high)*.5;
            int upper=LimboCascades.waterY(x,z-2),lower=LimboCascades.waterY(x,z+2);
            check(upper-lower==18,"Boundary must descend exactly 18 blocks");
            check(upper==47-(boundary-1)*18 && lower==47-boundary*18,"Sea ordering changed");
            Vec3 flow=LimboCascades.current(x,z-2);
            check(flow.dot(LimboCascades.outward(x,z))>0 && flow.length()<=.065001,"Current must go over the lip and remain bounded");
            for (int seed=0;seed<8;seed++) {
                int floor=UnderworldTerrain.seaFloor(seed,x,(int)Math.floor(z+8));
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
        System.out.println("PASS four cascades, 20 contour crossings, "+checks+" terrain samples, ferry free-fall/landing, cached mesh and unloaded edges.");
    }
}
