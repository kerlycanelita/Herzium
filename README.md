# Herzium

[![GitHub](https://img.shields.io/badge/GitHub-Herzium-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/Herzium)
[![Modrinth](https://img.shields.io/badge/Modrinth-Herzium-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/herzium)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/Herzium/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

<p align="center">
  <img src="src/main/resources/assets/herzium/icon.png" alt="Herzium icon" width="220">
</p>

**Switching items feels instant.**

Minecraft only registers your hotbar key on its next game tick, so pressing `2`
can sit there for a fraction of a second before anything moves. Herzium draws
the slot you pressed straight away, on the very next frame.

Client-side, works on any server, and it is not an FPS mod.

## What you get

- **Instant hotbar.** Press a number and the highlight and the item in your hand
  move immediately instead of waiting for the next tick. Up to 50 ms sooner.
- **No equip wobble.** Ordinary items appear in your hand without the little dip
  animation. Swords, axes, bows, shields and the rest of your combat gear keep
  it, so nothing about fighting looks different.
- **A start-up that gets out of the way.** The fade at the end of the loading
  screen, the fade on the title screen and the short pause after creating a
  world are gone. Loading itself is untouched.

## What it does not do

It does not give you more FPS, and it does not pretend to. If your game is
already struggling, this will not help.

It also leaves your settings alone. VSync, `Max Framerate`, `Reduce FPS when
inactive`, Raw Input and mouse sensitivity are exactly where you put them, and
Herzium never writes to `options.txt`.

Nothing it changes reaches the server. Your hotbar selection, attack and use
timing, reach, cooldowns and hitboxes are all still Minecraft's.

## Where it makes a difference

Best on a high refresh-rate monitor and when you switch items quickly, which
mostly means PvP. That fraction of a second is the whole point, so if you never
notice it, you do not need this mod.

## Install

1. [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer, and Java 25.
2. Drop `herzium-1.9.4.jar` in your `mods` folder.

Client-side only, and no Fabric API required. There is nothing to configure:
everything above is always on, and the only screen Herzium adds is a one-time
notice the first time you launch it.

## Compatible with

Herzium does not touch Raw Input or the cursor, so **KoHsium**, **Raw Input
Buffer**, **Ixeris** and **KoHs Inventory Tweaks** keep doing their thing. It
tells you in the log which ones it found.

With **Exordium** installed it skips that mod's HUD cache, so the hotbar is
drawn every frame. That is the one place the two overlap, and Herzium says so
at start-up.

## Supported version

Minecraft **26.1.2** only. Other versions are being worked on but are not
released yet.

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/herzium-<mod_version>.jar`, where `mod_version`
comes from `gradle.properties` — currently `1.9.4`. Ignore the file ending in
`-sources.jar`.

## License

MIT. See [LICENSE](LICENSE).

## Credits

Made by **zymekoh**.

[Exordium](https://github.com/tr7zw/Exordium) was studied as a reference for how
a HUD cache behaves, which is why Herzium knows to step around it.
