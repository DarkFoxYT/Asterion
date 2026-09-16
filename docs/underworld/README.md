# Asterion 2.0 — The Underworld Update

The 2.0 content is isolated under `net.krodark.asterion.update.underworld` and matching
`assets/asterion/underworld` resource paths. Existing Asterion identifiers remain untouched so
old worlds and resource packs do not lose their references.

Current narrative slice:

- A player's first death in a world diverts their respawn to the `asterion:limbo` dimension exactly once.
- The dimension is a finite, enclosed shale passage whose river leads from the arrival shelf to
  a terminal cavern.
- Charon's ferry waits at the first navigable water and begins its guided journey when boarded.
- The river atmosphere post-effect combines deep-black water extinction, moving parallax
  refraction, restrained surface highlights, and a separate low fog volume above the waterline.

Rendering automatically follows the existing cinematic-quality setting. Medium and high use
depth-reconstructed, two-scale refraction plus bounded volumetric mist. Low uses a reduced-noise
analytic path. Both paths reject pixels outside the river corridor before doing animated sampling,
and the depth reconstruction includes the camera-world offset so the effect stays locked to Limbo's
water plane while the player and ferry move.

The original Blockbench source is kept in `docs/underworld/source` and converted into runtime
GeckoLib geometry and texture assets by `tools/convert_charons_ferry.py`.
