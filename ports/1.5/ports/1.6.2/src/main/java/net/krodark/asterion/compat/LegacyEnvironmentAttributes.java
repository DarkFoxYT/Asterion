//? if <26.1 {
package net.krodark.asterion.compat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.AttributeTypes;
import net.minecraft.world.attribute.EnvironmentAttribute;

/** Preserve authored biome lighting on the older environment-attribute API. */
public final class LegacyEnvironmentAttributes {
    public static final EnvironmentAttribute<Integer> AMBIENT_LIGHT_COLOR = Registry.register(
            BuiltInRegistries.ENVIRONMENT_ATTRIBUTE, Identifier.withDefaultNamespace("visual/ambient_light_color"),
            EnvironmentAttribute.builder(AttributeTypes.RGB_COLOR).defaultValue(0xffffff)
                    .syncable().spatiallyInterpolated().build());
    private LegacyEnvironmentAttributes() { }
    public static void initialize() { }
}
//?}
