# Asterion 1.5.6

- NeoForge now uses native networking, lifecycle, rendering, and registry integration on Minecraft 1.20.1, 1.21.1, and 26.1.2. Forgified Fabric API and Sinytra Connector are no longer required or bundled with the NeoForge builds.
- Fixed the Forge 1.20.1 creative-tab crash by routing item additions through the correctly remapped vanilla interface.
- Restored vanilla wooden, iron, gold, and diamond tool crafting with additive material tags. Existing modpack tag additions remain supported.
- Disabled clouds inside the Labyrinth on every supported loader, including the newer cloud renderer. Other dimensions keep their normal clouds.
- Preserved shader-pack rendering outside the Labyrinth and Asterion's own lighting inside it, with framebuffer and viewport restoration checks on Iris/Complementary.
- Connected native multiplayer tracking, disconnect, and respawn events to boss and ragdoll synchronization. Native render-state attachments stay with their render states.

Use the file matching your Minecraft version and loader. Update both client and server to 1.5.6 for multiplayer.
