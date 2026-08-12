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
reduces mouse and hotbar latency, and removes safe decorative waits from startup
and world entry. When Minecraft loses focus, Herzium limits rendering to 10 FPS.

## KoHsium cooperation

Herzium 1.8.7 and KoHsium 0.10.0 use an explicit ownership split when both are
installed. Herzium remains the sole writer of VSync, the FPS limit and immediate
hotbar behavior. KoHsium owns its editable Raw Input, Smooth Camera and late-input
controls; Herzium stops rewriting those two vanilla input values while KoHsium is present.

Herzium remains authoritative over frame pacing when both mods are installed:
the active window stays uncapped and an unfocused or minimized window is held
to 10 FPS. KoHsium's optional Streaming Headroom does not replace those rules.

KoHsium continues to provide its independent late-event sample, conservative
section scheduling, render-work reduction, PvP visual safety and diagnostics.
Its adaptive percentile and background telemetry cadence are based on actual
render work instead of raw frame count, so Herzium's uncapped rendering does
not cause those maintenance tasks to run hundreds of times per second.

The Herzium warning remains first because it owns the initial-screen runnable.
KoHsium waits until the real title screen is free before showing its own notice,
so neither startup screen overwrites the other.

## What it removes

- The OpenGL `swap interval` (VSync) is forced to `0`.
- Minecraft's general FPS limiter becomes a no-op while the window is active.
- The configured frame-rate limit remains on vanilla's `Unlimited` value.
- Special menu, loading-screen, and AFK limits are bypassed while the window is
  active. An independent render-thread limiter enforces 10 FPS whenever the
  Minecraft window loses focus, including when it is minimized.
- When Exordium is installed, Herzium disables its HUD cache so the hotbar, TAB
  list, and the rest of the HUD are extracted and rendered every frame again.

The core optimizations and fast loading transitions remain permanently active.
The configuration screen only controls the optional immediate hotbar behavior.

## Low-latency input

- Without KoHsium, Raw Input is kept enabled whenever GLFW and the operating
  system support it, and Smooth Camera remains disabled.
- With KoHsium installed, Herzium yields those two input settings so KoHsium's
  Precision Camera, Direct Mouse and Force Raw controls remain authoritative.
  Mouse sensitivity and the amount of rotation produced by each raw delta remain unchanged.
- Mouse events are accumulated without discarding samples and applied before
  the next rendered frame. Actual end-to-end latency still depends on mouse
  polling rate, frame rate, display refresh rate, GPU queue, and compositor;
  Herzium cannot guarantee 0.1 ms response time.
- When `Immediate hotbar 1-9 selection` is enabled, keyboard and mouse-button
  hotbar bindings select their ordinary slot as soon as the input callback runs
  instead of waiting up to one 20 TPS client tick. Multiple presses resolve in
  event order, so the last received press wins. The option is enabled by
  default and can be disabled to restore Vanilla's tick timing. Mouse-wheel
  hotbar selection remains entirely Vanilla; the first-person renderer reads
  Vanilla's completed selection directly on the next rendered frame.
- Creative hotbar save/load shortcuts and spectator controls retain their
  vanilla path.
- `Attack` and `Use/Place` remain entirely on Vanilla's 20 TPS input path,
  including mouse buttons, side buttons, keyboard remaps, and scancodes.
  Herzium does not dispatch combat or interaction packets from render-only
  frames.

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

## Configuration menu

Installing the compatible Mod Menu release for the selected Minecraft version
adds a configuration button for Herzium.
Its interface uses a translucent dark-purple panel with animated lighting
transitions when buttons are hovered or focused. Its compact vertical layout
shrinks with high GUI scales while keeping transparent backgrounds,
high-contrast text, and every control inside the panel.

The `Immediate hotbar 1-9 selection` option controls Herzium's event-time slot
selection and is enabled by default. Turning it off restores Vanilla tick timing
for number-key and remapped-button selection; it never alters mouse-wheel input.
Zero-duration hotbar and offhand model replacement is part of the visual core
and remains active independently of this input option.
This is Herzium's only configuration option and it is enabled by default. Its
state is stored in `config/herzium.json`.

## High-refresh improvements

- The HUD is not capped at 240 FPS: it renders at the game's full frame rate.
  When the game delivers 240 FPS, the HUD, hotbar, TAB list, and inventories
  update at 240 Hz; if the game runs faster, they can exceed that rate too.
- The crosshair and hotbar attack-strength indicators interpolate only toward
  vanilla combat's `0.5` partial-tick sample. They never display a stronger
  value than the one used by the attack calculation.
- When the hotbar, mouse wheel, inventory, or server-authorized equipment state
  changes an item, both first-person hands adopt the current models on the next
  frame with no equip dip. Swing and held-item use animations remain Vanilla.
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

Herzium takes priority over Vanilla's limiter and the known limiter mixin paths:
focused rendering remains uncapped and unfocused rendering is protected at
10 FPS. It cannot override an external driver, compositor, or arbitrary mod
that blocks the render thread outside those paths. `Dynamic FPS` remains
declared incompatible so two background-throttling systems cannot fight each
other. Exordium buffer compatibility is specifically verified against Exordium
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
`build/libs/herzium-1.8.7.jar`. Do not use the file ending in `-sources.jar`.

### All supported Minecraft versions

The version matrix shares Herzium's sources and assets while applying the API
adaptations required by each Minecraft release. On Windows, build every target
with:

```powershell
.\version\build-all.ps1
```

This produces one release JAR per target at
`version/<minecraft-version>/build/libs/herzium-<minecraft-version>-1.8.7.jar`
for 1.21, every 1.21.x release through 1.21.11, and 26.1 through 26.2.

## Warning

While the Minecraft window is active, uncapped rendering may keep the GPU at
100% usage, consume more power, and generate more heat. When the window loses
focus or is minimized, Herzium intentionally protects the system with a 10 FPS
ceiling.

## License

Herzium is available under the [MIT License](LICENSE).

## Credits

Created by **zymekoh**.

The white `H` speed icon on a purple background is an original design supplied
for Herzium.

[Exordium](https://github.com/tr7zw/Exordium) was studied as a reference to
identify the buffer that limits HUD updates. Herzium does not include Exordium
code.
