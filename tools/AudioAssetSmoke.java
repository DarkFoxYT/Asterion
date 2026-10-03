import java.nio.*;
import java.nio.file.*;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;
public final class AudioAssetSmoke {
    public static void main(String[] args)throws Exception {
        int count=0;
        try(var files=Files.walk(Path.of("src/main/resources/assets/asterion/sounds/sfx"))) {
            for(Path file:files.filter(p->p.toString().endsWith(".ogg")).toList()) {
                byte[] bytes=Files.readAllBytes(file);ByteBuffer encoded=MemoryUtil.memAlloc(bytes.length);encoded.put(bytes).flip();
                IntBuffer channels=MemoryUtil.memAllocInt(1),rate=MemoryUtil.memAllocInt(1);
                ShortBuffer pcm=STBVorbis.stb_vorbis_decode_memory(encoded,channels,rate);
                if(pcm==null)throw new AssertionError("Undecodable SFX "+file);
                double squares=0;int peak=0;for(int i=0;i<pcm.limit();i++){int sample=pcm.get(i);peak=Math.max(peak,Math.abs(sample));squares+=(double)sample*sample;}
                double rms=Math.sqrt(squares/pcm.limit())/32768;
                if(peak==0)throw new AssertionError("Silent SFX "+file);
                System.out.printf("%s: channels=%d peak=%d RMS=%.4f duration=%.2fs%n",file.getFileName(),channels.get(0),peak,rms,pcm.limit()/(double)channels.get(0)/rate.get(0));
                org.lwjgl.system.libc.LibCStdlib.free(pcm);MemoryUtil.memFree(encoded);MemoryUtil.memFree(channels);MemoryUtil.memFree(rate);count++;
            }
        }
        System.out.println("PASS "+count+" decoded audible SFX assets");
    }
}
