# Asterion 1.5.9

- Fixed client worlds remaining in weapon-chain, grab-pose, weapon-release, and sword-trail caches after leaving a world.
- Fixed stale weapon spring entries surviving because cleanup compared ages from different entities.
- Release ragdoll simulation scratch data when its world is cleared.
- Release Amnetic's temporary particle-world reference after simulation, including failed steps.
- Release layer capture color/depth textures when the layer renderer is disposed, and recreate them when needed.
- Clean up older-port emission, post-processing, and interpolation caches at resource reload, disconnect, and shutdown.
- Stop older-port ground telegraph data retaining a world after rendering stops.
- Avoid an unnecessary extra pose-stack scope when rendering ordinary mobs.
- Reuse bounded collision-query storage to reduce temporary allocations while refreshing terrain every tick.
- Stop grapple renderers retaining worlds that are no longer active.
- Refresh 26.1.2 recipe-search reload callbacks after dimension changes so they release the previous world and use the current recipe context.

Graphics, textures, shader effects, animation quality, light budgets, particle limits, and gameplay remain unchanged. Spark and diagnostic fixtures are used only for development and are not bundled with the mod.
