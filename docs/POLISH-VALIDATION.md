# 26.1.2 polish validation

Minotaur look smoothing advances once per entity frame, including replay time, and grab targets use interpolated positions. Once a player is held, the arm stops aiming back at the position driven by that same arm. Idle/walk/chase tracking is restored while authored attack and cinematic poses retain control. Chain grappling explicitly keeps its authored pose; the chain renderer follows its animated attachment. The whole-body/eye/bossbar green cue and gear-dependent attack recovery/combo pressure accompany the port changes.

Ragdoll item use is blocked on both sides. Afterblow has a first-person blocking transform. Greek-fire particles have interpolated roll. Maze structure lookups avoid boxed coordinate keys. Two empty ferry animation resources now contain a stationary idle animation.

Native NeoForge packaging relocates Amnetic's bridge out of Minecraft's module, restores its missing guarded Iris helper, supplies the bridge's access rule, exposes Assimp natives, embeds Forgified Fabric API, and accepts the exact embedded renderer version. Asterion itself uses a loader-neutral render-type invoker. No Connector is required.

## Checks

- Fabric live client: world creation and actual Minotaur rendering with changing look targets and repeated grab/held-player timelines.
- NeoForge packaged client: the same scenario using the release JAR with GeckoLib as the only external mod; explicit ASTERION_26_POLISH pass marker.
- Fabric and NeoForge build compilation and packaging.

These are short renderer smoke tests, not exhaustive 26.1.2 gameplay, multiplayer, external shader-pack or Quilt validation. They do not establish a percentage of visual parity or guarantee zero performance issues. The port branch has additional long gameplay and 6,000-frame GeckoLib 4 regressions.


## NeoForge fluid hotfix (2026-09-20)

HeavyWaterFluid and TidalWaterFluid now explicitly return NeoForge's water FluidType through a NeoForge-only mixin. This covers source, flowing and tidal variants without changing their custom flow/tide implementation or Fabric. The packaged client verified all three registered custom fluids and all 26 states, then completed the in-world renderer smoke test without the reported getFluidType exception.
