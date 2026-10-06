# Essential and ragdolls

The 26.1.2 client supports Essential as an optional mod. The integration uses the
installed Essential cosmetic renderer and does not bundle or require Essential.
Host and guests should run the same Asterion build for shared-world ragdoll syncing.

- Cosmetics on the head, torso, arms and legs follow their physical body parts.
  Essential's skin masks, cosmetic-versus-armor setting and hidden held items are respected.
- Capes follow the physical torso, including Essential emissive cape textures.
  Cape visibility follows the player setting and capes are hidden when elytra is equipped.
- A knockdown interrupts the local Essential emote and closes its wheel. Starting
  another emote is blocked until recovery. The pre-emote camera is restored after recovery.
- Ragdolls keep a previously loaded player skin and its slim/wide model during
  temporary default-skin responses. Actual loaded skin changes still update the body.
  Skin memory is scoped to the client connection and cleared on disconnect.
- A player entering tracking range receives both the active state and latest
  accepted pose. Recovery, respawn, dimension changes and disconnect clear that pose.

Run `gradlew :26.1.2-fabric:checkRagdollCompatibility -I tools/ragdoll-compat-validation.gradle`
for skin-cache and bone-transform regressions. If the development Essential jar
and UniversalCraft library are installed in `run/essential`, the check also validates
the optional API signatures against those jars without initializing Essential's UI.

Restart `runEssentialClient` after building/staging a new jar. The currently running
client keeps its old code. For live verification, use a host and guest with different
skins and cosmetic outfits; knock down each player, enter tracking range while they
are down, recover from an emote, and repeat after respawn and reconnect. Check the
body, cape and cosmetics from both clients. Automated checks do not replace that
two-client visual and network test or remove the connection's latency.
