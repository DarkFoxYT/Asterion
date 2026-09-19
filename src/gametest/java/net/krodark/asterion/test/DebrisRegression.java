package net.krodark.asterion.test;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.port.client.PortPhysicsDebris;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

final class DebrisRegression {
    static void run(Minecraft client) {
        try {
            var cls=Class.forName("net.krodark.asterion.port.client.PortPhysicsDebris$Piece");
            var constructor=cls.getDeclaredConstructors()[0];constructor.setAccessible(true);
            var piecesField=PortPhysicsDebris.class.getDeclaredField("PIECES");piecesField.setAccessible(true);
            @SuppressWarnings("unchecked") var pieces=(java.util.List<Object>)piecesField.get(null);
            var airborne=constructor.newInstance(new Vec3(0,183,-3),6,1F,new java.util.Random(1),200);
            set(airborne,"sleeping",true);pieces.add(airborne);
            for(int i=0;i<8;i++)PortPhysicsDebris.tick(client);
            if((boolean)get(airborne,"sleeping") || ((Vec3)get(airborne,"position")).y>=182.9)
                throw new AssertionError("Unsupported resting rubble floats");
            pieces.remove(airborne);
            var embedded=constructor.newInstance(new Vec3(0.5,177.5,-3.5),6,.4F,new java.util.Random(2),200);
            pieces.add(embedded);PortPhysicsDebris.tick(client);
            if(((Vec3)get(embedded,"position")).distanceToSqr(new Vec3(.5,177.5,-3.5))<.01)
                throw new AssertionError("Embedded rubble did not recover");
            pieces.remove(embedded);
            PortPhysicsDebris.spawnDoors(new net.krodark.asterion.network.DoorBreakPayload(new net.minecraft.core.BlockPos(0,178,0),net.minecraft.core.Direction.NORTH,0,42));
            var doors=new java.util.ArrayList<Object>();var starts=new java.util.ArrayList<Vec3>();
            for(Object piece:pieces)if((int)get(piece,"variant")==7){doors.add(piece);starts.add((Vec3)get(piece,"position"));}
            if(doors.size()!=2)throw new AssertionError("Missing physical door leaves");
            for(int i=0;i<8;i++)PortPhysicsDebris.tick(client);
            for(int i=0;i<2;i++)if(((Vec3)get(doors.get(i),"position")).distanceToSqr(starts.get(i))<.1)
                throw new AssertionError("Door slab did not move");
            for(int i=0;i<220;i++)pieces.add(constructor.newInstance(new Vec3(0,184,0),6,.1F,new java.util.Random(i),200));
            var trim=PortPhysicsDebris.class.getDeclaredMethod("trim");trim.setAccessible(true);trim.invoke(null);
            if(!pieces.containsAll(doors))throw new AssertionError("Rubble burst evicted physical doors");
            pieces.clear();
            Asterion.LOGGER.info("ASTERION_DEBRIS PASSED: unsupported bodies wake, embedded rubble recovers, two physical door leaves move and survive rubble budget");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static Object get(Object o,String n)throws ReflectiveOperationException{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String n,Object v)throws ReflectiveOperationException{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
}
