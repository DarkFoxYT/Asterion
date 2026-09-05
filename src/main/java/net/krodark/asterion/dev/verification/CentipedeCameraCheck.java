package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.ScarletCentipedeEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Exercises the injected camera against a mounted client player on every surface. */
final class CentipedeCameraCheck {
    static void run(Minecraft client) {
        var player = client.player;
        Vec3 original = player.position();
        float yaw = player.getYRot(), pitch = player.getXRot();
        float headYaw = player.getYHeadRot();
        CameraType perspective = client.options.getCameraType();
        ScarletCentipedeEntity mount = Asterion.SCARLET_CENTIPEDE.create(client.level, EntitySpawnReason.EVENT);
        try {
            mount.setPos(original.x, 220, original.z);
            player.setPos(original.x, 223, original.z);
            if (!player.startRiding(mount, true, false)) throw new AssertionError("Camera fixture could not mount");
            player.setYRot(37);
            player.setYHeadRot(37);
            player.setXRot(23);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            Camera camera = new Camera();
            camera.setLevel(client.level);
            camera.setEntity(player);
            var align = Camera.class.getDeclaredMethod("alignWithEntity", float.class);
            align.setAccessible(true);
            var surface = ScarletCentipedeEntity.class.getDeclaredField("smoothedAttachmentNormal");
            surface.setAccessible(true);
            for (var direction : net.minecraft.core.Direction.values()) {
                Vec3 normal = direction.getUnitVec3();
                surface.set(mount, normal);
                align.invoke(camera, 1F);
                Vec3 expectedEye = mount.passengerPosition(player, 1).add(normal.scale(-player.getEyeHeight()));
                if (camera.position().distanceTo(expectedEye) > .001)
                    throw new AssertionError("Rider camera detached from model on " + direction);
                Quaternionf tilt = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                        new Vector3f((float)-normal.x, (float)-normal.y, (float)-normal.z));
                Vec3 view = Vec3.directionFromRotation(player.getViewXRot(1), player.getViewYRot(1));
                Vector3f expectedView = tilt.transform(new Vector3f((float)view.x, (float)view.y, (float)view.z));
                if (camera.forwardVector().distance(expectedView) > .001)
                    throw new AssertionError("Rider view rotation disagrees with steering on " + direction);
                if (Math.abs(camera.forwardVector().dot(camera.upVector())) > .001)
                    throw new AssertionError("Camera basis is not orthogonal");
            }
            surface.set(mount, new Vec3(0, -1, 0));
            var chainField = ScarletCentipedeEntity.class.getDeclaredField("bodyChain");
            chainField.setAccessible(true);
            var chain = (net.krodark.asterion.entity.CentipedeChain)chainField.get(mount);
            var empty = new net.krodark.asterion.entity.CentipedeCollision(area -> java.util.List.of());
            for (int tick = 0; tick < 50; tick++) {
                align.invoke(camera, 1F);
                Vec3 previousEye = camera.position();
                chain.tick(new Vec3(original.x, 220 + (tick % 2 == 0 ? .03 : -.03), original.z - tick * .2),
                        new Vec3(0, -1, 0), new Vec3(0, 0, -1), 7, empty);
                align.invoke(camera, 0F);
                if (tick > 4 && camera.position().distanceTo(previousEye) > .0001)
                    throw new AssertionError("Mounted camera jumps between smoothed ticks");
                for (float partial : new float[]{.1F, .4F, .75F, 1F}) {
                    align.invoke(camera, partial);
                    Vec3 expected = mount.passengerPosition(player, partial)
                            .add(mount.passengerNormal(player, partial).scale(-player.getEyeHeight()));
                    if (camera.position().distanceTo(expected) > .001)
                        throw new AssertionError("Moving rider camera detached from smoothed seat");
                }
            }
            Asterion.LOGGER.info("PASS: continuous moving centipede camera, shared smoothed seats and body poses");
            Asterion.LOGGER.info("PASS: mounted first-person camera eyes, view direction and basis on all six surfaces");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        } finally {
            player.stopRiding();
            player.setPos(original);
            player.setYRot(yaw);
            player.setYHeadRot(headYaw);
            player.setXRot(pitch);
            client.options.setCameraType(perspective);
            mount.discard();
        }
    }
}
