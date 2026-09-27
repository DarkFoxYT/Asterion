import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

/** Resolve the same namespaced shader includes used by Minecraft's resource loader. */
public final class ShaderIncludes {
    public static String resolve(String source) throws Exception {
        var matcher = Pattern.compile("#moj_import <([^:>]+):([^>]+)>").matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String resource = "assets/" + matcher.group(1) + "/shaders/include/" + matcher.group(2);
            Path local = Path.of("src/main/resources", resource);
            String include;
            if (Files.exists(local)) include = Files.readString(local);
            else try (var stream = ShaderIncludes.class.getClassLoader().getResourceAsStream(resource)) {
                if (stream == null) throw new AssertionError("Missing " + resource);
                include = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolve(include.replaceAll("(?m)^#version.*$", ""))));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
