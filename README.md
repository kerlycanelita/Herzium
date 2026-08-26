# Herzium

[![GitHub](https://img.shields.io/badge/GitHub-Herzium-6f2cff?style=for-the-badge&logo=github)](https://github.com/kerlycanelita/Herzium)
[![Modrinth](https://img.shields.io/badge/Modrinth-Herzium-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/herzium)
[![Issues](https://img.shields.io/badge/Report-Issues-a855f7?style=for-the-badge&logo=githubissues)](https://github.com/kerlycanelita/Herzium/issues)
[![Discord](https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/9t2VxEF7UU)

<p align="center">
  <img src="src/main/resources/assets/herzium/icon.png" alt="Herzium icon" width="220">
</p>

Herzium is a client-side Fabric mod for Minecraft 1.21 through 26.2 that removes
internal FPS limits while its window is active, forces VSync to remain disabled,
reduces mouse latency, and removes safe decorative waits from startup
and world entry. Background, minimized, menu, and AFK pacing remain owned by Minecraft.

## Ownership

Herzium takes what it needs and does not negotiate for it. It is the sole writer
of VSync, of the active-window frame policy, and of the Vanilla Raw Input window
mode. Earlier releases stood down from that last one for KoHsium and for KoHs
Inventory Tweaks; they no longer do.

That has a cost worth stating plainly rather than burying. KoHs Inventory Tweaks
owns Cursor Landing, which places the pointer when a screen opens and verifies
the placement afterwards. Herzium rewrites the Raw Input window mode at start-up,
and a GLFW mode change can move the pointer, so the two can race. If your cursor
lands centred instead of where Cursor Landing put it, that is this decision and
the start-up log says so.

Raw Input Buffer and Ixeris are treated differently, and not out of courtesy.
Each drives its own low-level mouse pipeline; enabling Vanilla's as well would
mean two implementations feeding the same deltas, which is a broken state rather
than a contested one. When either is installed Herzium leaves Vanilla Raw Input
off. Installing both at once is unsupported by either of them, and Herzium only
reports the overlap.

KoHsium keeps everything else it owns: its late-event sample, section
scheduling, render-work reduction, PvP visual safety and diagnostics. Its
adaptive cadence is based on actual render work rather than raw frame count, so
Herzium's uncapped rendering does not make those tasks run hundreds of times a
second.

The Herzium warning still appears first, because it owns the initial-screen
runnable. KoHsium waits for the real title screen before showing its own notice.

## What it removes

- The OpenGL `swap interval` (VSync) is forced to `0`.
- Minecraft's general FPS limiter becomes a no-op while the window is active.
- The configured frame-rate limit remains on vanilla's `Unlimited` value.
- Vanilla's own throttles are left intact where they cost nothing that can be
  seen: a minimized window, a menu with no level behind it, and ten minutes
  without a single input. Handing those back is what keeps a long session from
  ending slower than it started.
- The exception is Vanilla's sixty-second AFK throttle, which Herzium ignores
  while a level is loaded. A minute without input is still playing -- standing in
  a queue, reading chat -- and dropping to 30 fps there makes the next mouse
  movement land as one lump rather than a sweep.
- When Exordium is installed, Herzium disables its HUD cache so the hotbar, TAB
  list, and the rest of the HUD are extracted and rendered every frame again.

Every one of these is permanently active. There is nothing to configure and no
screen that reports it.

## Low-latency input

- Vanilla Raw Input is forced on at start-up whenever GLFW and the operating
  system support it, regardless of what any other mod would prefer. It is
  applied to the window directly and is never written to `options.txt`, so the
  value stored in your settings is left alone even though the running window
  ignores it.
- The single exception is Raw Input Buffer, which reads the Win32 raw stream
  alongside GLFW's. With it installed Herzium leaves raw mouse motion off so
  two paths do not deliver the same movement.
- Ixeris is not that case, despite looking like it. It intercepts
  `glfwSetInputMode` for `GLFW_RAW_MOUSE_MOTION` and forwards the value to its
  own handler, so that flag is not a competing setting -- it is the switch
  Ixeris listens to. Herzium turns it on and lets Ixeris take it. Earlier
  releases turned it off, which armed nothing and left the pointer on the
  operating system's accelerated path with no raw input at all.
- Smooth Camera is not touched. Herzium used to overwrite it every frame, which
  made cinematic camera impossible to enable and left the value behind after
  uninstalling.
- VSync and the frame-rate limit are bypassed at the window and pacing level,
  not by editing saved settings, so Video Settings shows what you chose rather
  than what Herzium enforces.
- Mouse events are accumulated without discarding samples and applied before the
  next rendered frame. End-to-end latency still depends on polling rate, frame
  rate, refresh rate, GPU queue and compositor; no mod can promise a number
  here, and Herzium does not.
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
  explains the possible instability, FPS drops, frame-time spikes, hardware
  usage, heat, and power cost of uncapped operation. It must be acknowledged
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
cost of uncapped operation. Acknowledging it is stored in `config/herzium.json`,
which is the only thing that file now contains.

## High-refresh improvements

- The HUD is not capped at 240 FPS: it renders at the game's full frame rate.
  When the game delivers 240 FPS, the HUD, hotbar, TAB list, and inventories
  update at 240 Hz; if the game runs faster, they can exceed that rate too.
- Inventories and containers remain on the active uncapped rendering cadence.
  Their world background is left completely to Vanilla or another installed
  visual mod; Herzium does not replace it with a black or custom backdrop.
- The crosshair and hotbar attack-strength indicators interpolate only toward
  vanilla combat's `0.5` partial-tick sample. They never display a stronger
  value than the one used by the attack calculation.
- Ordinary items adopt the current first-person model on the next frame with no
  equip dip. Combat-capable items -- swords, axes, pickaxes, spears, maces, bows,
  crossbows, tridents and shields -- keep Vanilla's visible equip transition.
  Swing and held-item use animations remain Vanilla.
- That classification is decided from item tags, which arrive from the server
  after the world does. Until they have arrived Herzium answers "combat", so an
  unclassifiable item keeps Vanilla's animation rather than losing it. The
  answer is only cached once the tag set is demonstrably live.
- Eating, bows, crossbows, held item use, gameplay cooldowns, and server tick
  rates are not accelerated or falsified.
- Players, hitboxes, raycasts, packet contents, block textures, and entity
  animation timers are never modified. Attack and use/place timing remains
  entirely Vanilla.

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

While the Minecraft window is active, uncapped rendering may keep the GPU at
100% usage, consume more power, and generate more heat. Inactive, minimized,
menu, and AFK frame pacing is provided by Minecraft itself.

## License

Herzium is available under the [MIT License](LICENSE).

## Credits

Created by **zymekoh**.

The white `H` speed icon on a purple background is an original design supplied
for Herzium.

[Exordium](https://github.com/tr7zw/Exordium) was studied as a reference to
identify the buffer that limits HUD updates. Herzium does not include Exordium
code.
