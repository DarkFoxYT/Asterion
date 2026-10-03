package net.krodark.asterion.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Decode world-list PNGs without the STB decoder implicated in the native crash. */
public final class SafeWorldIcon {
    private SafeWorldIcon() { }
    public static NativeImage read(InputStream input) throws IOException {
        byte[] bytes=input.readNBytes(1_048_577);
        if(bytes.length>1_048_576)throw new IOException("World icon exceeds 1 MiB");
        var readers=ImageIO.getImageReadersByFormatName("png");
        if(!readers.hasNext())throw new IOException("PNG reader unavailable");
        var reader=readers.next();
        try(var stream=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(stream,true,true);
            if(reader.getWidth(0)!=64 || reader.getHeight(0)!=64)throw new IOException("World icon must be 64x64");
            var decoded=reader.read(0);
            NativeImage image=new NativeImage(64,64,false);
            try {
                for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setPixel(x,y,decoded.getRGB(x,y));
                return image;
            } catch(RuntimeException | Error failure) {image.close();throw failure;}
        } finally {reader.dispose();}
    }
}
