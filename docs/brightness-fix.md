# World brightness regression

The 1.5 legacy bloom mixin previously disabled whole-scene capture only at low quality.
At medium/high quality, Amnetic used the configured 0.075 threshold to prefilter ordinary
scene pixels whenever no emissive G-buffer was populated. The 2.45 bloom intensity then
added that blurred terrain back onto the scene. `all(false)` does not disable this fallback.

All 1.5 variants and Underworld now keep scene capture disabled by default, at every quality.
Explicit emissive geometry, particles and populated emissive G-buffers retain their own paths.
The optional `sceneBloom` setting only applies in Asterion dimensions; ordinary worlds and
disconnected clients cannot enable scene bloom. Legacy emissive settings migrate to version 8
without changing customized thresholds, intensities or individual emissive strengths.

The 26.1.2 brightness mixin also leaves vanilla brightness untouched outside Asterion/Limbo,
including world changes and disconnects. No Minecraft gamma option or world data is rewritten.
Existing and newly generated worlds receive the fix immediately with the updated mod.

Run `LightingPolicySmoke` against each source tree, and compile each loader target.
Visual follow-up: compare bloom on/off in an ordinary daytime Overworld and a dark cave,
then enter and leave Asterion/Limbo. Torches and emissive eyes should still glow locally.
