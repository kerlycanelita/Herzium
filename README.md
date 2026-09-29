# Herzium

[![GitHub](https://img.shields.io/badge/GitHub-Herzium-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/Herzium)
[![Modrinth](https://img.shields.io/badge/Modrinth-Herzium-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/herzium)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/Herzium/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

<p align="center">
  <img src="src/main/resources/assets/herzium/icon.png" alt="Herzium icon" width="220">
</p>

**Responsive hotbar previews with three selection-order preferences.**

Herzium is a small client-side visual-response mod. When a hotbar slot is
requested with a configured hotbar key, Herzium can preview Vanilla's currently
resolvable HUD highlight on the next rendered frame instead of waiting for
Vanilla's next client tick. Ordinary non-combat items can also be previewed in hand. That can
make the response visible up to one normal client tick (about 50 ms) sooner on
a high refresh-rate display.

The existing input sampling is unchanged: Herzium observes logical key events
as Minecraft registers them, including remapped hotbar bindings. Attack/Use
continue through their normal Vanilla path with the same cooldown handling.

The preview is provisional. Herzium order is the default; Vanilla order is one
click away in Mod Menu, which offers three preferences for multiple slot inputs
pending in the same tick:

| Preference | Which slot wins? | Example |
| --- | --- | --- |
| Vanilla | Highest numbered slot | 1 + 9 selects 9 |
| Herzium (default) | Most recently pressed binding among pending slots | 9 then 1 selects 1 |
| Vanilla reversed | Lowest numbered slot | 1 + 9 selects 1 |

These are selection preferences, not a higher sampling rate. Herzium and Vanilla
reversed change the **real selected slot**, so the hand, HUD and item used follow
the same preference. They are not Vanilla-equivalent and may be restricted by
servers. Because Herzium order is the default, a fresh install is in that state
from its first session; select Vanilla order to keep Vanilla selection. Repeated presses retain Vanilla's queue and normal tick processing;
duplicate bindings in Herzium mode use the higher slot as a deterministic tie-break.
A key tapped twice inside one tick, or held until it auto-repeats, leaves clicks
queued; in Herzium order those older clicks can no longer undo a newer key.
Vanilla applies every hotbar key of a tick before its Use and Attack clicks; in
Herzium order a key pressed after the first Use of a tick (or after an Attack
when no Use follows) waits for the next tick, so the click uses the item held
when it was pressed.
A preview that Vanilla's own selection contradicts suspends previews for that
world; a slot chosen by another mod or the server only clears it.

## What it changes

- **Hotbar preview.** Vanilla's currently resolvable requested slot can be
  highlighted on the next rendered frame. Ordinary items can also appear in hand immediately;
  combat items keep Vanilla's hand/equip transition.
- **Selection-order preference.** A compact Mod Menu button cycles through the
  three modes and saves the choice. No restart is needed.
- **Ordinary-item equip transition.** Removes the decorative equip dip from
  ordinary main-hand and offhand items. Placing one still plays Vanilla's short
  hand dip, the visual confirmation of the placement. Combat items keep
  Vanilla's complete equip transition.
- **Smoother attack indicator.** Interpolates only the displayed attack meter,
  conservatively within Vanilla's current tick. It does not change cooldowns
  or attack timing.
- **Shorter decorative start-up transitions.** Removes the title-screen fade
  and post-world-creation hold. Resource loading and
  world creation still perform their real work.

## What it does not change

Herzium does not increase FPS or accelerate client/server ticks. It leaves
VSync, `Max Framerate`, `Reduce FPS when inactive`, Raw
Input, Smooth Camera, sensitivity and cursor placement untouched. It does not
write those options to `options.txt`.

The visual preview is not sent to the server. Selecting Vanilla order keeps the
real selection Vanilla too. The default Herzium order and Vanilla reversed can
change carried-slot packets and the resulting item/block actions because they
select a different item. Herzium does not dispatch actions early, add retries, send packets itself,
consume extra clicks or modify reach, cooldowns or hitboxes.
Servers may still restrict client mods or identify them through an approved
client/attestation system, so follow each server's rules.

## When it helps

The difference is easiest to see on high refresh-rate displays while switching
slots quickly, especially ordinary items. Herzium does not improve a GPU- or
CPU-limited frame rate and will not make loading work finish faster.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer.
2. Put the jar that matches your exact Minecraft version in the `mods` folder.

| Minecraft | Jar | Java |
| --- | --- | --- |
| 1.21.10 | `herzium-1.21.10-1.10.7.jar` | 21 |
| 1.21.11 | `herzium-1.21.11-1.10.7.jar` | 21 |
| 26.1 | `herzium-26.1-1.10.7.jar` | 25 |
| 26.1.1 | `herzium-26.1.1-1.10.7.jar` | 25 |
| 26.1.2 | `herzium-26.1.2-1.10.7.jar` or `herzium-1.10.7.jar` | 25 |
| 26.2 | `herzium-26.2-1.10.7.jar` | 25 |
| 26.3 | `herzium-26.3-1.10.7.jar` | 25 |

The root build's `herzium-1.10.7.jar` accepts `>=26.1.2 <26.2`, so it also loads
on a later 26.1.x patch; the per-version jars pin their exact version.

Herzium is client-side only. **Fabric API and Mod Menu are both optional**, with
two consequences worth knowing:

- Fabric Loader ships no resource-pack support, so **without Fabric API**
  Minecraft never reads any mod's language files and Herzium's screens show raw
  keys such as `herzium.warning.title`. Everything still works; only the wording
  is missing.
- **Without Mod Menu** there is no button to open the settings screen, so the
  selection order stays on its default.

A short information screen is shown once; after the player chooses **Continue**,
its acknowledgement is saved and it will not appear again.

## Languages

English is the fallback. Minecraft's seven Spanish locales are included:
Argentina, Chile, Ecuador, Spain, Mexico, Uruguay and Venezuela.

## Compatibility

Herzium does not control Raw Input or the cursor, so KoHsium, Raw Input Buffer,
Ixeris and KoHs Inventory Tweaks retain ownership of those behaviors. Detected
input-related mods are reported in the log for troubleshooting.

If Exordium is installed, Herzium bypasses Exordium's HUD frame buffer so the
hotbar preview can be drawn each frame. Exordium's HUD caching is therefore
inactive while both mods run. Players who prefer Exordium's caching should not
combine the two mods.

## Building

```bash
./gradlew build
```

The release JAR is written to `build/libs/herzium-<mod_version>.jar`, where
`mod_version` comes from `gradle.properties` — currently `1.10.7`. The file
ending in `-sources.jar` is not the playable build.

The other game versions come from a separate build:

```powershell
version\build-all.ps1
```

Each target lands in `version/<minecraft_version>/build/libs/`. That script
needs a Java 25 JDK, which it resolves from `JAVA_HOME`, `PATH` or the usual
install roots; the 1.21.x targets additionally compile at release 21 through a
toolchain Gradle downloads on demand.

The build runs `hotbarOrderTest`, which checks the production preference policy
and logical GUI bounds without launching Minecraft. See the
[26.1.2 audit](docs/audits/AUDIT-1.10.3-26.1.2.md) for scope and limitations, and
the [multiversion audit](docs/audits/AUDIT-1.10.3-multiversion.md) for how the
per-version builds were checked.

The [seven-version release audit](docs/audits/AUDIT-1.10.3-release-2026-09-22.md)
covers the final JARs from 1.21.10 through 26.3, including the 26.3 hand-state
adapter. To reproduce that build and export its isolated client launch inputs:

```powershell
version\build-all.ps1 -MinecraftVersions 1.21.10,1.21.11,26.1,26.1.1,26.1.2,26.2,26.3 -ExportRuntime
py tools/validation/smoke-release.py 1.21.10 1.21.11 26.1 26.1.1 26.1.2 26.2 26.3
py tools/validation/smoke-release.py 26.3 --gameplay
py tools/validation/smoke-release.py 1.21.11 26.1.2 --orders
```

Validation clients use new profiles under `tmp/release-audit/`, test the packaged
release JARs, and close automatically. The gameplay checks create their own world.
`--orders` presses hotbar keys through the real keyboard handler and records what
the HUD showed and which slot packets were sent; the
[in-game order audit](docs/audits/AUDIT-1.10.4-orders-ingame.md) has its results.
`--crystal` (26.2 and later) times the obsidian and end-crystal placement cycle
under each order; the [1.10.5 audit](docs/audits/AUDIT-1.10.5-orders-static.md)
has its results and a per-version bytecode check of the order hooks.
The validation mod is never included in a release artifact.

## Repository layout

| Location | Contents |
| --- | --- |
| `src/` | The shared client mod; the root build targets 26.1.2. |
| `version/` | Per-game-version builds and the adapters they need. |
| `debug/` | The optional diagnostic companion, built separately. |
| `docs/` | Audits, release text, checksums and recorded evidence. |
| `tools/` | Hotbar tests and repository maintenance helpers. |

Start with the [documentation index](docs/README.md). Build output and local
Minecraft instances remain outside Git; keep installable JARs out of the source tree.

## License

MIT. See [LICENSE](LICENSE).

## Credits

Made by **zymekoh**.

[Exordium](https://github.com/tr7zw/Exordium) was studied as a reference for how
a HUD cache behaves, which is why Herzium explicitly handles that overlap.
