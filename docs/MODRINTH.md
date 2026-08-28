<!-- Modrinth summary: Faster visual hotbar feedback for ordinary items, with Vanilla gameplay unchanged. -->

<p align="center">
  <img src="https://raw.githubusercontent.com/kerlycanelita/Herzium/main/src/main/resources/assets/herzium/icon.png" alt="Herzium icon" width="220">
</p>

<p align="center">
  <a href="https://github.com/kerlycanelita/Herzium"><img src="https://img.shields.io/badge/GitHub-Source-6f2cff?style=for-the-badge&logo=github" alt="GitHub"></a>
  <a href="https://github.com/kerlycanelita/Herzium/issues"><img src="https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues" alt="Issues"></a>
  <a href="https://discord.gg/9t2VxEF7UU"><img src="https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
</p>

# Herzium

**Faster visual hotbar feedback for ordinary items, with Vanilla gameplay unchanged.**

Herzium is a small client-side visual-response mod. It can preview an ordinary
non-combat hotbar item on the next rendered frame while Vanilla completes the
real selection on its normal client tick. On a high refresh-rate display, that
visual response can appear up to one normal client tick (about 50 ms) sooner.

The preview is provisional and render-only. Vanilla still resolves the actual
selected slot, input order, actions and network packets. If repeated previews
disagree with Vanilla, Herzium suspends the preview for the rest of that world.

## Features

- **Ordinary-item hotbar preview:** configured hotbar-key input can update the
  visible selected slot and ordinary item on the next rendered frame. Combat
  items wait for Vanilla.
- **Ordinary-item equip transition:** removes the decorative equip dip from
  ordinary main-hand and offhand items. Swords, axes, pickaxes, spears, maces,
  bows, crossbows, tridents, shields and other classified combat items keep
  Vanilla's complete transition.
- **Smoother attack indicator:** interpolates only the displayed attack meter,
  conservatively within Vanilla's current tick. Attack timing and cooldowns do
  not change.
- **Shorter decorative start-up transitions:** removes the loading-overlay
  fade, title-screen fade and post-world-creation hold. Loading and world
  creation still perform their real work.

## Scope and limits

Herzium does **not** raise FPS, accelerate game logic or make resource loading
finish faster. It leaves VSync, `Max Framerate`, `Reduce FPS when inactive`,
Raw Input, Smooth Camera, mouse sensitivity and cursor placement untouched. It
does not rewrite these settings in `options.txt`.

The preview itself is not sent to the server. The real selected slot, attack
and use actions, reach, cooldowns, hitboxes and packets remain Vanilla. Servers
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
