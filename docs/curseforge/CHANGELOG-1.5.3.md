# Asterion 1.5.3

Compatibility and multiplayer maintenance update for Minecraft 1.20.1, 1.21.1,
and 26.1.2, with Fabric, Quilt, Forge, and NeoForge builds.

- Fix dedicated-server startup failures caused by the bundled Assimp library
  requiring a client-only LWJGL module.
- Fix the animated-texture mixin conflict with Sodium on the legacy ports.
- Update the 26.1.2 renderer's window hook to compose with Sodium's driver
  workarounds and handle graphics-context fallback correctly.
- Send ragdoll poses and state changes to players tracking the entity, while
  preserving the owner's incoming pose packets for replay recording.
- Reduce network traffic for sleeping ragdolls, keep moving bodies updating
  every tick, and assemble pose snapshots in one pass through the body pieces.
- Reject stale or malformed poses and bound per-connection pose history.
- Prevent delayed pose packets from restarting ragdolls after recovery or respawn.
- Restore the 26.1.2 server item-use guard during ragdolls and boss grabs.
- Protect the Cursed Brazier and vanilla bosses from client dismemberment kill requests.
- Reduce repeated boss-bar work without changing combat timing or physics quality.

Replace the existing Asterion JAR on both the client and the server with the
matching Minecraft and loader build. Fabric/Quilt builds require Fabric API;
GeckoLib requirements follow the selected build's metadata.
