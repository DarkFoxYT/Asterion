import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Offline JFR summary; optional UTC start time excludes loading from steady-state results. */
public final class ReadPerformanceJfr {
    public static void main(String[] args) throws Exception {
        Instant start = args.length > 1 ? Instant.parse(args[1]) : Instant.MIN;
        Map<String, Long> cpu = new HashMap<>(), allocations = new HashMap<>();
        long samples = 0, weight = 0, minHeap = Long.MAX_VALUE, maxHeap = 0;
        try (var recording = new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                if (event.getStartTime().isBefore(start)) continue;
                String type = event.getEventType().getName();
                if (type.equals("jdk.GCHeapSummary") && event.getString("when").equals("After GC")) {
                    long heap = event.getLong("heapUsed");
                    minHeap = Math.min(minHeap, heap); maxHeap = Math.max(maxHeap, heap);
                }
                if (!type.equals("jdk.ExecutionSample") && !type.equals("jdk.ObjectAllocationSample")) continue;
                var trace = event.getStackTrace(); if (trace == null) continue;
                boolean allocation = type.equals("jdk.ObjectAllocationSample");
                long amount = allocation ? event.getLong("weight") : 1;
                if (allocation) weight += amount; else samples++;
                Set<String> seen = new HashSet<>();
                for (var frame : trace.getFrames()) {
                    var method = frame.getMethod();
                    String key = method.getType().getName() + "." + method.getName();
                    if ((key.contains("asterion") || key.contains("amnetic")) && seen.add(key))
                        (allocation ? allocations : cpu).merge(key, amount, Long::sum);
                }
            }
        }
        System.out.printf("execution_samples=%d allocation_weight_bytes=%d after_gc_heap_min=%d after_gc_heap_max=%d%n",
                samples, weight, minHeap == Long.MAX_VALUE ? 0 : minHeap, maxHeap);
        for (var map : List.of(cpu, allocations)) {
            System.out.println(map == cpu ? "Inclusive execution samples:" : "Inclusive sampled allocations (not retained bytes):");
            map.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(30).forEach(System.out::println);
        }
    }
}
