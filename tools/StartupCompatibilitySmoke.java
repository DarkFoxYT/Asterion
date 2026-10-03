import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

public final class StartupCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        for(String token:new String[]{"FabricMC","0",""}) {
            String[] launch={"--username","DevPlayer","--accessToken",token};
            var prepared=net.krodark.asterion.dev.OfflineLaunchArguments.prepare(launch);
            if(!prepared[prepared.length-1].equals("--offlineDeveloperMode"))throw new AssertionError("Dummy login still contacts account services");
            if(net.krodark.asterion.dev.OfflineLaunchArguments.prepare(prepared)!=prepared)throw new AssertionError("Duplicate offline argument");
        }
        String[] authenticated={"--accessToken","signed-session-placeholder"};
        if(net.krodark.asterion.dev.OfflineLaunchArguments.prepare(authenticated)!=authenticated)throw new AssertionError("Authenticated launch forced offline");
        String[] inline={"--accessToken=signed-session-placeholder"};
        if(net.krodark.asterion.dev.OfflineLaunchArguments.prepare(inline)!=inline)throw new AssertionError("Inline authenticated token forced offline");
        try(var input=StartupCompatibilitySmoke.class.getClassLoader().getResourceAsStream("net/minecraft/client/main/Main.class")) {
            var node=new ClassNode();new ClassReader(input).accept(node,0);boolean flag=false;
            for(var method:node.methods)for(var instruction:method.instructions)
                if(instruction instanceof org.objectweb.asm.tree.LdcInsnNode literal && "offlineDeveloperMode".equals(literal.cst))flag=true;
            if(!flag)throw new AssertionError("Minecraft lacks the native offline developer flag");
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        try(var input=StartupCompatibilitySmoke.class.getClassLoader().getResourceAsStream(
                "com/meekdev/amnetic/mixin/WindowGlContextMixin.class")) {
            var node=new ClassNode();new ClassReader(input).accept(node,0);
            boolean wrapper=false;
            for(var method:node.methods) {
                var annotations=new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
                if(method.visibleAnnotations!=null)annotations.addAll(method.visibleAnnotations);
                if(method.invisibleAnnotations!=null)annotations.addAll(method.invisibleAnnotations);
                for(var annotation:annotations) {
                    if(annotation.desc.endsWith("/Redirect;"))throw new AssertionError("Conflicting GLFW redirect remains");
                    if(annotation.desc.endsWith("/WrapOperation;"))wrapper=true;
                }
            }
            if(!wrapper)throw new AssertionError("Missing composable GLFW hook");
        }
        for(String path:args)try(var zip=new ZipFile(path)) {
            var json=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(),StandardCharsets.UTF_8));
            PackMetadataSection.CLIENT_TYPE.codec().parse(JsonOps.INSTANCE,json.getAsJsonObject().get("pack")).getOrThrow();
        }
        System.out.println("PASS bundled composable window hook and Minecraft's resource-pack metadata decoder");
    }
}
