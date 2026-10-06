package net.krodark.asterion.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/** Optional bridge to Essential's own cosmetic renderer; no bundled Essential dependency. */
final class EssentialRagdollCompatibility {
    private static final Bridge BRIDGE = load();
    private static boolean warned;
    private static boolean emoteInterrupted;

    private record Bridge(Class<?> playerExtension, Method cosmetics, Method equipped,
                          Object emoteSlot, Object emoteWheel, Method stopEmote,
                          Method renderer, Method renderState, Method render, Method shouldRender,
                          Constructor<?> matrices, Constructor<?> queue, Class<?> parts,
                          Method emissiveCape, Method savedCamera, Object config, Method thirdPersonEmotes,
                          Method skinMask, Class<?> cosmeticsInterface, Constructor<?> liveState,
                          Method blockedArmor, Method hidesHeldItems) { }
    private record Frame(Object renderer, Object cosmetics, PlayerModel model,
                         Map<ModelPart.Cube, Matrix4f> sources) { }

    static CameraType interruptEmote(Minecraft client) {
        if (BRIDGE == null || !BRIDGE.playerExtension.isInstance(client.player)) return null;
        try {
            CameraType previousCamera = null;
            Object state = BRIDGE.cosmetics.invoke(client.player);
            boolean active = state != null && ((Map<?, ?>) BRIDGE.equipped.invoke(state)).containsKey(BRIDGE.emoteSlot);
            if (active && !emoteInterrupted) {
                if (Boolean.TRUE.equals(BRIDGE.thirdPersonEmotes.invoke(BRIDGE.config)))
                    previousCamera = CameraType.values()[(int) BRIDGE.savedCamera.invoke(null)];
                BRIDGE.stopEmote.invoke(BRIDGE.emoteWheel);
                emoteInterrupted = true;
            }
            if (!active) emoteInterrupted = false;
            if (client.screen != null && client.screen.getClass().getName().equals("gg.essential.gui.emotes.EmoteWheel"))
                client.setScreen(null);
            return previousCamera;
        } catch (ReflectiveOperationException | RuntimeException error) { warn(error); return null; }
    }

    static void resetEmoteInterrupt() { emoteInterrupted = false; }

    static Identifier skinTexture(AbstractClientPlayer player, Identifier original) {
        if (BRIDGE == null || !BRIDGE.playerExtension.isInstance(player)) return original;
        try {
            if (!Boolean.TRUE.equals(BRIDGE.shouldRender.invoke(null, player))) return original;
            Identifier masked = (Identifier) BRIDGE.skinMask.invoke(player, original);
            return masked == null ? original : masked;
        } catch (ReflectiveOperationException | RuntimeException error) { warn(error); return original; }
    }

    @SuppressWarnings("unchecked")
    static Set<Integer> hiddenArmor(AbstractClientPlayer player) {
        if (BRIDGE == null || !BRIDGE.playerExtension.isInstance(player)) return Set.of();
        try { return Set.copyOf((Set<Integer>) BRIDGE.blockedArmor.invoke(BRIDGE.liveState.newInstance(player))); }
        catch (ReflectiveOperationException | RuntimeException error) { warn(error); return Set.of(); }
    }

    static boolean hidesHeldItems(AbstractClientPlayer player) {
        if (BRIDGE == null || !BRIDGE.playerExtension.isInstance(player)) return false;
        try {
            Object state = BRIDGE.cosmetics.invoke(player);
            return state != null && Boolean.TRUE.equals(BRIDGE.shouldRender.invoke(null, player))
                    && Boolean.TRUE.equals(BRIDGE.hidesHeldItems.invoke(state));
        } catch (ReflectiveOperationException | RuntimeException error) { warn(error); return false; }
    }

    static Identifier emissiveCape(AbstractClientPlayer player) {
        if (BRIDGE == null || !BRIDGE.playerExtension.isInstance(player)) return null;
        try {
            Object texture = BRIDGE.emissiveCape.invoke(player);
            return texture == null ? null : Identifier.parse(texture.toString());
        } catch (ReflectiveOperationException | RuntimeException error) { warn(error); return null; }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void submitCosmetics(Minecraft client, PoseStack poses, SubmitNodeCollector output,
                               List<RigidBodyPiece> bodies, float partial) {
        if (BRIDGE == null || client.level == null) return;
        Map<Integer, Frame> frames = new HashMap<>();
        for (RigidBodyPiece body : bodies) {
            if (!body.playerBody || body.region < 0 || body.region > 5
                    || !(client.level.getEntity(body.entityId) instanceof AbstractClientPlayer player)) continue;
            try {
                Frame frame;
                if (frames.containsKey(body.entityId)) frame = frames.get(body.entityId);
                else {
                    frame = frame(client, player, partial);
                    frames.put(body.entityId, frame);
                }
                if (frame == null) continue;
                ModelPart part = switch (body.region) {
                    case 0 -> frame.model.head; case 2 -> frame.model.rightArm;
                    case 3 -> frame.model.leftArm; case 4 -> frame.model.rightLeg;
                    case 5 -> frame.model.leftLeg; default -> frame.model.body;
                };
                if (part.isEmpty()) continue;
                var cube = part.getRandomCube(net.minecraft.util.RandomSource.create(0));
                Matrix4f source = frame.sources.get(cube);
                if (source == null) continue;
                // Convert the live cosmetic bone's model frame into this physical body's frame.
                // Essential can still animate its own accessories inside that frame.
                var center = DismembermentEngine.INSTANCE.renderCenter(body, partial);
                poses.pushPose();
                try {
                    float scale = (float) (body.halfExtents.y / Math.max(.001, (cube.maxY - cube.minY) / 32F));
                    poses.mulPose(RagdollCosmeticFrame.attachment(center,
                            DismembermentEngine.INSTANCE.renderOrientation(body, partial), scale, source));
                    int light = net.minecraft.client.renderer.LevelRenderer.getLightCoords(client.level, BlockPos.containing(center));
                    BRIDGE.render.invoke(frame.renderer, BRIDGE.matrices.newInstance(poses),
                            BRIDGE.queue.newInstance(output), null, frame.cosmetics, light, parts(body.region), false);
                } finally { poses.popPose(); }
            } catch (ReflectiveOperationException | RuntimeException error) { warn(error); }
        }
    }

    private static Frame frame(Minecraft client, AbstractClientPlayer player, float partial)
            throws ReflectiveOperationException {
        if (!Boolean.TRUE.equals(BRIDGE.shouldRender.invoke(null, player))) return null;
        var renderer = client.getEntityRenderDispatcher().getRenderer(player);
        Object cosmeticsRenderer = BRIDGE.renderer.invoke(renderer);
        var state = renderer.createRenderState(player, partial);
        if (!(state instanceof AvatarRenderState avatar)
                || !(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> living)
                || !(living.getModel() instanceof PlayerModel model)) return null;
        avatar.skin = RagdollPlayerSkins.resolve(player);
        Object cosmetics = BRIDGE.renderState.invoke(avatar);
        if (cosmeticsRenderer == null || cosmetics == null) return null;
        // Skin loads can briefly return a default skin while Essential's state is rebuilt.
        // Accessories which sample the player's skin must use the same loaded texture as the body.
        Object snapshot = cosmetics;
        Identifier skin = skinTexture(player, avatar.skin.body().texturePath());
        cosmetics = Proxy.newProxyInstance(BRIDGE.cosmeticsInterface.getClassLoader(),
                new Class<?>[]{BRIDGE.cosmeticsInterface}, (proxy, method, arguments) ->
                        method.getName().equals("skinTexture") ? skin : method.invoke(snapshot, arguments));
        model.setupAnim(avatar);
        Map<ModelPart.Cube, Matrix4f> sources = new IdentityHashMap<>();
        model.root().visit(new PoseStack(), (pose, path, index, cube) -> sources.put(cube,
                new Matrix4f(pose.pose()).translate((cube.minX + cube.maxX) / 32F,
                        (cube.minY + cube.maxY) / 32F, (cube.minZ + cube.maxZ) / 32F)));
        return new Frame(cosmeticsRenderer, cosmetics, model, sources);
    }

    private static Set<Object> parts(int region) throws ReflectiveOperationException {
        String name = switch (region) {
            case 0 -> "HEAD"; case 2 -> "RIGHT_ARM"; case 3 -> "LEFT_ARM";
            case 4 -> "RIGHT_LEG"; case 5 -> "LEFT_LEG"; default -> "BODY";
        };
        if (region != 1) return Set.of(BRIDGE.parts.getField(name).get(null));
        return Set.of(BRIDGE.parts.getField("BODY").get(null), BRIDGE.parts.getField("ROOT").get(null),
                BRIDGE.parts.getField("CAPE").get(null), BRIDGE.parts.getField("RIGHT_WING").get(null),
                BRIDGE.parts.getField("LEFT_WING").get(null), BRIDGE.parts.getField("RIGHT_SHOULDER_ENTITY").get(null),
                BRIDGE.parts.getField("LEFT_SHOULDER_ENTITY").get(null));
    }

    private static Bridge load() {
        try {
            Class<?> player = Class.forName("gg.essential.mixins.impl.client.entity.AbstractClientPlayerExt");
            Class<?> cosmetics = Class.forName("gg.essential.cosmetics.CosmeticsRenderState");
            Class<?> wheel = Class.forName("gg.essential.gui.emotes.EmoteWheel");
            Object companion = wheel.getField("Companion").get(null);
            Class<?> matrices = Class.forName("gg.essential.universal.UMatrixStack");
            Class<?> queue = Class.forName("gg.essential.model.backend.RenderBackend$CommandQueue");
            Class<?> renderer = Class.forName("gg.essential.cosmetics.EssentialModelRenderer");
            return new Bridge(player, player.getMethod("getCosmeticsState"),
                    Class.forName("gg.essential.cosmetics.CosmeticsState").getMethod("getCosmetics"),
                    Class.forName("gg.essential.mod.cosmetics.CosmeticSlot").getField("EMOTE").get(null),
                    companion, companion.getClass().getMethod("unequipCurrentEmote"),
                    Class.forName("gg.essential.mixins.impl.client.renderer.entity.PlayerEntityRendererExt")
                            .getMethod("essential$getEssentialModelRenderer"),
                    Class.forName("gg.essential.mixins.impl.client.model.PlayerEntityRenderStateExt")
                            .getMethod("essential$getCosmetics"),
                    renderer.getMethod("render", matrices, queue, AvatarRenderState.class, cosmetics, int.class, Set.class, boolean.class),
                    renderer.getMethod("shouldRender", AbstractClientPlayer.class),
                    matrices.getConstructor(PoseStack.class),
                    Class.forName("gg.essential.model.backend.minecraft.MinecraftRenderBackend$MinecraftCommandQueue")
                            .getConstructor(SubmitNodeCollector.class),
                    Class.forName("gg.essential.model.EnumPart"), player.getMethod("getEmissiveCapeTexture"),
                    wheel.getMethod("access$getSavedThirdPerson$cp"),
                    Class.forName("gg.essential.config.EssentialConfig").getField("INSTANCE").get(null),
                    Class.forName("gg.essential.config.EssentialConfig").getMethod("getThirdPersonEmotes"),
                    player.getMethod("applyEssentialCosmeticsMask", Identifier.class), cosmetics,
                    Class.forName("gg.essential.cosmetics.CosmeticsRenderState$Live").getConstructor(AbstractClientPlayer.class),
                    cosmetics.getMethod("blockedArmorSlots"),
                    Class.forName("gg.essential.cosmetics.CosmeticsState").getMethod("getHidesHeldItems"));
        } catch (ClassNotFoundException absent) { return null; }
        catch (ReflectiveOperationException | LinkageError incompatible) {
            warn(incompatible); return null;
        }
    }

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            Asterion.LOGGER.warn("Essential ragdoll integration could not use the installed cosmetic API", error);
        }
    }
}
