# Asterion 2.0 — The Underworld Update

The 2.0 content is isolated under `net.krodark.asterion.update.underworld` and matching
`assets/asterion/underworld` resource paths. Existing Asterion identifiers remain untouched so
old worlds and resource packs do not lose their references.

Current narrative slice:

- A player's first death in a world diverts their respawn to the `asterion:limbo` dimension exactly once.
- The player arrives on the dry bank inside the river's vaulted shale tunnel. That same
  waterway opens onto a broad shoreline and a subterranean sea that continues beyond the crossing.
- A gently curved stone landing leads to the moored ferry. Charon stands aboard as a persistent
  passenger; paying one gold nugget and boarding starts the shared crossing.
- Repeated arrival instructions are removed. Fare responses appear only when interacting with Charon.
- The rocky margins grow virtual two- and three-surface strand networks. They use no entities or
  block entities: loaded cave surfaces deterministically define the anchors, a client PBD/Verlet
  solver supplies sag and player deformation, and the server resolves spring drag and impulse-based
  tearing. Anchors are scattered across exposed block faces rather than snapped to their centres.
  Weapon rays and sufficiently hard sprint, fall, or launch impacts break the exact constraint they
  touch, leaving both surviving lengths loose and fully simulated instead of deleting the whole web.
  Six-block placement cells make the field dense throughout loaded caves; cached surface searches,
  distance culling, and fixed iteration/strand budgets keep generation and rendering bounded.
- Water uses the normal Minecraft fluid surface and a muted jade-green biome tint. There is no
  black-water overlay, extra water plane, UV distortion or Charon motion-blur pass.

Rendering follows the cinematic-quality setting: medium/high integrate twelve low-mist samples,
and low integrates four. The mist is a thin, moss-green 2.2-block band with soft depth contacts,
slowly drifting density, and a restrained distance haze. Rays outside the band skip noise sampling.
It does not replace underwater rendering. The old room-filling dust pass is disabled
in Limbo. When an Iris shader pack is active, the custom depth-based mist pass steps aside for the
pack's own fog and water rendering; the native biome/dimension colours remain available to the pack.

Terrain changes apply to newly generated chunks. Use a fresh test world to see the complete new
arrival and coast; existing Limbo chunks are not automatically deleted or regenerated. Old saved
ferries are raised to the moored waterline and existing Charon entities attach to their ferry.

Validation: compile both loader targets, then run
`./gradlew -I tools/underworld-validation.gradle checkUnderworldTerrain checkUnderworldShaders`
(use `./gradlew.bat` in PowerShell). Shader validation opens a hidden graphics context.
The terrain check covers spawn clearance, the bank and landing, the ferry channel, and open sea
at distant coordinates across 64 seeds. In-game checks should include walking onto the deck,
paying, crossing, saving/reloading, and switching shader packs.

The original Blockbench source is kept in `docs/underworld/source` and converted into runtime
GeckoLib geometry and texture assets by `tools/convert_charons_ferry.py`.
