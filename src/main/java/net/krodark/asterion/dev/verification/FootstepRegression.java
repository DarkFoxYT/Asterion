package net.krodark.asterion.dev.verification;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.krodark.asterion.entity.MinotaurAnimationTiming;

public final class FootstepRegression {
    public static void main(String[] args) throws Exception {
        var clips = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/asterion/geckolib/animations/entity/minotaur.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        for (String clip : new String[]{"walk", "run", "run charge attack"}) {
            boolean walk = clip.equals("walk");
            double length = walk ? MinotaurAnimationTiming.WALK_LENGTH : MinotaurAnimationTiming.RUN_LENGTH;
            if (clips.getAsJsonObject(clip).get("animation_length").getAsDouble() != length)
                throw new AssertionError("Footstep clock differs from authored clip: " + clip);
            for (int frame : walk ? new int[]{29, 58} : new int[]{18, 32}) {
                double contact = frame / 24.0;
                if (MinotaurAnimationTiming.crossedFootstep(contact - .02, contact - .001, walk)
                        || !MinotaurAnimationTiming.crossedFootstep(contact - .001, contact + .001, walk)
                        || MinotaurAnimationTiming.crossedFootstep(contact + .001, contact + .02, walk))
                    throw new AssertionError("Incorrect contact frame: " + clip + " " + frame);
            }
            for (int rate : new int[]{5, 20, 60, 144}) {
                int contacts = 0;
                double end = length * 100;
                for (int tick = 1; tick <= Math.ceil(end * rate); tick++) {
                    double previous = (tick - 1.0) / rate;
                    double current = Math.min(end, tick / (double)rate);
                    if (MinotaurAnimationTiming.crossedFootstep(previous, current, walk)) contacts++;
                }
                if (contacts != 200) throw new AssertionError(clip + " missed/duplicated contacts at " + rate + " Hz: " + contacts);
            }
        }
        System.out.println("PASS: walk 29/58, run and charge 18/32; 100 loops at 5/20/60/144 Hz");
    }
}
