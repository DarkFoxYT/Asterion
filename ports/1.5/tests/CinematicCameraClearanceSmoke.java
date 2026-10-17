import net.krodark.asterion.physics.CinematicMotion;
import net.minecraft.world.phys.Vec3;

/** Geometry regressions without opening a Minecraft client. */
public final class CinematicCameraClearanceSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Vec3 focus = new Vec3(0, 3.2, 1.5);
        Vec3 desired = new Vec3(0, 2.1, 13);
        require(CinematicMotion.clearCamera(focus, desired, .35, (a, b) -> null)
                .distanceTo(desired) < 1e-9, "Clear shot moves");
        Vec3 end = new Vec3(0, 0, 10);
        Vec3 corner = CinematicMotion.clearCamera(Vec3.ZERO, end, .35, (a, b) ->
                Math.abs(a.x) > .3 && Math.abs(a.y) > .3 ? new Vec3(a.x, a.y, 5) : null);
        require(Math.abs(corner.z - 4.65) < 1e-9, "Near-plane corner clips past obstacle");
        // The old origin was in the closed door at z=0 and collapsed the shot.
        Vec3 door = CinematicMotion.clearCamera(focus, desired, .35, (a, b) ->
                a.z <= .625 ? a : null);
        require(door.distanceTo(desired) < 1e-9, "Closed door pulls camera into panel");
        for (int frame = 0; frame <= 300; frame++) {
            Vec3 eye = new Vec3(0, 2, 2);
            Vec3 returning = end.lerp(eye, frame / 300.0);
            Vec3 safe = CinematicMotion.clearCamera(eye, returning, .35, (a, b) ->
                    b.z > 5 ? new Vec3(a.x, a.y, 5) : null);
            require(Double.isFinite(safe.lengthSqr()) && safe.z <= 5,
                    "Return crosses wall at frame " + frame);
            if (frame == 300) require(safe.equals(eye), "Camera does not return to player");
        }
        require(CinematicMotion.clearCamera(focus, focus, .35, (a, b) -> a).equals(focus),
                "Zero-length shot is unstable");
        System.out.println("PASS: door origin, diagonal near-plane clearance, 301 return samples, exact handoff.");
    }
}
