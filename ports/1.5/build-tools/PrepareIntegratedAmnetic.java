import com.google.gson.*;
import org.objectweb.asm.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Builds Asterion's renderer runtime, removing the unused preset editor feature. */
public final class PrepareIntegratedAmnetic {
    private static final String UI = "com/meekdev/amnetic/client/ui/";
    private static final String PRESETS = "com/meekdev/amnetic/client/particle/editor/";
    private static int editorRegistration, editorProfiling;

    private static byte[] runtimeClass(byte[] original) {
        ClassWriter output = new ClassWriter(0);
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, output) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean itf) {
                        if (owner.equals(UI + "AmneticEditorBridge") && method.equals("init")
                                && desc.equals("()V") && opcode == Opcodes.INVOKESTATIC) {
                            editorRegistration++;
                            return;
                        }
                        if (owner.equals(UI + "AmneticEditor") && method.equals("isEnabled")
                                && desc.equals("()Z") && opcode == Opcodes.INVOKESTATIC) {
                            editorProfiling++;
                            super.visitInsn(Opcodes.ICONST_0);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, method, desc, itf);
                    }
                };
            }
        }, 0);
        byte[] result = output.toByteArray();
        String constants = new String(result, StandardCharsets.ISO_8859_1);
        if (constants.contains(UI) || constants.contains(PRESETS))
            throw new IllegalStateException("Renderer still references removed editor code");
        return result;
    }

    private static void write(Path root, String name, byte[] bytes) throws IOException {
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root)) throw new IOException("Unsafe archive entry: " + name);
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    public static void main(String[] args) throws Exception {
        Path renderer = Path.of(args[0]);
        Path asterion = Path.of(args[1]);
        Path root = Path.of(args[2]).toAbsolutePath().normalize();
        Files.createDirectories(root);
        JsonObject rendererMetadata;
        List<String> removed = new ArrayList<>();
        int runtimeClasses = 0;
        try (ZipFile input = new ZipFile(renderer.toFile())) {
            rendererMetadata = JsonParser.parseString(new String(input.getInputStream(
                    input.getEntry("fabric.mod.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (ZipEntry entry : Collections.list(input.entries())) {
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (name.startsWith(UI) || name.startsWith(PRESETS)
                        || name.equals("com/meekdev/amnetic/client/AmneticDataGenerator.class")) {
                    removed.add(name);
                    continue;
                }
                if (name.equals("fabric.mod.json") || name.equals("META-INF/MANIFEST.MF")
                        || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA)")) continue;
                byte[] bytes = input.getInputStream(entry).readAllBytes();
                if (name.endsWith(".class")) {
                    bytes = runtimeClass(bytes);
                    runtimeClasses++;
                }
                if (name.toLowerCase(Locale.ROOT).contains("license")
                        || name.equals("META-INF/NOTICE")) {
                    name = "META-INF/licenses/amnetic/" + name;
                }
                write(root, name, bytes);
            }
        }
        if (editorRegistration != 1 || editorProfiling != 1)
            throw new IllegalStateException("Unexpected renderer editor entrypoints: "
                    + editorRegistration + "/" + editorProfiling);
        if (!removed.contains(PRESETS + "EffectIO.class"))
            throw new IllegalStateException("Expected particle preset file API was not removed");

        try (ZipFile input = new ZipFile(asterion.toFile())) {
            JsonObject metadata = JsonParser.parseString(new String(input.getInputStream(
                    input.getEntry("fabric.mod.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            metadata.getAsJsonObject("depends").remove("amnetic");
            metadata.getAsJsonObject("depends").addProperty("mixinextras", ">=0.5.5");
            JsonArray clients = new JsonArray();
            clients.add("com.meekdev.amnetic.client.AmneticClient");
            metadata.getAsJsonObject("entrypoints").getAsJsonArray("client").forEach(clients::add);
            metadata.getAsJsonObject("entrypoints").add("client", clients);
            JsonArray mixins = metadata.getAsJsonArray("mixins");
            rendererMetadata.getAsJsonArray("mixins").forEach(mixins::add);
            JsonArray jars = metadata.has("jars") ? metadata.getAsJsonArray("jars") : new JsonArray();
            for (JsonElement nested : rendererMetadata.getAsJsonArray("jars")) {
                String file = nested.getAsJsonObject().get("file").getAsString();
                if (!Files.isRegularFile(root.resolve(file))) throw new IOException("Missing native dependency: " + file);
                jars.add(nested);
            }
            metadata.add("jars", jars);
            JsonObject custom = metadata.has("custom") ? metadata.getAsJsonObject("custom") : new JsonObject();
            custom.addProperty("asterion:integrated_amnetic", true);
            metadata.add("custom", custom);
            write(root, "fabric.mod.json", new GsonBuilder().setPrettyPrinting().create()
                    .toJson(metadata).getBytes(StandardCharsets.UTF_8));
            ZipEntry manifest = input.getEntry("META-INF/MANIFEST.MF");
            if (manifest != null) write(root, manifest.getName(), input.getInputStream(manifest).readAllBytes());
        }
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(renderer)));
        String report = "Amnetic renderer integrated into Asterion.\n"
                + "Renderer input: " + renderer.getFileName() + "\nSHA-256: " + sha256 + "\n"
                + "Original packages, renderer assets and license notices are retained.\n"
                + "The authoring editor and its disk preset save/load/delete API are removed.\n"
                + "Asterion owns renderer initialization and mixin registration.\n"
                + "Native dependencies remain registered as nested library jars.\n"
                + "Removed entries:\n" + String.join("\n", removed) + "\n";
        write(root, "META-INF/asterion/amnetic-integration.txt", report.getBytes(StandardCharsets.UTF_8));
        System.out.println("Integrated " + runtimeClasses + " renderer classes; removed " + removed.size()
                + " editor entries and both runtime editor hooks.");
    }
}
