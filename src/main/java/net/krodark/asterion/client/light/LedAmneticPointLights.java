package net.krodark.asterion.client.light;

import com.meekdev.amnetic.client.light.FalloffCurve;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.Lights;
import net.krodark.asterion.AsterionConfig;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

final class LedAmneticPointLights {
    private static final Map<Object, Light> LIGHTS = new LinkedHashMap<>(128, 0.75F, true);
    private static final Map<Object, LedAmneticLight.LedPointLightSample> BUFFERED = new LinkedHashMap<>();

    private LedAmneticPointLights() {
    }

    static void update(Object key, LedAmneticLight.LedPointLightSample sample) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        if (!config.dynamicLightsEnabled) {
            remove(key);
            return;
        }
        int quality = config.dynamicLightQuality;
        boolean shadows = quality >= 2 && sample.castsShadow();
        float godray = quality >= 2 && sample.radius() >= 6.5F ? 0.24F : 0.0F;
        Light light = LIGHTS.computeIfAbsent(key, ignored -> createLight(sample));
        LedAmneticLight.LedPointLightSample previous = BUFFERED.put(key, sample);
        if (previous != null && previous.position().distanceToSqr(sample.position()) < 0.0009D
                && Math.abs(previous.strength() - sample.strength()) < 0.006F
                && Math.abs(previous.radius() - sample.radius()) < 0.006F
                && Math.abs(previous.red() - sample.red()) < 0.003F
                && Math.abs(previous.green() - sample.green()) < 0.003F
                && Math.abs(previous.blue() - sample.blue()) < 0.003F
                && previous.castsShadow() == sample.castsShadow()) {
            trimToBudget(key);
            return;
        }
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

    static void remove(Object key) {
        Light light = LIGHTS.remove(key);
        BUFFERED.remove(key);
        if (light != null) {
            light.remove();
        }
    }

    static void clear() {
        LIGHTS.values().forEach(Light::remove);
        LIGHTS.clear();
        BUFFERED.clear();
    }

    static Vec3 nearestAttractor(Vec3 origin, double maxDistance) {
        double maxDistanceSquared = maxDistance * maxDistance;
        double bestScore = Double.POSITIVE_INFINITY;
        Vec3 best = null;
        for (LedAmneticLight.LedPointLightSample sample : BUFFERED.values()) {
            if (sample.strength() <= 0.05F || sample.radius() <= 0.25F) continue;
            double distanceSquared = origin.distanceToSqr(sample.position());
            double reach = Math.min(maxDistance, Math.max(2.5D, sample.radius() * 1.65D));
            if (distanceSquared > maxDistanceSquared || distanceSquared > reach * reach) continue;
            double score = distanceSquared / Math.max(0.15D, sample.strength());
            if (score < bestScore) {
                bestScore = score;
                best = sample.position();
            }
        }
        return best;
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
        AsterionConfig config = AsterionConfig.INSTANCE;
        int quality = config.dynamicLightQuality;
        int qualityBudget = quality == 0 ? 24 : quality == 1 ? 56 : 96;
        int budget = Math.min(qualityBudget, config.maxDynamicLights);
        while (LIGHTS.size() > budget) {
            Iterator<Map.Entry<Object, Light>> iterator = LIGHTS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, Light> entry = iterator.next();
                if (Objects.equals(entry.getKey(), protectedKey)) {
                    continue;
                }
                entry.getValue().remove();
                BUFFERED.remove(entry.getKey());
                iterator.remove();
                break;
            }
        }
    }
}
