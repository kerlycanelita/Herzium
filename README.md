# Herzium

[![GitHub](https://img.shields.io/badge/GitHub-Herzium-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/Herzium)
[![Modrinth](https://img.shields.io/badge/Modrinth-Herzium-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/herzium)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/Herzium/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

<p align="center">
  <img src="src/main/resources/assets/herzium/icon.png" alt="Herzium icon" width="220">
</p>

**Faster visual hotbar feedback for ordinary items, with Vanilla gameplay unchanged.**

Herzium is a small client-side visual-response mod. When an ordinary
non-combat hotbar item is requested with a configured hotbar key, Herzium can
preview that slot in the HUD and in hand on the next rendered frame instead of
waiting for Vanilla's next client tick. That can make the response visible up
to one normal client tick (about 50 ms) sooner on a high refresh-rate display.

The preview is provisional and render-only. Vanilla still resolves the real
selected slot, input order, actions and network packets. If repeated previews
disagree with Vanilla, Herzium suspends the preview for the rest of that world.

## What it changes

- **Ordinary-item hotbar preview.** The requested slot and ordinary item can be
  shown on the next rendered frame. Combat items wait for Vanilla.
- **Ordinary-item equip transition.** Removes the decorative equip dip from
  ordinary main-hand and offhand items. Combat items keep Vanilla's complete
  equip transition.
- **Smoother attack indicator.** Interpolates only the displayed attack meter,
  conservatively within Vanilla's current tick. It does not change cooldowns
  or attack timing.
- **Shorter decorative start-up transitions.** Removes the loading-overlay
  fade, title-screen fade and post-world-creation hold. Resource loading and
  world creation still perform their real work.

## What it does not change

Herzium does not increase FPS, accelerate game logic or alter server-side
gameplay. It leaves VSync, `Max Framerate`, `Reduce FPS when inactive`, Raw
Input, Smooth Camera, sensitivity and cursor placement untouched. It does not
write those options to `options.txt`.

The visual preview itself is not sent to the server. The actual selected slot,
attack and use actions, reach, cooldowns, hitboxes and packets remain Vanilla.
Servers may still restrict client mods or identify them through an approved
client/attestation system, so follow each server's rules.

## When it helps

The difference is easiest to see on high refresh-rate displays while switching
ordinary items quickly. Herzium does not improve a GPU- or CPU-limited frame
rate and will not make loading work finish faster.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer and use
   Java 25.
2. Put `herzium-1.9.5.jar` in the `mods` folder.

Minecraft **26.1.2** is the supported game version. Herzium is client-side only.
**Fabric API is not required. Mod Menu is optional and is not required.**

There are no gameplay options. A short information screen is shown once; after
the player chooses **Continue**, its acknowledgement is saved and it will not
appear again.

## Languages

English is the fallback. Minecraft's seven Spanish locales are included:
Argentina, Chile, Ecuador, Spain, Mexico, Uruguay and Venezuela. The early
resource-independent loading message also selects Spanish for any `es_*`
locale and English otherwise.

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
`mod_version` comes from `gradle.properties` — currently `1.9.5`. The file
ending in `-sources.jar` is not the playable build.

## License

MIT. See [LICENSE](LICENSE).

## Credits

Made by **zymekoh**.

[Exordium](https://github.com/tr7zw/Exordium) was studied as a reference for how
a HUD cache behaves, which is why Herzium explicitly handles that overlap.
