import java.nio.file.Path;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Verify the zero-threshold fallback switch against the actual bundled renderer binaries. */
public final class BloomFallbackSmoke {
    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Supply bundled Amnetic jars");
        for (String file : args) {
            try (var jar = new JarFile(file)) {
                var entry = jar.getJarEntry("com/meekdev/amnetic/client/bloom/internal/BloomRenderer.class");
                if (entry == null) throw new AssertionError("Missing renderer: " + file);
                var renderer = new ClassNode();
                try (var stream = jar.getInputStream(entry)) {
                    new ClassReader(stream).accept(renderer, 0);
                }
                int guarded = 0, sourceThresholds = 0, emissivePaths = 0;
                for (var method : renderer.methods) for (var instruction : method.instructions) {
                    if (!(instruction instanceof MethodInsnNode call)) continue;
                    if (method.name.equals("render") && call.owner.endsWith("GBufferTargets")
                            && call.name.equals("hasEmissive")) emissivePaths++;
                    if (!call.owner.equals("com/meekdev/amnetic/client/bloom/BloomSettings")
                            || !call.name.equals("threshold") || !call.desc.equals("()F")) continue;
                    if (method.name.equals("render")) {
                        var zero = next(call);
                        var compare = zero == null ? null : next(zero);
                        var jump = compare == null ? null : next(compare);
                        if (zero == null || zero.getOpcode() != Opcodes.FCONST_0
                                || compare == null || compare.getOpcode() != Opcodes.FCMPL
                                || jump == null || jump.getOpcode() != Opcodes.IFLE)
                            throw new AssertionError("Zero no longer disables scene fallback: " + file);
                        guarded++;
                    } else if (method.name.equals("renderSources")) sourceThresholds++;
                }
                if (guarded != 1 || sourceThresholds != 1 || emissivePaths != 1)
                    throw new AssertionError("Bloom integration changed: " + file);
                System.out.println("PASS zero disables scene capture, separate emissive path retained: "
                        + Path.of(file).getFileName());
            }
        }
    }
}
