import com.google.gson.JsonParser;
import net.krodark.asterion.client.cinematic.studio.CutsceneData;
import java.nio.file.Files;
import java.nio.file.Path;

/** Cross-language contract test: consumes an export produced by the actual plugin smoke harness. */
public class CutsceneStudioSmoke {
    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of("build/cutscene-tests/fixture.json"));
        CutsceneData scene = CutsceneData.parse(json);
        var expected = JsonParser.parseString(Files.readString(Path.of("build/cutscene-tests/camera-expected.json"))).getAsJsonArray();
        for (int i = 0; i < expected.size(); i++) {
            var e = expected.get(i).getAsJsonObject();
            var k = scene.cameraAt(i / 40.0);
            for (int axis = 0; axis < 3; axis++) {
                near(k.position()[axis], e.getAsJsonArray("position").get(axis).getAsDouble());
                near(k.target()[axis], e.getAsJsonArray("target").get(axis).getAsDouble());
            }
            near(k.fov(), e.get("fov").getAsDouble()); near(k.roll(), e.get("roll").getAsDouble());
        }
        near(scene.matrixAt(scene.actors().getFirst(), .375)[12], 1.375);
        near(scene.matrixAt(scene.actors().getFirst(), 2)[12], 3);
        near(scene.matrixAt(scene.actors().getFirst(), -1)[12], 1);
        rejects(json.replace("\"version\":1", "\"version\":2"));
        rejects(json.replace("\"fps\":20", "\"fps\":60"));
        rejects(json.replace("\"fov\":60", "\"fov\":200"));
        rejects(json.replace("\"texture\":0", "\"texture\":3"));
        // Final sample can be shorter than 1/fps. It must still reach its endpoint.
        var actor = new CutsceneData.Actor("short", java.util.List.of(), new float[][]{new float[16], new float[16], new float[16]});
        actor.samples()[1][12] = 1; actor.samples()[2][12] = 2;
        var shortScene = new CutsceneData(.075, 20, 1.7, false, null, scene.camera(), java.util.List.of(), java.util.List.of(actor));
        near(shortScene.matrixAt(actor, .0625)[12], 1.5);
        String hour = "{\"format\":\"asterion_cutscene\",\"version\":1,\"units\":\"blocks\",\"duration\":3600,\"fps\":20,\"aspect\":1.777,\"fullbright\":false,\"camera\":[{\"time\":0,\"position\":[0,0,0],\"target\":[0,0,1],\"fov\":70,\"roll\":0,\"ease\":\"linear\"},{\"time\":3600,\"position\":[0,0,0],\"target\":[0,0,1],\"fov\":70,\"roll\":0,\"ease\":\"linear\"}],\"textures\":[],\"objects\":[]}";
        near(CutsceneData.parse(hour).duration(), 3600);
        var anchored = JsonParser.parseString(json).getAsJsonObject();
        anchored.add("world_origin", JsonParser.parseString("[100,64,-200]"));
        near(CutsceneData.parse(anchored.toString()).worldOrigin()[2], -200);
        anchored.add("world_origin", JsonParser.parseString("[100,64]")); rejects(anchored.toString());
        System.out.println("PASS: 81 JS/Java camera parity samples, actor interpolation, shortened final interval, malformed export rejection.");
    }
    private static void near(double actual, double expected) { if (Math.abs(actual - expected) > 1e-6) throw new AssertionError(actual + " != " + expected); }
    private static void rejects(String json) { try { CutsceneData.parse(json); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Accepted invalid scene"); }
}
