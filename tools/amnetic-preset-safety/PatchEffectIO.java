import java.nio.file.*;
import java.util.Set;
import java.util.Map;
import org.objectweb.asm.*;

/** Restore the preset safety fix without depending on Minecraft's mapping names. */
public final class PatchEffectIO {
    public static void main(String[] args) throws Exception {
        byte[] input = Files.readAllBytes(Path.of(args[0]));
        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new ClassWriter(0);
        Set<String> operations = Set.of("createDirectories", "list", "readString", "writeString", "deleteIfExists");
        Map<String, String> descriptors = Map.of(
                "createDirectories", "(Ljava/nio/file/Path;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;",
                "list", "(Ljava/nio/file/Path;)Ljava/util/stream/Stream;",
                "readString", "(Ljava/nio/file/Path;)Ljava/lang/String;",
                "writeString", "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;",
                "deleteIfExists", "(Ljava/nio/file/Path;)Z");
        int[] patched = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM8, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitCode() {
                        super.visitCode();
                        if ((name.equals("load") || name.equals("delete")) && descriptor.startsWith("(Ljava/lang/String;)")) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/meekdev/amnetic/client/particle/editor/SafePresetIO", "validateName", "(Ljava/lang/String;)V", false);
                        }
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (owner.equals("java/nio/file/Files") && operations.contains(method)) {
                            if (!descriptor.equals(descriptors.get(method))) throw new IllegalStateException("Unexpected preset IO signature: " + method + descriptor);
                            owner = "com/meekdev/amnetic/client/particle/editor/SafePresetIO";
                            patched[0]++;
                        }
                        super.visitMethodInsn(opcode, owner, method, descriptor, itf);
                    }
                };
            }
        }, 0);
        if (patched[0] == 0 && !new String(input, java.nio.charset.StandardCharsets.ISO_8859_1).contains("SafePresetIO"))
            throw new IllegalStateException("No expected preset file operations found");
        Files.write(Path.of(args[1]), writer.toByteArray());
    }
}
