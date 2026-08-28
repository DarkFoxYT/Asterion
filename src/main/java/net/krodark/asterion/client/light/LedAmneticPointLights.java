package net.krodark.asterion.client.light;

import com.meekdev.amnetic.client.light.FalloffCurve;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.Lights;
import net.krodark.asterion.AsterionConfig;
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
        int quality = AsterionConfig.INSTANCE.dynamicLightQuality;
        boolean shadows = quality >= 2 && sample.castsShadow();
        float godray = quality >= 2 && sample.radius() >= 6.5F ? 0.24F : 0.0F;
        Light light = LIGHTS.computeIfAbsent(key, ignored -> createLight(sample));
        light.setPosition(sample.position())
                .setColor(sample.red(), sample.green(), sample.blue())
                .setIntensity(Math.abs(sample.strength()))
                .setRange(Math.max(0.1F, sample.radius()))
                .castsShadow(shadows)
                .shadowStrength(0.72F)
                .godray(godray)
                .godraySteps(quality >= 2 ? 10 : 0)
                .godrayShadows(shadows)
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
        int quality = AsterionConfig.INSTANCE.dynamicLightQuality;
        boolean shadows = quality >= 2 && sample.castsShadow();
        boolean godrays = quality >= 2 && sample.radius() >= 6.5F;
        return Lights.point(
                        sample.position(),
                        sample.red(),
                        sample.green(),
                        sample.blue(),
                        Math.max(0.1F, sample.radius()),
                        Math.abs(sample.strength()))
                .setFalloff(FalloffCurve.SMOOTH)
                .castsShadow(shadows)
                .shadowStrength(0.72F)
                .godray(godrays ? 0.24F : 0.0F)
                .godraySteps(godrays ? 10 : 0)
                .godrayDensity(godrays ? 0.11F : 0.0F)
                .godrayAniso(0.72F)
                .godrayShadows(shadows)
                ;
    }

    private static void trimToBudget(Object protectedKey) {
        int quality = AsterionConfig.INSTANCE.dynamicLightQuality;
        int budget = quality == 0 ? 24 : quality == 1 ? 56 : 96;
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
