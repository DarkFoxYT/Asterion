import javax.imageio.ImageIO;
import java.nio.file.Path;

/** Check the actual wall/particle screenshots, excluding HUD, chat and crosshair. */
public final class NativeRenderingImageSmoke {
    public static void main(String[] directories) throws Exception {
        for (String directory : directories) for (String dimension : new String[]{"labyrinth", "limbo"}) {
            Path root = Path.of(directory);
            int hidden = bright(root.resolve((dimension.equals("labyrinth") ? "0000" : "0002") + "_asterion-" + dimension + "-occluded.png"));
            int visible = bright(root.resolve((dimension.equals("labyrinth") ? "0001" : "0003") + "_asterion-" + dimension + "-visible.png"));
            if (visible < 100 || hidden > Math.max(30, visible / 20))
                throw new AssertionError(root + " " + dimension + ": visible=" + visible + ", hidden=" + hidden);
            System.out.println("PASS " + root + " " + dimension + ": bright pixels visible=" + visible + ", behind wall=" + hidden);
        }
    }
    private static int bright(Path path) throws Exception {
        var image = ImageIO.read(path.toFile());
        if (image == null) throw new AssertionError("Missing screenshot " + path);
        int count = 0;
        for (int y = image.getHeight()*35/100; y < image.getHeight()*65/100; y++)
            for (int x = image.getWidth()*35/100; x < image.getWidth()*65/100; x++) {
                if (Math.abs(x-image.getWidth()/2) < 12 && Math.abs(y-image.getHeight()/2) < 12) continue;
                int rgb = image.getRGB(x,y), r = rgb>>16&255, g=rgb>>8&255, b=rgb&255;
                if (r > 170 && b > 170 && g < 50) throw new AssertionError("Missing/magenta particle texture " + path);
                if ((r*0.2126 + g*0.7152 + b*0.0722) > 175) count++;
            }
        return count;
    }
}
