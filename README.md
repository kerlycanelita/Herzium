# Herzium

[![GitHub](https://img.shields.io/badge/GitHub-Herzium-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/Herzium)
[![Modrinth](https://img.shields.io/badge/Modrinth-Herzium-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/herzium)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/Herzium/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

<p align="center">
  <img src="src/main/resources/assets/herzium/icon.png" alt="Herzium icon" width="220">
</p>

Herzium is a client-side Fabric mod for Minecraft 26.1.2 that removes visual
latency Minecraft adds on purpose, and nothing else. It does not raise your
frame rate, and it no longer touches VSync, the frame limit, Raw Input or the
cursor: every one of those is either a setting Minecraft already exposes or
another mod's to own.

**When it helps:** high refresh-rate displays and fast item switching, mainly
PvP. **When it does not:** if you are GPU or CPU bound, this changes nothing.

## Compatibility

Herzium does not write to `options.txt`, does not change the Raw Input window
mode, and does not place the cursor. KoHsium, Raw Input Buffer, Ixeris and
KoHs Inventory Tweaks keep everything they own; Herzium only reports what it
detects so a player can tell who is doing what.

When Exordium is installed, Herzium bypasses its HUD cache so the hotbar and
the rest of the HUD are extracted every frame. That is the one place where the
two mods disagree, and it is reported at start-up.

## What it removes

- The two-second fade at the end of the loading overlay.
- The two-second title-screen fade.
- The 500 ms hold after a new world is created, once chunk readiness and the
  compiled player section are already satisfied. The work itself is untouched.

Nothing here is exposed as a Vanilla setting. Resource reloading, validation,
model baking, shader compilation and error recovery are not modified.

## Low-latency input

- Raw Input, Smooth Camera, mouse grabbing, and cursor placement are completely
  Vanilla-owned unless another installed mod changes them. Herzium neither
  reads ahead nor writes these controls.
- Opening an inventory or container invokes no Herzium cursor operation. If a
  pointer still moves or centres with this build, that movement comes from
  Vanilla or another installed mod rather than Herzium.
- Raw Input Buffer and Ixeris are detected only to report their unsupported
  overlap. Detection never changes either pipeline or Vanilla's setting.
- VSync, `Max Framerate` and `Reduce FPS when inactive` are yours. Herzium used
  to override all three; it no longer touches any of them, because Minecraft
  already exposes them and overriding a setting silently is worse than not
  having the feature.
- Priority Hotbar is Herzium's primary input feature. Mouse movement remains
  outside its scope; end-to-end mouse latency depends on Vanilla, installed
  input mods, polling rate, display refresh, the GPU queue, and the compositor.
- `Priority Hotbar` previews the slot you pressed in the HUD and in the
  first-person hand before Vanilla's next tick commits it. It never consumes a
  click, never writes the selected slot, and never sends a packet: Vanilla
  resolves the selection exactly as it always did, and Herzium only stops the
  display from waiting up to one tick to agree.
- When several hotbar keys land in the same tick, the preview resolves them the
  way Vanilla's ascending loop will, so the previewed item is the item Vanilla
  is about to hold.
- The preview checks itself. Every tick it compares its prediction against
  Vanilla's committed slot; three disagreements in a row and it suspends itself
  for the rest of that world and says so in the log. Another mod that owns
  hotbar selection therefore wins without needing to be known to Herzium by
  name.
- Mouse-wheel selection is Vanilla's, unpreviewed, and supersedes a pending key
  preview.
- Creative hotbar save/load shortcuts and spectator controls keep their vanilla
  path.
- `Attack` and `Use/Place` stay entirely on Vanilla's 20 TPS input path,
  including mouse buttons, side buttons, remaps and scancodes. Herzium does not
  dispatch combat or interaction packets from render-only frames.

## Faster loading

- The resource reload itself, validation, model baking, shader compilation, and
  error recovery remain untouched.
- Once those required tasks finish, Herzium removes vanilla's two-second loading
  overlay fade and the title screen's two-second visual fade.
- The 500 ms post-readiness hold used only after creating a new world is
  removed; chunk readiness and the compiled-and-visible player section are
  still required.
- Herzium starts its tiny config read asynchronously, so Fabric's client
  entrypoint does not wait on disk I/O before Minecraft creates the window.
- A lightweight dark-purple loading screen displays a pixel spiral, real reload
  progress, and randomized English tips while the required work is running.
- After the initial resource load, a responsive English or Spanish advisory
  explains what the mod does and does not do. It must be acknowledged
  once before the normal title, onboarding, or Quick Play flow continues, and
  the acknowledgement is then persisted in `config/herzium.json`.
- The advisory includes fast right-moving purple particles. Particles near the
  mouse become more opaque, and compact logical resolutions receive a
  scrollable text area plus vertically stacked buttons.

## No configuration menu

There are no options. Every feature is core and always on, and Herzium adds no
Mod Menu entry.

The only interface it shows is the one-time start-up advisory, which explains
the instability, FPS drops, frame-time spikes, hardware usage, heat and power
does and does not do. Acknowledging it is stored in `config/herzium.json`,
which is the only thing that file now contains.

## What it changes on screen

- Ordinary items adopt the current first-person model on the next frame with no
  equip dip. Combat items -- swords, axes, pickaxes, spears, maces, bows,
  crossbows, tridents and shields -- keep Vanilla's visible equip transition.
- That classification comes from item tags, which arrive from the server after
  the world does. Until they have arrived Herzium answers "combat", so an
  unclassifiable item keeps Vanilla's animation rather than losing it.
- The crosshair and hotbar attack-strength indicators interpolate only toward
  Vanilla combat's `0.5` partial-tick sample. They never display a stronger
  value than the one the attack calculation uses.

## What it does not change

Herzium does not increase the game's 20 ticks per second, invent intermediate
server packets, or override VSync imposed by the graphics driver, operating
system compositor, or an external layer. An element whose logic only changes
once per tick will still change at 20 Hz even though it is rendered every frame.

Herzium takes priority over Vanilla's limiter and the known limiter mixin paths
only while the window is active. It cannot override an external driver, compositor, or arbitrary mod
that blocks the render thread outside those paths. `Dynamic FPS` is no longer
declared incompatible because Herzium no longer owns background throttling.
Exordium buffer compatibility is specifically verified against Exordium
2.1.0/2.1.1 for Minecraft 26.1.x.

## Requirements

- Minecraft Java Edition 1.21-1.21.11 or 26.1-26.2
- Fabric Loader 0.19.3 or a newer compatible version
- Java 21 for Minecraft 1.21.x; Java 25 for Minecraft 26.x
- Fabric API is not required at runtime
- Mod Menu is optional; use the release compatible with the selected Minecraft version

## Building

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The root build targets Minecraft 26.1.2 and generates
`build/libs/herzium-<mod_version>.jar`, where `mod_version` is the value in
`gradle.properties` -- currently `1.9.3`, so `build/libs/herzium-1.9.3.jar`. Do
not use the file ending in `-sources.jar`.

### All supported Minecraft versions

The version matrix shares Herzium's sources and assets while applying the API
adaptations required by each Minecraft release. On Windows, build every target
with:

```powershell
.\version\build-all.ps1
```

This produces one release JAR per target at
`version/<minecraft-version>/build/libs/herzium-<minecraft-version>-1.9.3.jar`
for 1.21, every 1.21.x release through 1.21.11, and 26.1 through 26.2.

## Warning

Herzium changes when things are drawn, never what the server is told. It does
not raise your frame rate, so if your bottleneck is the GPU or the CPU it will
not help. Hotbar selection, attack and use timing, reach, cooldowns, hitboxes
and every packet stay exactly as Vanilla resolves them.

## License

Herzium is available under the [MIT License](LICENSE).

## Credits

Created by **zymekoh**.

The white `H` speed icon on a purple background is an original design supplied
for Herzium.

[Exordium](https://github.com/tr7zw/Exordium) was studied as a reference to
identify the buffer that limits HUD updates. Herzium does not include Exordium
code.
