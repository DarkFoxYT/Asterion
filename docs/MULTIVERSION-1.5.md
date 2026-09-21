# Asterion 1.5 multiversion beta

This branch builds Minecraft 1.20.1 (Fabric/Quilt), 1.21.1 (Fabric/Quilt and NeoForge), and 26.1.2 (Fabric, Quilt, and NeoForge). Run `./gradlew stageTestBuilds` with Java 25 to build and stage all targets under `build/ported-builds`. Use `gradlew.bat` on Windows. The 1.20.1 and 1.21.1 games still require Java 17 and Java 21 respectively; 26.1.2 requires Java 25.

The 26.1.2 target is a self-contained Gradle build in `ports/26.1.2`. It contains the complete Underworld source and assets. Its automatic first-death entry remains off by default; developers may opt in with `-Dasterion.enableUnderworld=true`.

The 1.20.1 and 1.21.1 targets package the authored Underworld art as dormant resources. Their Labyrinth gameplay remains the active experience. The 26.1.2 Underworld Java code uses newer Minecraft APIs, so the older targets do not register its dimension, entities, or passage yet. Porting those runtime components is still required before Underworld can be enabled on the older versions.

Use a separate profile and a fresh world for each Minecraft version. Copy the jars from only one staged loader folder into that profile. Check `INSTALL-MODS.txt` in the folder for dependency requirements. These builds are beta candidates and need in-game testing.

The packaged downloads are in `build/downloads/1.5` (ignored by Git). Their SHA-256 hashes are:

| Minecraft | Loader | SHA-256 |
| --- | --- | --- |
| 1.20.1 | Fabric/Quilt | `18919FEEB26D4C39168E8913F9BA40C5B8437AC7BE47585C92FDA9B9FD621158` |
| 1.21.1 | Fabric/Quilt | `8B610A861980403E00A5B805EF3BCCB586A9D3D63A1767BC4CE36D16153291EB` |
| 1.21.1 | NeoForge | `8A383688C06F6B8E3B634C16FD461CBF63376474E523F7635B14DE423916568F` |
| 26.1.2 | Fabric | `A6162E4EA0DC09183E995FA5FE0BA9E63F817778ABFBDE313AD4BC820BABC3E7` |
| 26.1.2 | Quilt | `DD4E1FA47973B61EE3B22D41719632BFBFAAF21E449BC1A5E4A7A1DE6A39FD10` |
| 26.1.2 | NeoForge | `099C5B1DE2C222741BFF17C427D128D0BCDF00A6B5ADF56C21E728FC22D757E7` |

All staged builds compiled, the 1.20.1 and 1.21.1 asset checks passed, and every zip passed its CRC check. No in-game playthrough was performed for these 1.5 packages.
