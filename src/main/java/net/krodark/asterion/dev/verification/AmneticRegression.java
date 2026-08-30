package net.krodark.asterion.dev.verification;

import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.model.internal.parse.BbmodelParser;
import java.nio.charset.StandardCharsets;
import org.joml.Matrix4f;

/** Checks the upgraded API used by Asterion without opening a client or requiring a GPU. */
public final class AmneticRegression {
    public static void main(String[] args) {
        check(InstanceLayout.TEXTURED_BILLBOARD.stride() == 48, "Billboard payload layout changed");
        InstanceBatch<Integer> batch = new InstanceBatch<>((value, out) -> out.putFloat(value), Float.BYTES);
        try {
            double cameraX = 29_000_000, cameraY = 80, cameraZ = -29_000_000;
            batch.beginFrame(new Matrix4f().perspective((float) Math.toRadians(60), 1f, .1f, 64f),
                    cameraX, cameraY, cameraZ);
            check(batch.visible(cameraX, cameraY, cameraZ - 5, .5f), "Visible center was culled");
            check(!batch.visible(cameraX, cameraY, cameraZ + 5, .5f), "Behind-camera sprite retained");
            check(!batch.visible(cameraX + 100, cameraY, cameraZ - 5, .5f), "Off-screen sprite retained");
            check(batch.visible(cameraX + 4, cameraY, cameraZ - 5, 2f), "Partially visible large sprite was culled");
            batch.addVisible(7, cameraX, cameraY, cameraZ - 5, .5f);
            batch.addVisible(9, cameraX, cameraY, cameraZ + 5, .5f);
            check(batch.count() == 1 && batch.flip().getFloat() == 7f, "Visibility changed packed payload");
            batch.reset();
            for (int i = 0; i < 2048; i++) batch.add(i);
            var packed = batch.flip();
            check(packed.remaining() == 2048 * Float.BYTES, "Buffer growth lost payload");
            for (int i = 0; i < 2048; i++) check(packed.getFloat() == i, "CPU submission ordering changed");
        } finally {
            batch.free();
        }
        // Upstream's jar references bb4j without shipping it; catch missing parser dependencies.
        var model = BbmodelParser.parse("""
                {"meta":{"format_version":"4.10","model_format":"free","box_uv":false},
                 "name":"asterion_dependency_check","resolution":{"width":16,"height":16},
                 "elements":[{"type":"cube","name":"probe","uuid":"a4dd7a23-2507-4c48-9000-000000000001",
                   "from":[0,0,0],"to":[16,16,16],"origin":[8,8,8],
                   "faces":{"north":{"uv":[0,0,16,16],"texture":0}}}],
                 "outliner":["a4dd7a23-2507-4c48-9000-000000000001"],
                 "textures":[],"animations":[]}
                """.getBytes(StandardCharsets.UTF_8), "dependency-check.bbmodel");
        check(!model.isEmpty() && model.parts().getFirst().indices.length >= 3,
                "Blockbench parser did not produce geometry");
        System.out.println("PASS: Amnetic billboard layout, frustum visibility, edge bounds, large coordinates, ordered buffer growth and bb4j parser");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
