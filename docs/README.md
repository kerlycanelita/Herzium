# Herzium documentation

[Back to overview](../README.md)

## Guides

- [1.10.6 audit: clicks keep the key pressed before them](audits/AUDIT-1.10.6-action-boundary.md)
- [1.10.5 audit: order hooks per version and the crystal cycle](audits/AUDIT-1.10.5-orders-static.md)
- [In-game selection-order audit, 1.10.3 and 1.10.4](audits/AUDIT-1.10.4-orders-ingame.md)
- [Seven-version release audit, 1.21.10 through 26.3](audits/AUDIT-1.10.3-release-2026-09-22.md)
- [1.10.3 audit](audits/AUDIT-1.10.3-26.1.2.md)
- [1.10.3 multiversion audit](audits/AUDIT-1.10.3-multiversion.md)
- [Earlier 1.9.3 audit](audits/AUDIT-1.9.3.md)
- [Modrinth description](releases/MODRINTH.md)
- [Recorded evidence](evidence/1.9.3-26.1.2/README.md)
- [Build checksums](checksums.txt)
- [Diagnostic companion](../debug/README.md)

## Root build

| Setting | Value |
| --- | --- |
| Minecraft | `26.1.2` |
| Mod version | `1.10.6` |
| Loader | Fabric `0.19.3` |
| Loom | `1.17.17` |
| JDK | 25 |

Official Minecraft names for the 26.x root target.
The source of truth is [gradle.properties](../gradle.properties) and
[build.gradle](../build.gradle); each additional target declares its own dependencies.

## Working folders

Run build commands from the repository root unless a target's instructions say
otherwise. `build/`, `.gradle/` and `run/` hold local build or game state and are
excluded from Git. Preserve saves, configuration and logs when organizing files.
Audits describe the version and checks recorded at the time; they do not imply
that every later change has been tested in a running game.
