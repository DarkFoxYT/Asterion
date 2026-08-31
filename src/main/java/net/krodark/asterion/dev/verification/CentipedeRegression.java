package net.krodark.asterion.dev.verification;

import net.krodark.asterion.entity.CentipedeChain;
import net.krodark.asterion.entity.CentipedeCollision;
import net.krodark.asterion.entity.CentipedeFrame;
import net.krodark.asterion.entity.CentipedeInteraction;
import net.krodark.asterion.entity.CentipedeMotion;
import net.krodark.asterion.entity.CentipedeSeats;
import net.krodark.asterion.entity.CentipedeTrail;
import net.krodark.asterion.entity.CentipedeSurfaceProbe;
import net.krodark.asterion.entity.CentipedeSegments;
import net.krodark.asterion.entity.CentipedeBodyConstraint;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Direction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Standalone regression executable: run with gradlew centipedeRegression (also part of check). */
public final class CentipedeRegression {
    private static int checks;
    private static final Vec3 NORTH = new Vec3(0, 0, -1);
    private static final Vec3 DOWN = CentipedeFrame.DOWN;
    private static final CentipedeCollision EMPTY = new CentipedeCollision(region -> List.of());

    public static void main(String[] args) throws Exception {
        frameRoundTrips();
        anchorTranslations();
        independentLinks();
        surfacesAndSweeps();
        climbingChain();
        cornerTrail();
        outsideCornerChain();
        contactProbe();
        lengthsAndBodySeparation();
        stableSeats();
        segmentPickingAndSaddles();
        smoothMotion();
        collisionCache();
        modelContract();
        System.out.println("Centipede regression: " + checks + " checks passed");
    }

    private static void frameRoundTrips() {
        Vec3[] normals = {DOWN, new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(1, -1, 1).normalize()};
        for (Vec3 normal : normals) for (int degrees = 0; degrees < 360; degrees += 3) {
            double angle = Math.toRadians(degrees);
            Vec3 initial = CentipedeFrame.tangent(NORTH, normal, new Vec3(0, 1, 0));
            Vec3 right = initial.cross(normal.scale(-1));
            Vec3 forward = initial.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
            Quaternionf wanted = CentipedeFrame.rotation(normal, forward);
            Vector3f e = CentipedeFrame.boneAngles(wanted);
            Quaternionf rendered = new Quaternionf().rotationZYX(e.z, e.y, e.x);
            near(vector(rendered.transform(new Vector3f(0, 1, 0))), normal.scale(-1), 0.0005,
                    "belly normal, " + normal + " yaw=" + degrees);
            near(vector(rendered.transform(new Vector3f(0, 0, -1))), forward, 0.0005,
                    "surface heading yaw=" + degrees);
        }
        for (int step = 0; step <= 100; step++) {
            Vec3 normal = DOWN.lerp(new Vec3(1, 0, 0), step / 100.0).normalize();
            for (int yaw = 0; yaw < 360; yaw += 10) {
                Vec3 forward = CentipedeFrame.tangent(new Vec3(Math.cos(Math.toRadians(yaw)),
                        Math.sin(Math.toRadians(yaw)), 0.35), normal, NORTH);
                Vector3f r = CentipedeFrame.boneAngles(CentipedeFrame.rotation(normal, forward));
                Vec3 up = vector(new Quaternionf().rotationZYX(r.z, r.y, r.x).transform(new Vector3f(0, 1, 0)));
                near(up, normal.scale(-1), 0.0005, "belly frame during corner transition");
            }
        }
        // Demonstrate that the prior XYZ extraction is caught by this same reconstruction.
        Quaternionf oldCase = CentipedeFrame.rotation(new Vec3(1, 0, 0), new Vec3(0, 1, 1).normalize());
        Vector3f wrong = oldCase.getEulerAnglesXYZ(new Vector3f());
        Vec3 wrongUp = vector(new Quaternionf().rotationZYX(wrong.z, wrong.y, wrong.x).transform(new Vector3f(0, 1, 0)));
        require(wrongUp.distanceTo(new Vec3(-1, 0, 0)) > 0.1, "regression must detect the old Euler-order roll");
    }

    private static void anchorTranslations() {
        for (int i = -32; i <= 32; i++) {
            Vec3 origin = new Vec3(217.5, 63.2, -41.7);
            Vec3 world = origin.add(i * 0.43, i * 0.22, -i * 0.91);
            Vector3f p = CentipedeFrame.boneTranslation(world.subtract(origin));
            // Actual GeckoLib BoneSnapshot translation, including its mirrored X and render scale.
            Vec3 rendered = origin.add(-p.x / 16.0 * 2, p.y / 16.0 * 2, p.z / 16.0 * 2);
            near(rendered, world, 0.00001, "world-space anchor is independent of player yaw");
        }
    }

    private static void independentLinks() {
        CentipedeChain chain = new CentipedeChain();
        Vec3 head = new Vec3(0, 0.655, 0);
        chain.tick(head, DOWN, NORTH, 7, EMPTY);
        Vec3[] fixed = new Vec3[7];
        for (int i = 0; i < 7; i++) fixed[i] = chain.sample(i, 1).position();
        for (int yaw = 0; yaw < 360; yaw += 5) {
            double a = Math.toRadians(yaw);
            chain.tick(head, DOWN, new Vec3(Math.sin(a), 0, -Math.cos(a)), 7, EMPTY);
            for (int i = 1; i < 7; i++) near(chain.sample(i, 1).position(), fixed[i], 1e-9, "stationary turn moved link " + i);
        }
        for (int tick = 0; tick < 240; tick++) {
            double angle = tick * 0.022;
            Vec3 heading = new Vec3(Math.sin(angle), 0, -Math.cos(angle));
            head = head.add(heading.scale(0.18));
            chain.tick(head, DOWN, heading, 7, EMPTY);
            for (int i = 1; i < 7; i++) {
                var pose = chain.sample(i, 1);
                require(pose.position().distanceTo(chain.sample(i - 1, 1).position()) <= CentipedeFrame.LINK_LENGTH + 1e-8,
                        "stretched link on empty ground");
                near(pose.normal(), DOWN, 1e-8, "ground steering changed support normal");
            }
        }
        Vec3 before = chain.sample(6, 1).position();
        for (int i = 0; i < 300; i++) chain.sample(6, i % 100 / 100F);
        near(chain.sample(6, 1).position(), before, 0, "render sampling advanced physics");
        chain.tick(head, DOWN, NORTH, 32, EMPTY);
        require(Double.isFinite(chain.sample(31, 1).position().x), "dynamic extension");
        chain.tick(new Vec3(100, 30, 100), DOWN, NORTH, 3, EMPTY);
        require(chain.sample(2, 1).position().distanceTo(new Vec3(100, 30, 100)) < 5, "teleport reset");
    }

    private static void surfacesAndSweeps() {
        AABB floor = new AABB(-100, -3, -100, 100, 0, 100);
        AABB wall = new AABB(0, -100, -100, 4, 100, 100);
        AABB ceiling = new AABB(-100, 4, -100, 100, 7, 100);
        AABB slab = new AABB(-4, 0, -4, 4, 0.5, 4);
        sweepCase(floor, new Vec3(0, 2, 0), new Vec3(4, -5, 0), DOWN, NORTH);
        sweepCase(wall, new Vec3(-3, 2, 0), new Vec3(10, 4, 2), new Vec3(1, 0, 0), new Vec3(0, 1, 0));
        sweepCase(ceiling, new Vec3(0, 2, 0), new Vec3(3, 8, 0), new Vec3(0, 1, 0), NORTH);
        sweepCase(slab, new Vec3(0, 2, 0), new Vec3(0, -1, 0), DOWN, NORTH);
        CentipedeCollision support = new CentipedeCollision(region -> List.of(floor, wall));
        var grounded = support.resolve(new Vec3(-2, 0.7, 0), new Vec3(-2, 0.7, 0), DOWN, NORTH);
        near(grounded.normal(), DOWN, 1e-8, "own floor support instead of leader wall normal");
        var climbing = support.resolve(new Vec3(-0.7, 3, 0), new Vec3(-0.7, 3, 0), DOWN, new Vec3(0, 1, 0));
        near(climbing.normal(), new Vec3(1, 0, 0), 1e-8, "segment discovers its own wall");
    }

    private static void sweepCase(AABB obstacle, Vec3 from, Vec3 to, Vec3 normal, Vec3 forward) {
        CentipedeCollision collision = new CentipedeCollision(region -> List.of(obstacle));
        var result = collision.resolve(from, to, normal, forward);
        AABB body = CentipedeCollision.volume(result.position(), CentipedeFrame.extents(result.normal(), forward));
        require(!body.intersects(obstacle), "sweep tunnels through block " + obstacle + " result=" + result.position());
    }

    private static void climbingChain() {
        List<AABB> blocks = List.of(new AABB(-100, -4, -100, 100, 0, 100),
                new AABB(4, 0, -100, 8, 40, 100));
        var collision = new CentipedeCollision(region -> blocks);
        var chain = new CentipedeChain();
        for (int tick = 0; tick < 90; tick++) {
            Vec3 normal = tick < 25 ? DOWN : new Vec3(1, 0, 0);
            Vec3 forward = tick < 25 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 head = tick < 25 ? new Vec3(-2 + tick * 0.18, 0.655, 0)
                    : new Vec3(4 - 0.655, 1.1 + (tick - 25) * 0.18, 0);
            chain.tick(head, normal, forward, 7, collision);
            for (int i = 0; i < 7; i++) {
                var pose = chain.sample(i, 1);
                AABB body = CentipedeCollision.volume(pose.position(), CentipedeFrame.extents(pose.normal(), pose.forward()));
                if (i > 0) require(pose.position().distanceTo(chain.sample(i - 1, 1).position()) < CentipedeFrame.LINK_LENGTH + .35,
                        "corner pulled chain apart tick=" + tick + " link=" + i + " gap=" + pose.position().distanceTo(chain.sample(i - 1, 1).position()));
                for (AABB block : blocks) require(!body.intersects(block), "climbing intersection tick=" + tick + " link=" + i);
                require(Math.abs(pose.normal().dot(pose.forward())) < 1e-6, "heading leaves surface plane");
                for (float partial : new float[]{0.25F, 0.5F, 0.75F}) {
                    var interpolated = chain.sample(i, partial);
                    require(Math.abs(interpolated.normal().dot(interpolated.forward())) < 1e-6,
                            "interpolated heading leaves surface plane");
                    AABB interpolatedBody = CentipedeCollision.volume(interpolated.position(),
                            CentipedeFrame.extents(interpolated.normal(), interpolated.forward()));
                    for (AABB block : blocks) require(!interpolatedBody.intersects(block),
                            "interpolated collision tick=" + tick + " link=" + i + " partial=" + partial);
                }
            }
        }
    }

    private static void cornerTrail() {
        var trail = new CentipedeTrail();
        trail.reset(new CentipedeChain.Pose(Vec3.ZERO, DOWN, new Vec3(1, 0, 0)));
        for (int i = 1; i <= 40; i++) trail.record(new CentipedeChain.Pose(new Vec3(i * .1, 0, 0), DOWN, new Vec3(1, 0, 0)));
        for (int i = 1; i <= 40; i++) trail.record(new CentipedeChain.Pose(new Vec3(4, i * .1, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0)));
        near(trail.behind(5).position(), new Vec3(3, 0, 0), 1e-8, "tail must visit corner, not shortcut the wall");
        near(trail.behind(2).position(), new Vec3(4, 2, 0), 1e-8, "front section follows up wall");
        near(trail.behind(5).normal(), DOWN, 1e-8, "rear keeps its own historical floor support");
        Vec3 stationaryTail = trail.behind(5).position();
        for (int i = 0; i < 50; i++) trail.record(new CentipedeChain.Pose(new Vec3(4, 4, 0), new Vec3(1, 0, 0), NORTH));
        near(trail.behind(5).position(), stationaryTail, 0, "looking around must not advance the route");
        for (int i = 1; i <= 4000; i++) trail.record(new CentipedeChain.Pose(new Vec3(4, 4 + i * .1, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0)));
        near(trail.behind(31 * CentipedeFrame.LINK_LENGTH).position(),
                new Vec3(4, 404 - 31 * CentipedeFrame.LINK_LENGTH, 0), 1e-7, "history retains the longest dynamic tail");
    }

    private static void outsideCornerChain() {
        AABB block = new AABB(0, -20, 0, 30, 30, 30);
        var collision = new CentipedeCollision(region -> List.of(block));
        var chain = new CentipedeChain();
        for (int tick = 0; tick < 150; tick++) {
            Vec3 head, normal, forward;
            if (tick < 30) {
                head = new Vec3(-.655, 5, 6 - tick * .2);
                normal = new Vec3(1, 0, 0); forward = NORTH;
            } else if (tick < 60) {
                double angle = (tick - 30) * Math.PI / 60;
                normal = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                head = new Vec3(-.655 * normal.x, 5, -.655 * normal.z);
                forward = new Vec3(Math.sin(angle), 0, -Math.cos(angle));
            } else {
                head = new Vec3((tick - 60) * .2, 5, -.655);
                normal = new Vec3(0, 0, 1); forward = new Vec3(1, 0, 0);
            }
            chain.tick(head, normal, forward, 7, collision);
            for (int i = 0; i < 7; i++) {
                var pose = chain.sample(i, 1);
                if (i > 0) require(pose.position().distanceTo(chain.sample(i - 1, 1).position()) < CentipedeFrame.LINK_LENGTH + .45,
                        "outside corner separated link " + i + " tick=" + tick);
                for (float partial : new float[]{.25F, .5F, .75F, 1}) {
                    var p = chain.sample(i, partial);
                    require(!CentipedeCollision.volume(p.position(), CentipedeFrame.extents(p.normal(), p.forward())).intersects(block),
                            "outside-corner section clips world tick=" + tick + " link=" + i);
                    require(Math.abs(p.normal().dot(p.forward())) < 1e-6, "outside-corner belly frame rolls");
                }
            }
        }
        require(chain.sample(6, 1).position().z < 0, "last section failed to round the outside corner");
    }

    private static void contactProbe() {
        AABB body = new AABB(-1, 0, -1, 1, .82, 1);
        AABB wall = new AABB(1.55, -3, -5, 3, 8, 5);
        var early = CentipedeSurfaceProbe.ahead(body, new Vec3(.3, 0, 0), Direction.DOWN, List.of(wall));
        require(early == null, "distant wall must not trigger climbing or tilt");
        var closer = CentipedeSurfaceProbe.ahead(body.move(.49, 0, 0), new Vec3(.3, 0, 0), Direction.DOWN, List.of(wall));
        require(closer != null && closer.face() == Direction.EAST && closer.gap() < .08, "near-contact wall may hand off");
        near(closer.normal(), DOWN, 0, "probe must not tilt before confirmed attachment");
        require(CentipedeSurfaceProbe.ahead(body, new Vec3(.3, 0, 0), Direction.DOWN, List.of()) == null,
                "empty space cannot be a climbable surface");
        require(CentipedeSurfaceProbe.ahead(body, Vec3.ZERO, Direction.DOWN, List.of(wall)) == null, "stationary mount doesn't attach from looking");
        require(CentipedeSurfaceProbe.ahead(body, new Vec3(0, 0, .3), Direction.DOWN, List.of(wall)) == null, "parallel wall doesn't steal attachment");
        require(CentipedeSurfaceProbe.ahead(body, new Vec3(-.3, 0, 0), Direction.DOWN, List.of(wall)) == null, "wall behind isn't anticipated");
        AABB ceiling = new AABB(-5, .88, -5, 5, 3, 5);
        var nextWall = CentipedeSurfaceProbe.ahead(body, new Vec3(0, .3, 0), Direction.EAST, List.of(ceiling));
        require(nextWall != null && nextWall.face() == Direction.UP, "wall-to-ceiling hand-off at real contact");
        AABB outside = new AABB(-2.1, 2, -.1, 0, 2.82, 2);
        AABB endingWall = new AABB(0, 0, -5, 4, 8, 0);
        var wrap = CentipedeSurfaceProbe.aroundEdge(outside, new Vec3(0, 0, .3), Direction.EAST, List.of(endingWall));
        require(wrap == null, "outside corner cannot invent contact on the hidden side of a wall");
        require(CentipedeSurfaceProbe.aroundEdge(outside, new Vec3(0, 0, .3), Direction.EAST,
                List.of(endingWall, new AABB(0, 0, 0, 4, 8, 4))) == null, "block seam must not trigger corner wrapping");
    }

    private static void lengthsAndBodySeparation() throws Exception {
        RandomSource random = RandomSource.create(712);
        java.util.Set<Integer> lengths = new java.util.HashSet<>();
        for (int i = 0; i < 10000; i++) {
            int count = CentipedeSegments.randomCount(random);
            require(count >= 5 && count <= 12, "spawn length out of range");
            lengths.add(count);
        }
        require(lengths.size() == 8, "spawn length is not varied");
        var argument = com.mojang.brigadier.arguments.IntegerArgumentType.integer(CentipedeSegments.MIN, CentipedeSegments.MAX);
        require(argument.parse(new com.mojang.brigadier.StringReader("3")) == 3, "command lower boundary");
        require(argument.parse(new com.mojang.brigadier.StringReader("32")) == 32, "command upper boundary");
        for (String invalid : new String[]{"-1", "0", "2", "33", "100000"}) {
            boolean rejected = false;
            try { argument.parse(new com.mojang.brigadier.StringReader(invalid)); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
            require(rejected, "command accepted unsupported segment count " + invalid);
        }
        for (Vec3 normal : new Vec3[]{DOWN, new Vec3(1, 0, 0), new Vec3(0, 0, 1)}) {
            Vec3 forward = CentipedeFrame.tangent(NORTH, normal, new Vec3(0, 1, 0));
            Vec3 center = new Vec3(3, 5, 7);
            for (double distance : new double[]{0, .01, .5, 1, 1.44}) {
                Vec3 other = center.add(forward.scale(distance));
                Vec3 separated = CentipedeBodyConstraint.separate(center, other, normal, forward);
                require(separated.distanceTo(other) >= CentipedeBodyConstraint.CORE_SPACING - 1e-8, "body cores still overlap");
                require(Math.abs(separated.subtract(center).dot(normal)) < 1e-8, "self avoidance lifted segment off its surface");
            }
            Vec3 obstacle = center.add(forward.scale(2));
            double fraction = CentipedeBodyConstraint.movementFraction(center, forward, obstacle);
            require(fraction > 0 && fraction < .6, "head must stop before entering its own tail");
            require(center.add(forward.scale(fraction)).distanceTo(obstacle) >= CentipedeBodyConstraint.CORE_SPACING,
                    "head sweep tunnels through body");
            require(CentipedeBodyConstraint.movementFraction(center, forward.scale(-1), obstacle) == 1, "must allow moving away from tail");
            require(CentipedeBodyConstraint.movementFraction(center, forward.scale(-1), center.add(forward.scale(.1))) == 1,
                    "must allow escaping an existing overlap");
        }
    }

    private static void stableSeats() {
        var seats = new CentipedeSeats();
        var rear = new java.util.UUID(0, 1);
        var driver = new java.util.UUID(0, 2);
        var second = new java.util.UUID(0, 3);
        require(seats.claim(rear, 5, 7), "rear rider may board before driver");
        require(seats.seatOf(rear) == 5 && seats.firstFree(7) == 0, "rear rider did not take control");
        require(seats.claim(driver, 0, 7), "driver can join after passengers");
        require(!seats.claim(second, 5, 7), "occupied seat rejected");
        require(!seats.claim(rear, 2, 7), "one seat per rider");
        require(!seats.claim(second, -1, 7) && !seats.claim(second, 7, 7), "out of range seat rejected");
        require(seats.claim(second, 2, 7), "another segment remains available");
        seats.release(driver);
        require(seats.seatOf(rear) == 5 && seats.seatOf(second) == 2, "dismount reordered passengers");
        require(seats.firstFree(7) == 0, "driver seat empty; AI may run, nobody promoted");
        var decoded = new CentipedeSeats();
        decoded.decode(seats.encode());
        require(decoded.seatOf(rear) == 5 && decoded.seatOf(second) == 2, "seat sync/save round trip");
        decoded.decode("-1=" + rear + ";999=" + second + ";0=invalid;5=" + rear + ";1=" + rear + ";");
        require(decoded.seatOf(rear) == 5 && decoded.seatOf(second) == -1, "invalid/duplicate saved seats rejected");
        seats.release(rear);
        require(seats.claim(rear, 31, 32), "dynamic last segment can be occupied");
    }

    private static void segmentPickingAndSaddles() {
        Vec3[] normals = {DOWN, new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(1, -1, 0).normalize()};
        for (Vec3 normal : normals) for (int angle = 0; angle < 360; angle += 30) {
            Vec3 forward = CentipedeFrame.tangent(NORTH, normal, new Vec3(0, 1, 0));
            Vec3 right = forward.cross(normal.scale(-1));
            double yaw = Math.toRadians(angle);
            forward = forward.scale(Math.cos(yaw)).add(right.scale(Math.sin(yaw)));
            var poses = new CentipedeChain.Pose[32];
            for (int i = 0; i < 32; i++) poses[i] = new CentipedeChain.Pose(
                    new Vec3(4, 20, -10).subtract(forward.scale(i * CentipedeFrame.LINK_LENGTH)), normal, forward);
            for (int seat : new int[]{0, 1, 6, 31}) {
                var pose = poses[seat];
                Vec3 eye = pose.position().subtract(normal.scale(3));
                var hit = CentipedeInteraction.pick(eye, eye.add(normal.scale(5)), poses.length, i -> poses[i]);
                require(hit != null && hit.seat() == seat, "direct body pick on any surface seat=" + seat);
                require(CentipedeInteraction.contains(hit.point(), seat, pose, .001), "server recognizes picked segment");
                Vec3 saddle = CentipedeInteraction.saddle(pose, seat);
                Vec3 localSaddle = CentipedeInteraction.toLocal(saddle, pose);
                near(localSaddle, new Vec3(0, seat == 0 ? .23 : .42, seat == 0 ? -1.45 : 0), 1e-8,
                        "seat matches actual shell, with no global Y offset");
                Vec3 localAttachment = new Vec3(0, .6, 0);
                Vec3 attachment = CentipedeInteraction.toWorld(localAttachment, pose).subtract(pose.position());
                Vec3 riderFeet = saddle.subtract(attachment);
                near(riderFeet.add(attachment), saddle, 1e-8, "rider attachment stays on shell");
            }
            Vec3 eye = CentipedeInteraction.toWorld(new Vec3(0, 3, -1.45), poses[0]);
            var headHit = CentipedeInteraction.pick(eye, eye.add(normal.scale(5)), 32, i -> poses[i]);
            require(headHit != null && headHit.seat() == 0, "head is the driver seat");
            Vec3 miss = CentipedeInteraction.toWorld(new Vec3(3, 3, 0), poses[0]);
            require(CentipedeInteraction.pick(miss, miss.add(normal.scale(5)), 32, i -> poses[i]) == null,
                    "empty space next to model must not mount");
            Vec3 shortEye = poses[5].position().subtract(normal.scale(3));
            require(CentipedeInteraction.pick(shortEye, shortEye.add(normal), 32, i -> poses[i]) == null,
                    "ray cannot mount beyond its reach");
        }
    }

    private static void smoothMotion() {
        Vec3[] normals = {DOWN, new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, -1)};
        for (Vec3 normal : normals) {
            Vec3 heading = CentipedeFrame.tangent(NORTH, normal, new Vec3(0, 1, 0));
            Vec3 wanted = heading.scale(-1);
            for (int tick = 0; tick < 100; tick++) {
                Vec3 next = CentipedeMotion.steer(heading, wanted, normal, .05);
                require(Math.acos(Math.min(1, heading.dot(next))) <= .050001, "AI turn-rate cap");
                require(Math.abs(next.dot(normal)) < 1e-9, "AI steering left surface plane");
                heading = next;
            }
            near(heading, wanted, 1e-8, "AI can finish a 180-degree turn without snapping");
        }
        for (double delta : new double[]{-1, -.1, 0, .1, 1})
            for (double velocity : new double[]{-5, -.1, 0, .1, 5})
                for (int step = 0; step <= 100; step++) {
                    Vec3 a = new Vec3(1, 2, 3), b = a.add(delta, delta, delta);
                    Vec3 p = CentipedeMotion.interpolate(a, b, new Vec3(velocity, velocity, velocity),
                            new Vec3(-velocity, -velocity, -velocity), step / 100.0);
                    require(p.x >= Math.min(a.x, b.x) - 1e-9 && p.x <= Math.max(a.x, b.x) + 1e-9,
                            "smooth interpolation overshot into a neighboring block");
                    if (step == 0) near(p, a, 0, "interpolation starts at previous tick");
                    if (step == 100) near(p, b, 1e-9, "interpolation ends at current tick");
                }
        var chain = new CentipedeChain();
        chain.tick(new Vec3(0, .655, 0), DOWN, NORTH, 7, EMPTY);
        for (int i = 1; i <= 50; i++) chain.tick(new Vec3(0, .655, -.2 * i), DOWN, NORTH, 7, EMPTY);
        require(chain.speed(6, 1) > .1, "moving tail animates its own legs");
        Vec3 head = chain.sample(0, 1).position();
        float[] phases = new float[7];
        for (int i = 0; i < 7; i++) phases[i] = chain.gait(i, 1);
        for (int tick = 0; tick < 50; tick++) chain.tick(head, DOWN, NORTH, 7, EMPTY);
        for (int i = 0; i < 7; i++) {
            require(Math.abs(chain.gait(i, 1) - phases[i]) < .0001, "stationary legs keep their planted phase");
            require(chain.speed(i, 1) < .0001, "gait amplitude eases to rest");
        }
        for (int tick = 0; tick < 100; tick++) chain.tick(head, DOWN, NORTH.scale(-1), 7, EMPTY);
        near(chain.sample(0, 1).forward(), NORTH.scale(-1), 1e-7, "exact reversal must not get stuck in normalized lerp");
        near(chain.sample(0, 1).position(), head, 0, "stationary reversal moved the head");
    }

    private static void collisionCache() {
        int[] queries = {0};
        AABB floor = new AABB(-100, -3, -100, 100, 0, 100);
        var world = new CentipedeCollision(region -> { queries[0]++; return List.of(floor); });
        var chain = new CentipedeChain();
        chain.tick(new Vec3(0, .655, 0), DOWN, NORTH, 7, world);
        queries[0] = 0;
        chain.tick(new Vec3(0, .655, -.2), DOWN, NORTH, 7, world);
        require(queries[0] <= 7, "substeps must reuse world voxel queries");
        var snapshot = world.cachedIn(new AABB(-2, -2, -2, 2, 2, 2));
        var outlier = snapshot.resolve(new Vec3(30, 2, 0), new Vec3(30, -2, 0), DOWN, NORTH);
        require(outlier.position().y >= CentipedeFrame.HALF_HEIGHT, "cache fallback lost out-of-range collision");
    }

    private static void modelContract() throws Exception {
        Path path = Path.of("src/main/resources/assets/asterion/geckolib/models/entity/centipede.geo.json");
        JsonArray bones = JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        Map<String, JsonObject> byName = new HashMap<>();
        for (var bone : bones) {
            JsonObject b = bone.getAsJsonObject();
            require(byName.put(b.get("name").getAsString(), b) == null, "duplicate bone name");
        }
        require(byName.containsKey("head_anchor"), "independent head anchor");
        JsonObject template = byName.get("segment_0");
        for (int i = 0; i < 32; i++) {
            JsonObject b = byName.get("segment_" + i);
            require(b.get("pivot").equals(template.get("pivot")), "residual static pivot offset in segment " + i);
            require(b.get("cubes").equals(template.get("cubes")), "residual static geometry offset in segment " + i);
            require(b.get("parent").getAsString().equals("segment_anchor_" + i), "wrong rotation parent");
        }
    }

    private static Vec3 vector(Vector3f v) { return new Vec3(v.x, v.y, v.z); }
    private static void near(Vec3 actual, Vec3 expected, double tolerance, String message) {
        require(actual.distanceTo(expected) <= tolerance, message + " actual=" + actual + " expected=" + expected);
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
