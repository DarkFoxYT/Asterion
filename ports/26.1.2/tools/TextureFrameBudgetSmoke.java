import net.krodark.asterion.client.render.TextureFrameBudget;

public final class TextureFrameBudgetSmoke {
    public static void main(String[] args) throws Exception {
        long mb=1024L*1024;
        var accepted=new java.util.concurrent.atomic.AtomicInteger();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(8)) {
            for(int i=0;i<128;i++)pool.submit(()->{if(TextureFrameBudget.reserve(mb))accepted.incrementAndGet();});
        }
        if(accepted.get()!=32||TextureFrameBudget.usedBytes()!=32*mb||TextureFrameBudget.reserve(1))
            throw new AssertionError("Aggregate native memory cap exceeded");
        TextureFrameBudget.release(32*mb);
        if(TextureFrameBudget.usedBytes()!=0||!TextureFrameBudget.reserve(mb))
            throw new AssertionError("Memory budget does not recover after idle/reload cleanup");
        TextureFrameBudget.release(mb);
        System.out.println("PASS concurrent texture discovery stays at 32 MiB total and cleanup restores capacity.");
    }
}
