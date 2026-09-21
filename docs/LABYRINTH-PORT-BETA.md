# Labyrinth beta builds for Minecraft 1.20.1 and 1.21.1

These packages were built from commit `8cd4a47a` of the multiversion branch on 2026-09-21. They contain the Labyrinth gameplay and fixes already present on that branch. The Underworld chapter from the 26.1.2 beta has not been ported to these Minecraft APIs yet.

Use a separate profile and a fresh world. Copy every jar from one package's `mods` folder into the matching Minecraft profile. Do not combine packages. See `INSTALL-MODS.txt` inside each zip for loader and Java requirements.

| Minecraft | Loader | Package SHA-256 |
| --- | --- | --- |
| 1.20.1 | Fabric or Quilt | `EE28FB73124EB46A5838AC987DCEA4933B5F34C97F02191D7AE9C36FB45C9915` |
| 1.21.1 | Fabric or Quilt | `8982CA4C93240D1C1BF4430C16BFA60F3FD8062A308BF0F723597D112F804D94` |
| 1.21.1 | NeoForge | `DF7E6B287B909BEA14CD895297C25B1E4E836F90256B2E52150692A21B86F9B9` |

All three `stagePlayableBuild` tasks completed successfully and each zip passed its archive CRC check. This verifies packaging and compilation; it does not replace in-game beta testing.
