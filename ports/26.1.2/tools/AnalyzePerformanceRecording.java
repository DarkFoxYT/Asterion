import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;

/** Prints sampled hot stacks after startup; does not interpret CPU samples as FPS or GPU time. */
public final class AnalyzePerformanceRecording {
    public static void main(String[] args) throws Exception {
        Map<String,Integer> counts=new HashMap<>();
        java.time.Instant start=null;
        long total=0;
        double skip=args.length>1?Double.parseDouble(args[1]):30;
        try(var file=new RecordingFile(Path.of(args[0]))) {
            while(file.hasMoreEvents()) {
                var event=file.readEvent();
                if(start==null)start=event.getStartTime();
                if(java.time.Duration.between(start,event.getStartTime()).toMillis()<skip*1000)continue;
                if(!Set.of("jdk.ExecutionSample","jdk.NativeMethodSample").contains(event.getEventType().getName()))continue;
                var stack=event.getStackTrace();
                if(stack==null||stack.getFrames().isEmpty())continue;
                var thread=event.getThread("sampledThread");
                var frame=stack.getFrames().getFirst().getMethod();
                if(args.length>2) {
                    var match=stack.getFrames().stream().filter(f->f.getMethod().getType().getName().startsWith(args[2])).findFirst();
                    if(match.isEmpty())continue;
                    frame=match.get().getMethod();
                }
                String key=(thread==null?"unknown":thread.getJavaName())+" | "+frame.getType().getName()+"."+frame.getName();
                counts.merge(key,1,Integer::sum);total++;
            }
        }
        System.out.println("Sampled CPU/native stacks after "+skip+" seconds: "+total);
        counts.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(35)
                .forEach(e->System.out.println(e.getValue()+" "+e.getKey()));
    }
}
