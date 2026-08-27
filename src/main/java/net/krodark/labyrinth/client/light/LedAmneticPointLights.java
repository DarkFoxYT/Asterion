package net.krodark.labyrinth.client.light;

import com.meekdev.amnetic.client.light.FalloffCurve;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.Lights;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Budgeted Amnetic point lights shared by LEDs and small emissive props. */
final class LedAmneticPointLights {
    private static final Map<Object, Light> LIGHTS = new LinkedHashMap<>(128, 0.75F, true);

    private LedAmneticPointLights() {
    }

    static void update(Object key, LedAmneticLight.LedPointLightSample sample) {
        Light light = LIGHTS.computeIfAbsent(key, ignored -> createLight(sample));
        light.setPosition(sample.position())
                .setColor(sample.red(), sample.green(), sample.blue())
                .setIntensity(Math.abs(sample.strength()))
                .setRange(Math.max(0.1F, sample.radius()))
                .castsShadow(sample.castsShadow() && true)
                .shadowStrength(0.72F)
                .godray(sample.radius() >= 6.5F ? 0.32F : 0.0F)
                .godraySteps(16)
                .godrayShadows(sample.castsShadow())
                .setEnabled(true);
        trimToBudget(key);
    }

    static void retainOnly(Set<?> activeKeys) {
        Iterator<Map.Entry<Object, Light>> iterator = LIGHTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Light> entry = iterator.next();
            if (!activeKeys.contains(entry.getKey())) {
                entry.getValue().remove();
                iterator.remove();
            }
        }
    }

    static void remove(Object key) {
        Light light = LIGHTS.remove(key);
        if (light != null) {
            light.remove();
        }
    }

    static void clear() {
        LIGHTS.values().forEach(Light::remove);
        LIGHTS.clear();
    }

    private static Light createLight(LedAmneticLight.LedPointLightSample sample) {
        return Lights.point(
                        sample.position(),
                        sample.red(),
                        sample.green(),
                        sample.blue(),
                        Math.max(0.1F, sample.radius()),
                        Math.abs(sample.strength()))
                .setFalloff(FalloffCurve.SMOOTH)
                .castsShadow(sample.castsShadow()
                        && true)
                .shadowStrength(0.72F)
                .godray(sample.radius() >= 6.5F ? 0.32F : 0.0F)
                .godraySteps(16)
                .godrayDensity(sample.radius() >= 6.5F ? 0.14F : 0.0F)
                .godrayAniso(0.72F)
                .godrayShadows(sample.castsShadow())
                ;
    }

    private static void trimToBudget(Object protectedKey) {
        int budget = 96;
        while (LIGHTS.size() > budget) {
            Iterator<Map.Entry<Object, Light>> iterator = LIGHTS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, Light> entry = iterator.next();
                if (Objects.equals(entry.getKey(), protectedKey)) {
                    continue;
                }
                entry.getValue().remove();
                iterator.remove();
                break;
            }
        }
    }
}

