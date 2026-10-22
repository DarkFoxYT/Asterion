package net.fabricmc.fabric.api.resource;

public interface IdentifiableResourceReloadListener
        extends net.minecraft.server.packs.resources.PreparableReloadListener {
    net.minecraft.resources.Identifier getFabricId();
}
