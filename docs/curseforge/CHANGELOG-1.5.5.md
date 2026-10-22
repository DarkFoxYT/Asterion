# Asterion 1.5.5 — loader and modpack compatibility fixes

- Fixed entity attribute registration on NeoForge 1.21.1 and 26.1.2. All eight Asterion mobs use NeoForge's attribute creation event, avoiding immutable-map startup failures when another mod initializes entity attributes early. Includes the fix for [#1: Crash on start](https://github.com/DarkFoxYT/Asterion/issues/1).
- Fixed Forge 1.20.1 production remapping and added the missing Mixin reference maps for Forge and NeoForge 1.20.1. Client fields such as the centipede interaction click delay now resolve to the correct production names.
- Enabled the bundled 1.20.1 renderer's existing Mixin reference map, fixing additional production startup failures in its client hooks.
- Fixed heavy-water pickup Mixin compatibility across all Minecraft versions. Modern builds use a public interface handler; Forge and NeoForge 1.20.1 use a class-based bucket hook compatible with their older Mixin runtime.
- Bundled MixinExtras 0.5.5 in the Fabric/Quilt 1.20.1 and 1.21.1 files to prevent Quilt startup crashes when reading newer redirect annotations.
- Fixed NeoForge 1.21.1 disconnecting on world join when the Forgified Fabric API networking bridge returned a placeholder codec for Asterion packets.
- Replaced exact-version locks for embedded GeckoLib and Forgified Fabric API on modern NeoForge with bounded patch-version ranges. Modpacks can negotiate a shared compatible copy while Asterion continues to bundle its tested library versions.
- Fixed Amnetic's forward and G-buffer passes restoring stale framebuffer bindings and assumed full-screen viewports. Shader passes now recover their actual draw target, read target and viewport, addressing terrain disappearing with shader packs such as Complementary Unbound.
- Iris/Oculus now use their shader-disabled rendering pipeline in the labyrinth, preserving Asterion's own atmosphere and effects without shader-pack clouds or lighting. The selected pack remains available in other dimensions.
- External shader packs retain control of Overworld lighting, bloom, post-processing and hand depth. Asterion no longer resets the main framebuffer or clears shader depth before hand rendering while an external pack is active.
- Fixed the Minecraft 1.20.1 texture interpolation hook conflicting with Sodium 0.5's older package name when using Iris.
- Fixed Forge and NeoForge 1.20.1 registering biome features and spawns twice through the native modifiers and Fabric API bridge. This prevents feature-order cycle crashes during world generation and keeps spawn weights correct.
- Restored the mapped model-cube accessor on Forge and NeoForge 1.20.1, preventing a world-render crash when the ragdoll renderer initializes armor UVs.
- Retained dedicated-server-safe Assimp packaging, composable renderer window hooks, and the integrated Fabric/Quilt renderer.
- Includes the Forge loader updates from 1.5.4: 47.4.28 for Minecraft 1.20.1, 52.1.20 for 1.21.1, and 64.1.5 for 26.1.2.

Use the file matching your Minecraft version and loader. Runtime libraries are embedded.
Minecraft 1.20.1 requires Java 17; 1.21.1 requires Java 21; 26.1.2 requires Java 25.
NeoForge 1.21.1 requires 21.1.264 or newer; NeoForge 26.1.2 requires 26.1.2.109 or newer.
