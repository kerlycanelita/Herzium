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

The preview is provisional. Vanilla order remains the default. Mod Menu now
offers three preferences for multiple slot inputs pending in the same tick:

| Preference | Which slot wins? | Example |
| --- | --- | --- |
| Vanilla (default) | Highest numbered slot | 1 + 9 selects 9 |
| Herzium | Most recently pressed binding among pending slots | 9 then 1 selects 1 |
| Vanilla reversed | Lowest numbered slot | 1 + 9 selects 1 |

These are selection preferences, not a higher sampling rate. The alternative
orders change the **real selected slot**, so the hand, HUD and item used follow
the same preference. They are not Vanilla-equivalent and may be restricted by
servers. Repeated presses retain Vanilla's queue and normal tick processing;
duplicate bindings in Herzium mode use the higher slot as a deterministic tie-break.
Any confirmed preview disagreement suspends previews for that world.

## What it changes

- **Hotbar preview.** Vanilla's currently resolvable requested slot can be
  highlighted on the next rendered frame. Ordinary items can also appear in hand immediately;
  combat items keep Vanilla's hand/equip transition.
- **Selection-order preference.** A compact Mod Menu button cycles through the
  three modes and saves the choice. No restart is needed.
- **Ordinary-item equip transition.** Removes the decorative equip dip from
  ordinary main-hand and offhand items. Combat items keep Vanilla's complete
  equip transition.
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

The visual preview is not sent to the server. In default Vanilla mode, the
real selection remains Vanilla too. The optional orders can change carried-slot
packets and the resulting item/block actions because they select a different
item. Herzium does not dispatch actions early, add retries, send packets itself,
consume extra clicks or modify reach, cooldowns or hitboxes.
Servers may still restrict client mods or identify them through an approved
client/attestation system, so follow each server's rules.

## When it helps

The difference is easiest to see on high refresh-rate displays while switching
slots quickly, especially ordinary items. Herzium does not improve a GPU- or
CPU-limited frame rate and will not make loading work finish faster.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer and use
   Java 25.
2. Put `herzium-1.10.3.jar` in the `mods` folder.

Minecraft **26.1.2** is the supported game version. Herzium is client-side only.
**Fabric API is not required. Mod Menu is optional and is not required.**

Open Herzium's configuration button in Mod Menu to choose a slot order.
A short information screen is shown once; after
the player chooses **Continue**, its acknowledgement is saved and it will not
appear again.

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
`mod_version` comes from `gradle.properties` — currently `1.10.3`. The file
ending in `-sources.jar` is not the playable build.

The build runs `hotbarOrderTest`, which checks the production preference policy
and logical GUI bounds without launching Minecraft. See the
[26.1.2 audit](docs/audits/AUDIT-1.10.3-26.1.2.md) for scope and limitations.

## Repository layout

| Location | Contents |
| --- | --- |
| `src/` | The main Minecraft 26.1.2 client mod. |
| `version/` | Separate multi-version build and adapters. |
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
