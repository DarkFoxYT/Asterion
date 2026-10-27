package net.krodark.asterion.client;

import java.util.ArrayList;
import java.util.Random;
import java.util.List;
import net.krodark.asterion.compat.FlashbackAppearanceCompatibility;
import net.krodark.asterion.effect.PeripheralWitnessPolicy;
import net.krodark.asterion.effect.WitnessCaptureFence;
import net.krodark.asterion.entity.RenderRemnantEntity;
import net.krodark.asterion.game.RenderRemnantContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PeripheralWitnessController {
    private static final Random RANDOM = new Random();
    private static final ArrayList<RenderRemnantEntity> WITNESSES = new ArrayList<>();
    private static ClientLevel currentLevel;
    private static int cooldown = 60;
    private static int nextEntityId = -2_100_000_000;
    private PeripheralWitnessController() {}

    public static void tick(Minecraft client) {
        if (client.level != currentLevel) { clear(); currentLevel = client.level; cooldown = 40 + RANDOM.nextInt(60); }
        if (client.level == null || client.player == null
                || !client.player.isAlive() || FlashbackAppearanceCompatibility.isPlayingReplay()) { clear(); return; }
        if (client.isPaused()) return;
        var camera = client.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        var forward = camera.forwardVector();
        var up = camera.upVector();
        var left = camera.leftVector();
        double tan = Math.tan(Math.toRadians(Math.max(30, camera.getFov())) * .5);
        double aspect = client.getWindow().getWidth() / (double) Math.max(1, client.getWindow().getHeight());
        WITNESSES.removeIf(witness -> {
            if (witness.isRemoved()) return true;
            Vec3 delta = witness.position().add(0, 1.6, 0).subtract(eye);
            double depth = delta.x * forward.x() + delta.y * forward.y() + delta.z * forward.z();
            double x = -(delta.x * left.x() + delta.y * left.y() + delta.z * left.z()) / Math.max(.001, depth * tan * aspect);
            double y = (delta.x * up.x() + delta.y * up.y() + delta.z * up.z()) / Math.max(.001, depth * tan);
            if (!witness.persistentWitness) {
                if (PeripheralWitnessPolicy.onScreen(x, y, depth)) witness.visibleTicks++;
                if (PeripheralWitnessPolicy.focused(x, y, depth)) witness.focusTicks++;
                else witness.focusTicks = Math.max(0, witness.focusTicks - 1);
                if (witness.tickCount >= PeripheralWitnessPolicy.LIFETIME
                        || witness.focusTicks >= PeripheralWitnessPolicy.FOCUS_LIMIT
                        || witness.visibleTicks >= PeripheralWitnessPolicy.VISIBLE_LIMIT
                        || delta.lengthSqr() < 6 * 6 || delta.lengthSqr() > 96 * 96) { witness.discard(); return true; }
            }
            Vec3 towardPlayer = client.player.getEyePosition().subtract(witness.position().add(0, 2.3, 0));
            witness.setYRot((float) Math.toDegrees(Math.atan2(-towardPlayer.x, towardPlayer.z)));
            witness.setXRot((float) Math.max(-65, Math.min(65, -Math.toDegrees(Math.atan2(towardPlayer.y,
                    Math.sqrt(towardPlayer.x * towardPlayer.x + towardPlayer.z * towardPlayer.z))))));
            witness.appearance = PeripheralWitnessPolicy.opacity(witness.tickCount, witness.focusTicks, witness.visibleTicks, witness.persistentWitness)
                    * (float) PeripheralWitnessPolicy.peripheralOpacity(x, y, depth);
            return false;
        });
        WitnessCaptureFence.observersPresent(!WITNESSES.isEmpty());
        if (--cooldown > 0 || WITNESSES.stream().anyMatch(w -> w.persistentWitness)
                || WITNESSES.size() >= PeripheralWitnessPolicy.MAX_NATURAL_WITNESSES || client.screen != null || client.player.isSpectator()
                || !client.level.dimension().identifier().getNamespace().equals("asterion")
                || net.krodark.asterion.client.cinematic.CinematicHud.isHidden()) return;
        int count = Math.min(PeripheralWitnessPolicy.MAX_NATURAL_WITNESSES - WITNESSES.size(), RANDOM.nextInt(3) == 0 ? 2 : 1);
        cooldown = spawn(client, false, count) > 0 ? 100 + RANDOM.nextInt(140) : 40 + RANDOM.nextInt(40);
    }

    public static boolean command(String command) {
        String[] args = command.trim().split("\\s+");
        if (args.length == 0 || !(args[0].equalsIgnoreCase("theseus") || args[0].equalsIgnoreCase("thesius"))) return false;
        var client = Minecraft.getInstance();
        String action = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "help";
        if (action.equals("clear")) { clear(); cooldown = 100 + RANDOM.nextInt(140); notice(client, "The render residue is gone."); return true; }
        if (action.equals("test") || action.equals("spawn") || action.equals("natural")) {
            int count = 1;
            try { if (args.length > 2) count = Integer.parseInt(args[2]); }
            catch (NumberFormatException invalid) { notice(client, "Use /theseus test [1-8], /theseus natural, or /theseus clear."); return true; }
            if (count < 1 || count > PeripheralWitnessPolicy.MAX_TEST_WITNESSES) { notice(client, "Choose between 1 and 8 watchers."); return true; }
            if (FlashbackAppearanceCompatibility.isPlayingReplay()) { notice(client, "Watchers remain hidden in replay playback."); return true; }
            clear();
            int made = spawn(client, !action.equals("natural"), count);
            notice(client, made == 0 ? "No clear viewpoint found. Try an open corridor or room." : "Render residue: " + made + ". /theseus clear removes it.");
            return true;
        }
        notice(client, "/theseus test [1-8] keeps watchers at screen edges; /theseus natural tests a fleeting sighting; /theseus clear removes both.");
        return true;
    }

    private static int spawn(Minecraft client, boolean persistent, int count) {
        if (client.level == null || client.player == null) return 0;
        var camera = client.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        var forward = camera.forwardVector(); var up = camera.upVector(); var left = camera.leftVector();
        double tan = Math.tan(Math.toRadians(Math.max(30, camera.getFov())) * .5);
        double aspect = client.getWindow().getWidth() / (double) Math.max(1, client.getWindow().getHeight());
        double maximum = Math.min(48, Math.max(12, client.options.getEffectiveRenderDistance() * 16 - 12));
        int made = 0;
        var work = new net.krodark.asterion.physics.TickWorkBudget(64, persistent ? 8_000_000L : 2_000_000L);
        for (int attempt = 0; attempt < 40 && made < count; attempt++) {
            if (!work.tryStep()) break;
            double side = (attempt % 2 == 0 ? -1 : 1) * (.89 + RANDOM.nextDouble() * .07);
            // Try eye-level screen edges first so low labyrinth ceilings do not reject every sighting.
            double vertical = attempt < 28 ? (RANDOM.nextDouble() - .5) * .02
                    : (attempt % 4 < 2 ? -.5 : .5) + (RANDOM.nextDouble() - .5) * .15;
            double distance = 10 + RANDOM.nextDouble() * (maximum - 10);
            Vec3 direction = new Vec3(forward.x() - left.x() * side * tan * aspect + up.x() * vertical * tan,
                    forward.y() - left.y() * side * tan * aspect + up.y() * vertical * tan,
                    forward.z() - left.z() * side * tan * aspect + up.z() * vertical * tan).normalize();
            Vec3 center = eye.add(direction.scale(distance));
            Vec3 feet = center.subtract(0, 1.6, 0);
            var obstruction = ray(client, eye, center);
            if (obstruction.getType() == HitResult.Type.BLOCK) {
                // Search along the hit wall for a real edge with an exposed eye and covered shoulder.
                Vec3 corner = corner(client, eye, obstruction, forward, left, tan, aspect, work);
                if (corner == null) continue;
                feet = corner; center = feet.add(0, 2.35, 0);
            } else {
                if (!persistent && attempt < 28) continue;
                if (!persistent) {
                    var floor = ray(client, feet.add(0, 1, 0), feet.subtract(0, 4, 0));
                    if (floor.getType() != HitResult.Type.BLOCK || floor.getDirection().getStepY() != 1) continue;
                    feet = floor.getLocation().add(0, .02, 0); center = feet.add(0, 2.35, 0);
                }
            }
            BlockPos block = BlockPos.containing(feet);
            if (!client.level.hasChunk(block.getX() >> 4, block.getZ() >> 4)
                    || feet.y < client.level.getMinY() + 1 || feet.y > client.level.getMaxY() - 4
                    || !client.level.noCollision(new AABB(feet.x - .16, feet.y, feet.z - .16, feet.x + .16, feet.y + 2.85, feet.z + .16))) continue;
            Vec3 delta = center.subtract(eye);
            double depth = delta.x * forward.x() + delta.y * forward.y() + delta.z * forward.z();
            double projectedSide = -(delta.x * left.x() + delta.y * left.y() + delta.z * left.z()) / Math.max(.001, depth * tan * aspect);
            if (depth <= 0 || Math.abs(projectedSide) < .85 || Math.abs(projectedSide) > 1.04
                    || delta.lengthSqr() < 8 * 8 || delta.lengthSqr() > 64 * 64) continue;
            var witness = new RenderRemnantEntity(RenderRemnantContent.TYPE, client.level);
            witness.setId(nextEntityId++);
            witness.setPos(feet);
            witness.persistentWitness = persistent;
            client.level.addEntity(witness);
            WITNESSES.add(witness); made++;
        }
        WitnessCaptureFence.observersPresent(!WITNESSES.isEmpty());
        return made;
    }

    public static List<RenderRemnantEntity> visibleWitnesses() { return WITNESSES; }

    private static BlockHitResult ray(Minecraft client, Vec3 from, Vec3 to) {
        return client.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
    }

    private static Vec3 corner(Minecraft client, Vec3 eye, BlockHitResult wall,
                               org.joml.Vector3fc forward, org.joml.Vector3fc left, double tan, double aspect,
                               net.krodark.asterion.physics.TickWorkBudget work) {
        if (wall.getDirection().getStepY() != 0 || wall.getLocation().distanceToSqr(eye) < 6 * 6) return null;
        Vec3 normal = new Vec3(wall.getDirection().getStepX(), 0, wall.getDirection().getStepZ());
        Vec3 along = new Vec3(-normal.z, 0, normal.x);
        for (int step = 0; step < 12; step++) {
            if (!work.tryStep()) return null;
            double slide = (step % 2 == 0 ? -1 : 1) * (.35 + (step / 2) * .55);
            Vec3 head = wall.getLocation().add(normal.scale(-.20)).add(along.scale(slide));
            BlockPos block = BlockPos.containing(head);
            if (!client.level.hasChunk(block.getX() >> 4, block.getZ() >> 4)) continue;
            Vec3 delta = head.subtract(eye);
            double depth = delta.x * forward.x() + delta.y * forward.y() + delta.z * forward.z();
            double x = -(delta.x * left.x() + delta.y * left.y() + delta.z * left.z()) / Math.max(.001, depth * tan * aspect);
            if (depth <= 0 || Math.abs(x) < .85 || Math.abs(x) > 1.04) continue;
            var ground = ray(client, head.add(0, .4, 0), head.subtract(0, 4, 0));
            if (ground.getType() != HitResult.Type.BLOCK || ground.getDirection().getStepY() != 1) continue;
            Vec3 feet = ground.getLocation().add(0, .02, 0);
            Vec3 eyes = feet.add(0, 2.37, 0);
            boolean leftEye = ray(client, eye, eyes.add(along.scale(-.12))).getType() == HitResult.Type.MISS;
            boolean rightEye = ray(client, eye, eyes.add(along.scale(.12))).getType() == HitResult.Type.MISS;
            boolean leftShoulder = ray(client, eye, feet.add(0, 1.85, 0).add(along.scale(-.42))).getType() == HitResult.Type.MISS;
            boolean rightShoulder = ray(client, eye, feet.add(0, 1.85, 0).add(along.scale(.42))).getType() == HitResult.Type.MISS;
            if ((leftEye || rightEye) && (!leftShoulder || !rightShoulder)) return feet;
        }
        return null;
    }

    public static void clear() { for (var witness : WITNESSES) witness.discard(); WITNESSES.clear(); WitnessCaptureFence.clearObservers(); }
    private static void notice(Minecraft client, String message) { if (client.player != null) client.gui.setOverlayMessage(Component.literal(message), false); }
}
