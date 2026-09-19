import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Fail before launch if a private Amnetic integration points at an API removed by an update. */
public final class AmneticCompatibilitySmoke {
    private static ClassNode read(String name) throws Exception {
        try(var stream=AmneticCompatibilitySmoke.class.getClassLoader().getResourceAsStream(name+".class")) {
            if(stream==null)throw new AssertionError("Missing class "+name);
            var node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
        }
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> a,List<AnnotationNode> b) {
        var out=new ArrayList<AnnotationNode>();if(a!=null)out.addAll(a);if(b!=null)out.addAll(b);return out;
    }
    private static Object value(AnnotationNode a,String key) {
        if(a.values!=null)for(int i=0;i<a.values.size();i+=2)if(key.equals(a.values.get(i)))return a.values.get(i+1);
        return null;
    }
    public static void main(String[] args) throws Exception {
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/asterion.mixins.json"))).getAsJsonObject();
        int checks=0;
        for(var item:config.getAsJsonArray("client")) {
            String name=item.getAsString();if(!name.startsWith("Amnetic"))continue;
            var mixin=read("net/krodark/asterion/mixin/"+name);ClassNode target=null;
            for(var a:annotations(mixin.visibleAnnotations,mixin.invisibleAnnotations))if(a.desc.endsWith("/Mixin;")) {
                var values=(List<?>)value(a,"value");var targets=(List<?>)value(a,"targets");
                target=read(values!=null?((Type)values.getFirst()).getInternalName():targets.getFirst().toString().replace('.','/'));
            }
            if(target==null)throw new AssertionError("No target for "+name);
            for(var f:mixin.fields)for(var a:annotations(f.visibleAnnotations,f.invisibleAnnotations))if(a.desc.endsWith("/Shadow;")) {
                if(target.fields.stream().noneMatch(t->t.name.equals(f.name)&&t.desc.equals(f.desc)))throw new AssertionError(name+" missing field "+f.name);
                checks++;
            }
            for(var m:mixin.methods)for(var a:annotations(m.visibleAnnotations,m.invisibleAnnotations)) {
                if(a.desc.endsWith("/Shadow;")||a.desc.endsWith("/Invoker;")) {
                    String method=value(a,"value") instanceof String v?v:m.name;
                    if(target.methods.stream().noneMatch(t->t.name.equals(method)&&t.desc.equals(m.desc)))throw new AssertionError(name+" missing method "+method);
                    checks++;
                }
                Object selected=value(a,"method");if(!(selected instanceof List<?> selectors))continue;
                List<MethodNode> matched=new ArrayList<>();
                for(var selector:selectors) {
                    String text=selector.toString();boolean found=false;
                    for(var t:target.methods)if(text.equals(t.name)||text.equals(t.name+t.desc)){matched.add(t);found=true;}
                    if(!found)throw new AssertionError(name+" missing injection target "+text);
                    checks++;
                }
                Object at=value(a,"at");List<?> locations=at instanceof List<?> l?l:at==null?List.of():List.of(at);
                for(var location:locations)if(location instanceof AnnotationNode annotation && value(annotation,"target") instanceof String instruction && instruction.startsWith("L")) {
                    boolean found=false;
                    for(var t:matched)for(var insn:t.instructions)if(insn instanceof MethodInsnNode call && instruction.equals("L"+call.owner+";"+call.name+call.desc))found=true;
                    if(!found)throw new AssertionError(name+" missing wrapped call "+instruction);
                    checks++;
                }
            }
        }
        System.out.println("PASS "+checks+" Amnetic private-field, method, and wrapped-call compatibility checks.");
    }
}
