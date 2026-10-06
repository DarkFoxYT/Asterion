import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Checks every required mixin against the exact Minecraft/dependency bytecode. */
public final class NativeMixinSmoke {
    static ClassNode read(String name) throws Exception {
        try(var in=NativeMixinSmoke.class.getClassLoader().getResourceAsStream(name+".class")) {
            if(in==null) return null;
            var node=new ClassNode(); new ClassReader(in).accept(node,0); return node;
        }
    }
    static List<AnnotationNode> anns(List<AnnotationNode> a,List<AnnotationNode> b) {
        var list=new ArrayList<AnnotationNode>(); if(a!=null)list.addAll(a); if(b!=null)list.addAll(b);return list;
    }
    static Object value(AnnotationNode a,String key) {
        if(a.values!=null)for(int i=0;i<a.values.size();i+=2)if(key.equals(a.values.get(i)))return a.values.get(i+1);
        return null;
    }
    public static void main(String[] args) throws Exception {
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var mixins=new com.google.gson.JsonArray();mixins.addAll(config.getAsJsonArray("mixins"));mixins.addAll(config.getAsJsonArray("client"));
        int failures=0,checks=0;
        for(var item:mixins) {
            String name=item.getAsString();var mixin=read("net/krodark/asterion/mixin/"+name);
            if(mixin==null){System.out.println("FAIL missing mixin "+name);failures++;continue;}
            var targets=new ArrayList<String>();
            for(var a:anns(mixin.visibleAnnotations,mixin.invisibleAnnotations))if(a.desc.endsWith("/Mixin;")) {
                if(value(a,"value") instanceof List<?> values)for(var v:values)targets.add(((Type)v).getInternalName());
                if(value(a,"targets") instanceof List<?> values)for(var v:values)targets.add(v.toString().replace('.','/'));
            }
            for(String targetName:targets) {
                var target=read(targetName);
                if(target==null){if(name.startsWith("Sodium"))continue;System.out.println("FAIL "+name+" missing target "+targetName);failures++;continue;}
                var fields=new ArrayList<FieldNode>();var methods=new ArrayList<MethodNode>();
                for(var node=target;node!=null;node=node.superName==null?null:read(node.superName)){fields.addAll(node.fields);methods.addAll(node.methods);}
                for(var f:mixin.fields)for(var a:anns(f.visibleAnnotations,f.invisibleAnnotations))if(a.desc.endsWith("/Shadow;")) {
                    checks++;if(fields.stream().noneMatch(t->t.name.equals(f.name)&&t.desc.equals(f.desc))){System.out.println("FAIL "+name+" shadow "+f.name+f.desc);failures++;}
                }
                for(var m:mixin.methods)for(var a:anns(m.visibleAnnotations,m.invisibleAnnotations)) {
                    if(a.desc.endsWith("/Accessor;")) {
                        var field=value(a,"value");if(field==null)continue;
                        checks++;if(fields.stream().noneMatch(t->t.name.equals(field))){System.out.println("FAIL "+name+" accessor "+field);failures++;}
                    }
                    if(a.desc.endsWith("/Invoker;")||a.desc.endsWith("/Shadow;")) {
                        String selected=value(a,"value") instanceof String s?s:m.name;
                        checks++;if(methods.stream().noneMatch(t->t.name.equals(selected)&&t.desc.equals(m.desc))){System.out.println("FAIL "+name+" method "+selected+m.desc);failures++;}
                    }
                    if(!(value(a,"method") instanceof List<?> selectors))continue;
                    boolean optional=Integer.valueOf(0).equals(value(a,"require"));
                    var matched=new ArrayList<MethodNode>();
                    for(var selector:selectors) {
                        String text=selector.toString();var found=methods.stream().filter(t->text.equals(t.name)||text.equals(t.name+t.desc)).toList();matched.addAll(found);checks++;
                        
                    }
                    if(matched.isEmpty()&&!optional){System.out.println("FAIL "+name+" injections "+selectors);failures++;}
                    if(a.desc.endsWith("/Inject;")) {
                        var arguments = Type.getArgumentTypes(m.desc);
                        int callback = -1;
                        for(int i=0;i<arguments.length;i++)if(arguments[i].getClassName().startsWith("org.spongepowered.asm.mixin.injection.callback.CallbackInfo")){callback=i;break;}
                        if(callback>=0)for(var selected:matched) {
                            boolean returnsValue=!Type.getReturnType(selected.desc).equals(Type.VOID_TYPE);
                            boolean returnable=arguments[callback].getClassName().endsWith("CallbackInfoReturnable");
                            if(returnsValue!=returnable){System.out.println("FAIL "+name+" callback return type "+m.name+" expected "+selected.name+selected.desc);failures++;}
                        }
                        if(callback>0)for(var selected:matched) {
                            var expected=Type.getArgumentTypes(selected.desc);
                            boolean compatible=callback==expected.length;
                            if(compatible)for(int i=0;i<callback;i++)if(!arguments[i].equals(expected[i])&&!arguments[i].getClassName().equals("java.lang.Object"))compatible=false;
                            if(!compatible){System.out.println("FAIL "+name+" callback "+m.name+m.desc+" expected "+selected.name+selected.desc);failures++;}
                        }
                    }
                    Object at=value(a,"at");List<?> locations=at instanceof List<?> l?l:at==null?List.of():List.of(at);
                    for(var location:locations)if(location instanceof AnnotationNode annotation&&value(annotation,"target") instanceof String instruction&&instruction.startsWith("L")) {
                        boolean found=false;for(var t:matched)for(var insn:t.instructions) {
                            if(insn instanceof MethodInsnNode call&&instruction.equals("L"+call.owner+";"+call.name+call.desc))found=true;
                            if(insn instanceof FieldInsnNode field&&instruction.equals("L"+field.owner+";"+field.name+":"+field.desc))found=true;
                        }
                        checks++;if(!found&&!optional){System.out.println("FAIL "+name+" instruction "+instruction);failures++;}
                    }
                }
            }
        }
        System.out.println("Checked "+checks+" mixin bindings; failures="+failures);
        if(failures>0)throw new AssertionError("Native mixin compatibility failed");
    }
}
