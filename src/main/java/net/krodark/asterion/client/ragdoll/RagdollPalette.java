package net.krodark.asterion.client.ragdoll;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

final class RagdollPalette {
    private RagdollPalette() { }

    static int forEntity(Entity entity, RagdollConfig config) {
        if (entity == null) return configured(config);
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath()
                .toLowerCase(Locale.ROOT);

        if (containsAny(path, "ender_dragon", "enderman", "endermite", "shulker")) return 0x6E2499;
        if (containsAny(path, "blaze", "magma_cube", "strider", "ghast", "zoglin")) return 0xF06412;
        if (containsAny(path, "warden", "sculk")) return 0x07505A;
        if (containsAny(path, "slime")) return 0x46A84D;
        if (containsAny(path, "zombie", "drowned", "husk", "zombified_piglin")) return 0x42582A;
        if (containsAny(path, "skeleton", "stray", "bogged")) return 0xA3977B;
        if (containsAny(path, "wither")) return 0x241B2C;
        if (containsAny(path, "spider", "silverfish", "bee")) return 0x59611D;
        if (containsAny(path, "iron_golem")) return 0x31291E;
        if (containsAny(path, "snow_golem")) return 0xB8D7DC;
        return configured(config);
    }

    static int withAlpha(int rgb, float alpha) {
        return (Mth.clamp((int) (alpha * 255.0f + 0.5f), 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    static int darken(int rgb, float factor, float alpha) {
        int red = Mth.clamp((int) (((rgb >>> 16) & 255) * factor), 0, 255);
        int green = Mth.clamp((int) (((rgb >>> 8) & 255) * factor), 0, 255);
        int blue = Mth.clamp((int) ((rgb & 255) * factor), 0, 255);
        return withAlpha((red << 16) | (green << 8) | blue, alpha);
    }

    private static int configured(RagdollConfig config) {
        int red = Mth.clamp((int) (config.red * 255.0f + 0.5f), 0, 255);
        int green = Mth.clamp((int) (config.green * 255.0f + 0.5f), 0, 255);
        int blue = Mth.clamp((int) (config.blue * 255.0f + 0.5f), 0, 255);
        return (red << 16) | (green << 8) | blue;
    }

    private static boolean containsAny(String path, String... fragments) {
        for (String fragment : fragments) if (path.contains(fragment)) return true;
        return false;
    }
}

