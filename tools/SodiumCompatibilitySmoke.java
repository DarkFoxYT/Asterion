import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.jar.JarFile;

public final class SodiumCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        try (var jar = new JarFile(args[0])) {
            String root = "net/caffeinemc/mods/sodium/client/";
            check(jar, root+"render/SodiumWorldRenderer", "instanceNullable", "()L"+root+"render/SodiumWorldRenderer;");
            check(jar, root+"render/SodiumWorldRenderer", "isBoxVisible", "(DDDDDD)Z");
            check(jar, root+"render/SodiumWorldRenderer", "setupTerrain", null);
            check(jar, root+"render/SodiumWorldRenderer", "setLevel", null);
            check(jar, root+"render/SodiumWorldRenderer", "reload", "()V");
            check(jar, root+"render/chunk/compile/pipeline/DefaultFluidRenderer", "isFullBlockFluidVisible",
                "(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Z");
        }
        System.out.println("PASS installed Sodium visibility, lifecycle and water-surface hook signatures");
    }
    private static void check(JarFile jar, String type, String method, String descriptor) throws Exception {
        var entry = jar.getJarEntry(type+".class");
        if (entry == null) throw new AssertionError("Missing Sodium class " + type);
        try (var stream = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            if (node.methods.stream().noneMatch(m -> m.name.equals(method) && (descriptor == null || m.desc.equals(descriptor))))
                throw new AssertionError("Missing Sodium method " + method);
        }
    }
}
