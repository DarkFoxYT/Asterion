import java.nio.file.*;
import java.util.*;
import me.lucko.spark.proto.SparkSamplerProtos.*;
public class ReadSpark {
 public static void main(String[] args)throws Exception {
  var bytes=Files.readAllBytes(Path.of(args[0]));
  System.out.println("bytes="+bytes.length+" header="+java.util.HexFormat.of().formatHex(Arrays.copyOf(bytes,10)));
  var data=SamplerData.parseFrom(bytes);
  for(var t:data.getThreadsList()) {
   if(!t.getName().equals("Render thread")&&!t.getName().equals("Server thread"))continue;
   System.out.println("THREAD "+t.getName()+" times="+t.getTimesList());
   t.getChildrenList().stream().filter(n->n.getClassName().contains("asterion")||n.getClassName().contains("amnetic"))
    .sorted(Comparator.comparingDouble((StackTraceNode n)->n.getTimesList().stream().mapToDouble(Double::doubleValue).sum()).reversed())
    .limit(30).forEach(n->System.out.println(n.getClassName()+"."+n.getMethodName()+":"+n.getLineNumber()+" ms="+n.getTimesList()));
  }
 }
}
