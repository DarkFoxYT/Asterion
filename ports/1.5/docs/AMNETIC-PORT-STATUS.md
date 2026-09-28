# Amnetic port status

All current Asterion 1.5 and Underworld 2.0 jars in the repository's top-level
`modbuilds/` folder include one Amnetic jar for that target. Fabric and Quilt use
Loom's nested-jar metadata. Forge and NeoForge use Jar-in-Jar metadata. The
`tools/verify-modbuilds.ps1` check inspects both the nested file and its loader
metadata after the full build command completes.

Install only the matching Asterion jar and the other required dependencies for
that Minecraft version and loader. Do not add a separate Amnetic jar. Packaging
does not by itself prove that every loader starts. The 1.20.1 Forge and NeoForge
development clients now start; packaged Forge still needs an in-game playthrough.
