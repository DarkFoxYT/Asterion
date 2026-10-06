package net.krodark.asterion.client.ragdoll;

import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipFile;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public final class RagdollCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        PlayerSkin fallback = skin("minecraft:entity/player/wide/steve", PlayerModelType.WIDE);
        PlayerSkin loaded = skin("minecraft:skins/alice", PlayerModelType.SLIM);
        PlayerSkin changed = skin("essential:skins/alice-new", PlayerModelType.WIDE);
        require(RagdollPlayerSkins.remember(alice, fallback).equals(fallback), "Never-loaded player loses fallback");
        require(RagdollPlayerSkins.remember(alice, loaded).equals(loaded), "Async skin never replaces fallback");
        var retained = RagdollPlayerSkins.remember(alice, fallback);
        require(retained.body().equals(loaded.body()) && retained.model() == loaded.model(), "Reload reverts body/model to Steve");
        require(RagdollPlayerSkins.remember(bob, fallback).equals(fallback), "Another player's skin leaked");
        require(RagdollPlayerSkins.remember(alice, changed).equals(changed), "Real skin changes are frozen");
        RagdollPlayerSkins.clear();
        require(RagdollPlayerSkins.remember(alice, fallback).equals(fallback), "Previous server's skin survived disconnect");
        for (int i = 0; i < 120; i++) {
            var source = new Matrix4f().translation(i * .01F, -.4F, .7F)
                    .rotateXYZ(i * .01F, i * .03F, i * -.02F).scale(1.1F);
            var original = new Matrix4f(source);
            var center = new Vec3(10 + i, 64, -20);
            var rotation = new Quaternionf().rotationXYZ(i * .03F, -.8F, .7F);
            float size = i % 2 == 0 ? .8F : 1.3F;
            // Essential adds 1.5 before submitting cosmetic bones in the original model space.
            var mapped = RagdollCosmeticFrame.attachment(center, rotation, size, source)
                    .translate(0, 1.5F, 0).mul(source);
            var expected = new Matrix4f().translation((float)center.x, (float)center.y, (float)center.z)
                    .rotate(rotation).scale(size);
            for (var point : new Vector3f[]{new Vector3f(), new Vector3f(.2F, -.3F, .4F), new Vector3f(-.1F, .8F, 0)})
                require(mapped.transformPosition(new Vector3f(point)).distance(expected.transformPosition(new Vector3f(point))) < .0001,
                        "Cosmetic drifts from physical bone after rotation/scale");
            require(source.equals(original), "Attachment changed shared model transform");
        }
        // Check optional absence without loading a client or Essential's GUI singletons.
        require(EssentialRagdollCompatibility.emissiveCape(null) == null, "Absent Essential should be a no-op");
        if (args.length > 0) {
            try (var jar = new ZipFile(args[0]); var universal = new ZipFile(args[1])) {
                method(jar, "gg/essential/mixins/impl/client/entity/AbstractClientPlayerExt", "getCosmeticsState", "()Lgg/essential/cosmetics/CosmeticsState;");
                method(jar, "gg/essential/mixins/impl/client/entity/AbstractClientPlayerExt", "getEmissiveCapeTexture", "()Lgg/essential/util/UIdentifier;");
                method(jar, "gg/essential/mixins/impl/client/entity/AbstractClientPlayerExt", "applyEssentialCosmeticsMask", "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/resources/Identifier;");
                method(jar, "gg/essential/cosmetics/CosmeticsState", "getCosmetics", "()Ljava/util/Map;");
                method(jar, "gg/essential/cosmetics/CosmeticsState", "getHidesHeldItems", "()Z");
                method(jar, "gg/essential/cosmetics/CosmeticsRenderState$Live", "<init>", "(Lnet/minecraft/client/player/AbstractClientPlayer;)V");
                method(jar, "gg/essential/cosmetics/CosmeticsRenderState", "blockedArmorSlots", "()Ljava/util/Set;");
                method(jar, "gg/essential/gui/emotes/EmoteWheel$Companion", "unequipCurrentEmote", "()V");
                method(jar, "gg/essential/gui/emotes/EmoteWheel$Companion", "canEmote", "(Lnet/minecraft/client/player/AbstractClientPlayer;)Z");
                method(jar, "gg/essential/gui/emotes/EmoteWheel", "access$getSavedThirdPerson$cp", "()I");
                method(jar, "gg/essential/config/EssentialConfig", "getThirdPersonEmotes", "()Z");
                method(jar, "gg/essential/mixins/impl/client/renderer/entity/PlayerEntityRendererExt", "essential$getEssentialModelRenderer", "()Lgg/essential/cosmetics/EssentialModelRenderer;");
                method(jar, "gg/essential/mixins/impl/client/model/PlayerEntityRenderStateExt", "essential$getCosmetics", "()Lgg/essential/cosmetics/CosmeticsRenderState$Snapshot;");
                method(jar, "gg/essential/cosmetics/EssentialModelRenderer", "render", "(Lgg/essential/universal/UMatrixStack;Lgg/essential/model/backend/RenderBackend$CommandQueue;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lgg/essential/cosmetics/CosmeticsRenderState;ILjava/util/Set;Z)V");
                method(jar, "gg/essential/cosmetics/EssentialModelRenderer", "shouldRender", "(Lnet/minecraft/client/player/AbstractClientPlayer;)Z");
                method(jar, "gg/essential/model/backend/minecraft/MinecraftRenderBackend$MinecraftCommandQueue", "<init>", "(Lnet/minecraft/client/renderer/SubmitNodeCollector;)V");
                method(universal, "gg/essential/universal/UMatrixStack", "<init>", "(Lcom/mojang/blaze3d/vertex/PoseStack;)V");
                for (String name : new String[]{"ROOT", "HEAD", "BODY", "RIGHT_ARM", "LEFT_ARM", "LEFT_LEG", "RIGHT_LEG", "CAPE", "RIGHT_WING", "LEFT_WING", "RIGHT_SHOULDER_ENTITY", "LEFT_SHOULDER_ENTITY"})
                    require(read(jar, "gg/essential/model/EnumPart").fields.stream().anyMatch(f -> f.name.equals(name)), "Missing cosmetic bone " + name);
                require(read(jar, "gg/essential/mod/cosmetics/CosmeticSlot").fields.stream().anyMatch(f -> f.name.equals("EMOTE")), "Missing EMOTE slot");
            }
        }
        System.out.println("Ragdoll compatibility: skin reload/isolation/reconnect, 120 attachment frames, optional absence and installed Essential API passed.");
    }

    private static PlayerSkin skin(String path, PlayerModelType model) {
        return new PlayerSkin(new ClientAsset.ResourceTexture(Identifier.parse(path)), null, null, model, true);
    }
    private static ClassNode read(ZipFile jar, String type) throws Exception {
        var entry = jar.getEntry(type + ".class");
        require(entry != null, "Missing Essential class " + type);
        try (var input = jar.getInputStream(entry)) {
            var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static void method(ZipFile jar, String type, String name, String desc) throws Exception {
        require(read(jar, type).methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc)), "Missing API " + type + "." + name + desc);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
