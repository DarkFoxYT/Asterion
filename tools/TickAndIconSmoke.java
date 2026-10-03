import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.krodark.asterion.client.render.SafeWorldIcon;
import net.krodark.asterion.physics.TickWorkBudget;

public final class TickAndIconSmoke {
    public static void main(String[] args) throws Exception {
        long[] clock={0};
        var budget=new TickWorkBudget(512,2_000_000L,()->clock[0]);
        for(int i=0;i<512;i++)if(!budget.tryStep())throw new AssertionError("Budget stops too early");
        if(budget.tryStep())throw new AssertionError("Count budget exceeded");
        budget=new TickWorkBudget(512,2_000_000L,()->clock[0]);
        clock[0]=2_000_000L;
        if(budget.tryStep())throw new AssertionError("Time budget exceeded");
        var source=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        source.setRGB(10,20,0x8044AADD);
        var bytes=new ByteArrayOutputStream();ImageIO.write(source,"png",bytes);
        try(var icon=SafeWorldIcon.read(new ByteArrayInputStream(bytes.toByteArray()))) {
            if(icon.getPixel(10,20)!=0x8044AADD)throw new AssertionError("Icon colour/alpha changed");
        }
        try {SafeWorldIcon.read(new ByteArrayInputStream(new byte[]{1,2,3}));throw new AssertionError("Malformed icon accepted");}
        catch(IOException expected) { }
        bytes.reset();ImageIO.write(new java.awt.image.BufferedImage(65,64,2),"png",bytes);
        try {SafeWorldIcon.read(new ByteArrayInputStream(bytes.toByteArray()));throw new AssertionError("Oversized dimensions accepted");}
        catch(IOException expected) { }
        int count=0;
        if(Files.isDirectory(Path.of("run/saves")))try(var paths=Files.walk(Path.of("run/saves"))) {
            for(Path path:paths.filter(p->p.getFileName().toString().equals("icon.png")).toList()) {
                try(var input=Files.newInputStream(path);var icon=SafeWorldIcon.read(input)) {
                    if(icon.getWidth()!=64 || icon.getHeight()!=64)throw new AssertionError("Bad icon dimensions");count++;
                }
            }
        }
        // Verify the redirect against the actual client bytecode, not an assumed method name.
        try(var input=TickAndIconSmoke.class.getClassLoader().getResourceAsStream("net/minecraft/client/gui/screens/worldselection/WorldSelectionList$WorldListEntry.class")) {
            var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(input).accept(node,0);
            int calls=0;
            for(var method:node.methods)if(method.name.equals("loadIcon"))for(var instruction:method.instructions)
                if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call && call.owner.equals("com/mojang/blaze3d/platform/NativeImage")
                        && call.name.equals("read") && call.desc.equals("(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"))calls++;
            if(calls!=1)throw new AssertionError("World icon redirect target changed");
        }
        System.out.println("PASS 512-operation/2ms cooperative budget, Java PNG colour/alpha, invalid input/dimensions, "+count+" saved-world icons, and actual icon decoder mixin target");
    }
}
