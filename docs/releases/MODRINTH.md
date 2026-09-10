<!-- Modrinth summary: Responsive hotbar previews with three selection-order preferences. -->

<p align="center">
  <img src="https://raw.githubusercontent.com/kerlycanelita/Herzium/main/src/main/resources/assets/herzium/icon.png" alt="Herzium icon" width="220">
</p>

<p align="center">
  <a href="https://github.com/kerlycanelita/Herzium"><img src="https://img.shields.io/badge/GitHub-Source-6f2cff?style=for-the-badge&logo=github" alt="GitHub"></a>
  <a href="https://github.com/kerlycanelita/Herzium/issues"><img src="https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues" alt="Issues"></a>
  <a href="https://discord.gg/9t2VxEF7UU"><img src="https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
</p>

# Herzium

**Responsive hotbar previews with three selection-order preferences.**

Herzium is a small client-side visual-response mod. It can preview an ordinary
non-combat hotbar item on the next rendered frame while Vanilla completes the
real selection on its normal client tick. On a high refresh-rate display, that
visual response can appear up to one normal client tick (about 50 ms) sooner.

The preview is provisional. Vanilla order remains the default, and Mod Menu
provides three preferences: Vanilla (highest pending slot wins), Herzium (last
pressed binding among pending slots wins), and Vanilla reversed (lowest slot
wins). Alternative orders change the **real selected slot** and therefore may
change the item used on a server. They are not Vanilla-equivalent; follow server
rules. A confirmed preview disagreement suspends previews for that world.

## Features

- **Hotbar preview:** normal/remapped hotbar bindings can update the highlight
  on the next frame. Ordinary items can also appear in hand; combat items keep
  their Vanilla hand transition.
- **Three selection orders:** a compact Mod Menu button saves your preference.
  This does not increase sampling speed or accelerate click processing.
- **Ordinary-item equip transition:** removes the decorative equip dip from
  ordinary main-hand and offhand items. Swords, axes, pickaxes, spears, maces,
  bows, crossbows, tridents, shields and other classified combat items keep
  Vanilla's complete transition.
- **Smoother attack indicator:** interpolates only the displayed attack meter,
  conservatively within Vanilla's current tick. Attack timing and cooldowns do
  not change.
- **Shorter decorative start-up transitions:** removes the title-screen fade
  and post-world-creation hold. Loading and world
  creation still perform their real work.

## Scope and limits

Herzium does **not** raise FPS, accelerate game logic or make resource loading
finish faster. It leaves VSync, `Max Framerate`, `Reduce FPS when inactive`,
Raw Input, Smooth Camera, mouse sensitivity and cursor placement untouched. It
does not rewrite these settings in `options.txt`.

The preview itself is not sent to the server. Default Vanilla mode retains
Vanilla selection. The other orders can change carried-slot packets and the
resulting actions because a different item wins. Normal/remapped click dispatch,
reach, cooldowns and hitboxes keep their existing paths. Herzium does not add
packets or retries of its own. Servers
may have their own client-mod rules or use an approved client/attestation
system, so players should follow the rules of the server they join.

The difference is easiest to see on high refresh-rate displays during quick
ordinary-item switching. It does not improve a GPU- or CPU-limited frame rate.

## Compatibility

Herzium does not control Raw Input or cursor placement. KoHsium, Raw Input
Buffer, Ixeris and KoHs Inventory Tweaks retain ownership of those behaviors.
Detected input-related mods are reported in the log for troubleshooting.

When Exordium is installed, Herzium bypasses Exordium's HUD frame buffer so the
hotbar preview can be drawn each frame. This means Exordium's HUD caching is
inactive while both mods run. Do not combine them if Exordium's caching is more
important to you than Herzium's per-frame preview.

## Requirements

- Minecraft **26.1.2**
- Fabric Loader **0.19.3 or newer**
- Java **25**
- Client-side only
- **Fabric API is not required**
- **Mod Menu is optional and is not required**

Herzium includes English and every Spanish locale shipped by Minecraft 26.1.2:
Argentina, Chile, Ecuador, Spain, Mexico, Uruguay and Venezuela. Its information
screen is shown once and stays dismissed after choosing **Continue**.

## License

Herzium is available under the [MIT License](https://github.com/kerlycanelita/Herzium/blob/main/LICENSE).

Made by **zymekoh**.
