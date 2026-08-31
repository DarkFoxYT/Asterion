# Local emissive rendering

The emissive implementation lives entirely in Asterion. `libs/amnetic-1.0-SNAPSHOT-asterion.1.jar` (upstream commit `de756b6` plus the documented local-preset safety patch) is bundled inside Asterion together with its bb4j parser dependency. The patch does not change Amnetic rendering or add G-buffer hooks.

`AsterionEmissiveBoneLayer` replaces each selected bone with one full-bright, depth-tested surface draw using Minecraft's entity pipeline. Texture alpha and ordinary fog still apply. Dimming scales RGB without changing opacity. Vines additionally submit only their `glow` geometry to Amnetic's explicit emission buffer, as described in `vine-emission.md`; the other layers retain their surface-only behavior.

- Minotaur: `glow`, parent `head`, using the current 512px `minotaur.png` atlas rather than the mismatched legacy eye masks. Default brightness is 0.85. Animated eye tint remains intact.
- Vine: only `glow` emits, including the nested `glow` under `head` in the new upright model. Default brightness is 0.65. Only end segments render the bulb. Each orientation selects its authored model and root pose; `head` stays normally lit.
- Other layers, including runes and sanctuaries, default to 0.8 surface brightness. The legacy `emissiveStrength` hook remains source-compatible but no longer adds a halo; new layers can override `surfaceBrightness`.

`EmissiveBoneMesh` caches immutable local-space position/UV arrays per baked bone. Every entity using that baked model shares its buffer. The render loop only applies the captured animated matrix and tint, using a reusable vector and Minecraft's batched vertex staging. Cube transforms and quad traversal happen only when the cache is first populated. Weak keys allow replaced model data to be collected after resource reload. Matching model/layer atlases skip redundant texture-dimension lookups. This reduces submissions and CPU allocation; it is not a measured FPS guarantee or a persistent GPU instance buffer.

`config/asterion-emissive.json` now has version 2. Existing shipped bright defaults migrate once: eye strength 4.75 becomes brightness 0.85, vine strength 2.25 becomes 0.65, and particle bloom intensity 4.8 becomes 0.16 with two blur levels. Customized values are retained within the new bounds. The legacy strength field names remain to avoid breaking existing config files. The enabled setting controls particle bloom; these sharp surfaces do not require bloom.

Verification: `gradlew build emissiveRegression` checks the updated dependency APIs, frustum bounds, ordered instance-buffer growth, bb4j model parsing, existing regression suites, cached vertices against GeckoLib through 32 rotated/scaled poses, and the local full-bright shader on a hidden OpenGL context. GPU checks cover lower brightness, preserved alpha, occlusion, texture transparency, fog and no halo outside the geometry.

Restart the client to replace the previous Java implementation. Live in-game appearance, all block states, resource reload and resize behavior still need a visual check; the hidden GPU test does not render a live GeckoLib scene.
